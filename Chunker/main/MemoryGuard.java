package main;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static main.ConsoleColorUtils.*;

/**
 * Global memory guard that applies backpressure to chunk pre-generation.
 *
 * <p>The pre-generator can submit chunks faster than the region file I/O can
 * persist them. Every submitted chunk stays referenced until the server has
 * loaded it and queued it for saving, and Leaf/Moonrise keeps a second
 * in-memory queue of chunks waiting to be written to disk. When the disk is
 * slower than the generator both queues grow until the heap is exhausted with
 * {@code java.lang.OutOfMemoryError: Java heap space}.
 *
 * <p>This guard bounds that pressure with two independent signals:
 * <ol>
 *   <li>Pending chunk queue: the number of chunk operations submitted by this
 *       plugin that have not completed yet. Once the counter reaches
 *       {@link #maxPendingSaves()}, new submissions are paused until the queue
 *       drains below the resume threshold.</li>
 *   <li>Heap pressure: once the used heap exceeds the configured fraction of
 *       the maximum heap, submissions are paused as well. This throttles the
 *       server-side save queue that the plugin cannot observe directly.</li>
 * </ol>
 *
 * <p>The wait loop sleeps in short intervals on the calling (virtual) thread
 * and always aborts when the owning pre-generation task is disabled, so
 * command shutdowns and plugin disables stay responsive.
 *
 * <p>All thresholds can be overridden with system properties:
 * {@code chunker.memory-guard.max-pending-saves},
 * {@code chunker.memory-guard.resume-threshold},
 * {@code chunker.memory-guard.pause-millis},
 * {@code chunker.memory-guard.heap-pause-millis},
 * {@code chunker.memory-guard.heap-threshold} (set to 0 to disable the heap
 * check) and {@code chunker.memory-guard.log-interval-millis}.
 */
public final class MemoryGuard {

	private static final String PROPERTY_PREFIX = "chunker.memory-guard.";
	public static final String MAX_PENDING_SAVES_PROPERTY = PROPERTY_PREFIX + "max-pending-saves";
	public static final String RESUME_THRESHOLD_PROPERTY = PROPERTY_PREFIX + "resume-threshold";
	public static final String PAUSE_MILLIS_PROPERTY = PROPERTY_PREFIX + "pause-millis";
	public static final String HEAP_PAUSE_MILLIS_PROPERTY = PROPERTY_PREFIX + "heap-pause-millis";
	public static final String HEAP_THRESHOLD_PROPERTY = PROPERTY_PREFIX + "heap-threshold";
	public static final String LOG_INTERVAL_MILLIS_PROPERTY = PROPERTY_PREFIX + "log-interval-millis";

	private static final int DEFAULT_MAX_PENDING_SAVES = 5_000;
	private static final int DEFAULT_RESUME_THRESHOLD = 4_000;
	private static final long DEFAULT_PAUSE_MILLIS = 250L;
	private static final long DEFAULT_HEAP_PAUSE_MILLIS = 1_000L;
	private static final double DEFAULT_HEAP_THRESHOLD = 0.85d;
	private static final long DEFAULT_LOG_INTERVAL_MILLIS = 30_000L;
	private static final long HEAP_CHECK_MIN_BYTES = 1024L * 1024L * 1024L;

	private static final MemoryGuard INSTANCE = new MemoryGuard();

	private final int maxPendingSaves;
	private final int resumeThreshold;
	private final long pauseMillis;
	private final long heapPauseMillis;
	private final double heapThreshold;
	private final long logIntervalMillis;

	private final AtomicInteger pendingSaves = new AtomicInteger();
	private final AtomicLong lastThrottleLog = new AtomicLong();
	private final AtomicBoolean throttleNoticeActive = new AtomicBoolean();

	private MemoryGuard() {
		this.maxPendingSaves = positiveIntProperty(MAX_PENDING_SAVES_PROPERTY, DEFAULT_MAX_PENDING_SAVES);
		this.resumeThreshold = Math.min(positiveIntProperty(RESUME_THRESHOLD_PROPERTY, DEFAULT_RESUME_THRESHOLD), this.maxPendingSaves);
		this.pauseMillis = positiveLongProperty(PAUSE_MILLIS_PROPERTY, DEFAULT_PAUSE_MILLIS);
		this.heapPauseMillis = positiveLongProperty(HEAP_PAUSE_MILLIS_PROPERTY, DEFAULT_HEAP_PAUSE_MILLIS);
		this.heapThreshold = heapThresholdProperty();
		this.logIntervalMillis = positiveLongProperty(LOG_INTERVAL_MILLIS_PROPERTY, DEFAULT_LOG_INTERVAL_MILLIS);
	}

