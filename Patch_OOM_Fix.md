# Patch OOM Fix — Memory Guard (Backpressure) cho Chunker 26.3

## 1. Triệu chứng & nguyên nhân gốc

Server crash `java.lang.OutOfMemoryError: Java heap space` khi pregen tốc độ cao
(~164 chunks/s) trên Leaf 26.3 (MoonriseRegionFileIO), RAM 8GB.

**Nguyên nhân:** plugin sinh chunk nhanh hơn tốc độ ghi đĩa. Không có cơ chế
backpressure nào giới hạn số chunk đang nằm trong pipeline:

- `PreGenerator.submitPaperBatch()` / `processChunkPaper()` gọi
  `getChunkAsync()` kiểu fire-and-forget rồi `markChunkProcessed()` ngay lập
  tức — không đếm số chunk đang chờ load/save.
- `processPaperSafetyBatch()` chỉ giới hạn theo `parallelTasksMultiplier * 16`
  (có thể lên tới hàng chục nghìn khi multiplier lớn).
- `submitFoliaBatch()` / `processChunkFolia()` không giới hạn số task đang bay.
- Queue lưu chunk phía server (Moonrise) lớn dần trong RAM nhanh hơn tốc độ
  flush xuống đĩa → tràn heap.

## 2. Các thay đổi

### 2.1 File mới: `Chunker/main/MemoryGuard.java`

Singleton toàn cục giữ "Pending Save Queue" bằng `AtomicInteger` (CAS):

| Cơ chế | Mặc định | Mô tả |
|---|---|---|
| `max-pending-saves` | `5000` | Ngưỡng chunk đang chờ tối đa. Vượt ngưỡng → tạm dừng submit chunk mới. |
| `resume-threshold` | `4000` | Hysteresis: chỉ submit lại khi queue giảm xuống dưới ngưỡng này (chống rung lắc). |
| `pause-millis` | `250ms` | Bước `Thread.sleep` khi chờ queue xả (chạy trên virtual thread của `AsyncDelayedScheduler`). |
| `heap-pause-millis` | `1000ms` | Bước sleep khi throttle vì áp lực heap. |
| `heap-threshold` | `0.85` | Heap dùng vượt 85% max heap → throttle thêm. Đặt `0` để tắt kiểm tra heap. |
| `log-interval-millis` | `30000ms` | Chống spam log cảnh báo. |

Hai lớp bảo vệ độc lập:

1. **Pending queue cap** — `reserveSlot()` / `tryReserveSlot()` / `releaseSlot()`:
   giới hạn số chunk đang nằm trong pipeline submit → load → unload.
2. **Heap pressure** — đọc `Runtime.totalMemory() - freeMemory()` so với
   `maxMemory()`; bắt được cả queue lưu phía Moonrise mà plugin không quan sát
   trực tiếp được qua API Bukkit/Paper.

Cơ chế chờ: `reserveSlot(BooleanSupplier active)` lặp `Thread.sleep` theo bước
ngắn, luôn kiểm tra `task.enabled` mỗi vòng lặp → khi `/pregenoff`, task bị
disable hoặc plugin shutdown thì thoát ngay, không treo scheduler.
Có log "Memory guard engaged..." / "Memory guard released..." để admin theo dõi.

Có thể override qua system property, ví dụ trong `jvm-args.txt` / lệnh khởi động:

```
-Dchunker.memory-guard.max-pending-saves=3000
-Dchunker.memory-guard.resume-threshold=2000
-Dchunker.memory-guard.heap-threshold=0.80
```

### 2.2 `Chunker/main/PreGenerator.java` — wire guard vào 4 đường sinh chunk

- **`processChunkPaper()`**: `reserveSlot()` trước khi `getChunkAsync()`, release
  trong callback khi chunk đã load xong (hoặc load thất bại). Đường này trước
  đây hoàn toàn không có cơ chế dừng.