	/**
	 * @return the shared guard instance
	 */
	public static MemoryGuard getInstance() {
		return INSTANCE;
	}

	/**
	 * @return number of chunks currently queued in the generation/save pipeline
	 */
	public int pendingSaves() {
		return pendingSaves.get();
	}

	/**
	 * @return configured maximum number of chunks allowed in the pipeline
	 */
	public int maxPendingSaves() {
		return maxPendingSaves;
	}

	/**
	 * Non-blocking slot reservation.
	 *
	 * @return true when a slot was reserved, false while the guard is throttling
	 */
	public boolean tryReserveSlot() {
		if (isHeapOverThreshold()) return false;
		return tryAcquire(maxPendingSaves);
	}

	/**
	 * Reserves a slot, sleeping in short intervals while the guard is
	 * throttling. Must only be called from scheduler threads, never from the
	 * main server thread.
	 *
	 * @param active supplier telling whether the owning task is still running
	 * @return true when a slot was reserved, false when the task stopped
	 */
	public boolean reserveSlot(BooleanSupplier active) {
		if (tryReserveSlot()) return true;

		boolean heapThrottled = isHeapOverThreshold();
		logThrottle(heapThrottled);
		try {
			while (active.getAsBoolean()) {
				if (!sleepQuietly(heapThrottled ? heapPauseMillis : pauseMillis)) return false;

				heapThrottled = isHeapOverThreshold();
				if (heapThrottled || pendingSaves.get() > resumeThreshold) {
					logThrottle(heapThrottled);
					continue;
				}
				if (tryAcquire(resumeThreshold)) return true;
			}
			return false;
		} finally {
			endThrottle();
		}
	}

	/**
	 * Releases a previously reserved slot.
	 */
	public void releaseSlot() {
		while (true) {
			int current = pendingSaves.get();
			if (current <= 0) return;
			if (pendingSaves.compareAndSet(current, current - 1)) return;
		}
	}

	private boolean tryAcquire(int limit) {
		while (true) {
			int current = pendingSaves.get();
			if (current >= limit) return false;
			if (pendingSaves.compareAndSet(current, current + 1)) return true;
		}
	}

	private boolean isHeapOverThreshold() {
		if (heapThreshold <= 0.0d) return false;
		Runtime runtime = Runtime.getRuntime();
		long max = runtime.maxMemory();
		if (max < HEAP_CHECK_MIN_BYTES) return false;
		long used = runtime.totalMemory() - runtime.freeMemory();
		return used >= (long) (max * heapThreshold);
	}

	private void logThrottle(boolean heapThrottled) {
		throttleNoticeActive.set(true);
		long now = System.currentTimeMillis();
		long last = lastThrottleLog.get();
		if (now - last < logIntervalMillis) return;
		if (!lastThrottleLog.compareAndSet(last, now)) return;
		String reason = heapThrottled
				? "heap usage above " + (long) (heapThreshold * 100.0d) + "%"
				: pendingSaves.get() + "/" + maxPendingSaves + " chunks pending save";
		logColor(YELLOW, "Memory guard engaged (" + reason + "). Pausing new chunk generation until pressure drops.");
	}

	private void endThrottle() {
		if (throttleNoticeActive.compareAndSet(true, false)) {
			logColor(GREEN, "Memory guard released: chunk generation resumed (pending=" + pendingSaves.get() + ").");
		}
	}

	private static boolean sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private static int positiveIntProperty(String key, int fallback) {
		String raw = System.getProperty(key);
		if (raw == null) return fallback;
		try {
			int value = Integer.parseInt(raw.trim());
			return value > 0 ? value : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static long positiveLongProperty(String key, long fallback) {
		String raw = System.getProperty(key);
		if (raw == null) return fallback;
		try {
			long value = Long.parseLong(raw.trim());
			return value > 0 ? value : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static double heapThresholdProperty() {
		String raw = System.getProperty(HEAP_THRESHOLD_PROPERTY);
		if (raw == null) return DEFAULT_HEAP_THRESHOLD;
		try {
			double value = Double.parseDouble(raw.trim());
			if (value <= 0.0d) return 0.0d;
			return Math.min(value, 1.0d);
		} catch (NumberFormatException e) {
			return DEFAULT_HEAP_THRESHOLD;
		}
	}
}