- **`getChunkAsync()`**: chuyển callback cũ sang `getChunkAtAsync(...).whenComplete(...)`
  để đảm bảo `releaseSlot()` chạy trong `finally` kể cả khi future hoàn thành
  exceptionally — không rò rỉ slot, không kẹt guard.
- **`processChunkPaperWithSafety()`**: `reserveSlot()` trước khi submit,
  `releaseSlot()` trong `finally` của `whenComplete` (song song với
  `activeSafetyTasks` sẵn có).
- **`processChunkFolia()`**: `reserveSlot()` trước khi vào `RegionScheduler`,
  release trên mọi nhánh kết thúc (task disabled, unload xong, exception,
  `RuntimeException` khi schedule bị từ chối).
- **`syncProcess()`** (Bukkit fallback, chạy main thread): dùng
  `tryReserveSlot()` non-blocking — **không bao giờ sleep main thread**; nếu
  guard đang throttle thì bỏ qua chunk trong tick đó và thử lại tick sau, trước
  khi tiêu thụ iterator (tránh lệch `submittedChunks`/`totalChunksProcessed`).

### 2.3 Fix Memory Leak: `pinnedNewChunks`

`Chunker/main/PreGenerator.java` thêm handler `onChunkUnload(ChunkUnloadEvent)`:

- Trước đây `pinnedNewChunks` (tập chunk mới được ghim trong lúc pregen) chỉ
  được clear khi **không có player online**, nên khi pregen chạy lâu với player
  online thì set phình vô hạn (mỗi entry vẫn được giữ trong `LongOpenHashSet`).
- Nay khi chunk unload (`EventPriority.MONITOR`, `ignoreCancelled = true`),
  entry tương ứng bị xóa khỏi `pinnedNewChunks` → bộ nhớ được giải phóng theo
  vòng đời chunk, không tích tụ.
- Các cấu trúc khác (`playerChunkMap`, `playerChunkRefCount`,
  `playerLoadedChunks`) đã được dọn sẵn trong `terminate()`/cleanup scheduler,
  không đổi.

**Không thay đổi logic sinh chunk, thứ tự iterator, radius, hay cách tính tiến
độ** — chỉ thêm van tiết lưu (memory guard) và dọn tham chiếu.

## 3. Luồng hoạt động sau patch

```
scheduler tick (virtual thread)
  └─ submitPaperBatch/submitFoliaBatch
       └─ với từng chunk:
            reserveSlot()  ← nếu pending >= 5000 hoặc heap > 85%:
            │                  log cảnh báo + sleep 250ms/lần
            │                  đến khi pending <= 4000 và heap ổn
            │                  (thoát ngay nếu task bị disable)
            └─ submit load chunk
                 └─ callback/whenComplete → unloadRequest → releaseSlot()
```

Khi đĩa chậm hơn tốc độ sinh, pending tăng tới 5000 → pregen tự giảm tốc đúng
bằng tốc độ xả của I/O, RAM không còn phình; khi đĩa đuổi kịp, pregen tự chạy
lại hết tốc lực.

## 4. Kiểm thử / theo dõi

- Log server khi guard kích hoạt:
  `Memory guard engaged (5000/5000 chunks pending save). Pausing new chunk generation...`
  và khi xả xong: `Memory guard released: chunk generation resumed (pending=...).`
- Chỉnh ngưỡng thấp hơn nếu RAM ≤ 8GB và disk chậm, ví dụ 3000/2000.
- Muốn hành vi cũ hoàn toàn (không guard): đặt
  `-Dchunker.memory-guard.max-pending-saves=2147483647 -Dchunker.memory-guard.heap-threshold=0`.

## 5. Kết quả build

- Lệnh: `mvn clean package` (Java 25)
- Trạng thái: **BUILD SUCCESS**
- Artifact: `Chunker/target/Chunker-26.3.jar`
- Thời điểm: 2026-10-17
