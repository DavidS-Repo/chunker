# Chunker

#### Supported MC Versions:
- Minecraft 1.20.5+  
- **Requires Java 21 or higher**

#### Supported Servers:
- Spigot, Bukkit, Paper, Pufferfish, Purpur

---
<div style="display: flex; justify-content: space-between; width: 100%;">
    <img src="https://www.toolsnexus.com/mc/chunker1.png" style="width: 49%; height: auto;">
    <img src="https://www.toolsnexus.com/mc/chunker2.png" style="width: 49%; height: auto;">
</div>

## Overview
Chunker is designed to be more efficient and resilient than traditional pre-generators. Many pre-generators track thousands of chunks in memory, risking significant rollback or data loss if the server crashes. Chunker, by contrast, only tracks minimal state (like the current region’s position and how many chunks are completed), so crashes have less impact on the generation process.

It works best on Paper forks because it can utilize asynchronous chunk-loading (`CompletableFuture`) for faster performance. On non-Paper servers (Bukkit/Spigot and others), it falls back to a synchronous chunk-loading method but still performs significantly faster than many default solutions.

You can configure Chunker to run automatically **only** when there are no players online and to halt immediately if a player joins. This behavior is controlled by `auto_run` in `settings.yml`. You can also adjust how aggressively each world’s tasks run by changing `task_queue_timer` and `parallel_tasks_multiplier`. If you want to keep the server load minimal, set low concurrency or run only when the server is empty.

---

## Paper Config
> **On non-Paper servers, asynchronous functionality will **not** be used.**  
> However, if you **are** on a Paper-based server (including Pufferfish, Purpur, etc.), you can take extra steps to optimize pre-generation:

In your `paper-global.yml` (or equivalent), consider increasing the parallelism for chunk generation and I/O. For example:

```yaml
chunk-system:
  gen-parallelism: default
  io-threads: 16
  worker-threads: 16
```

- **Adjust** `io-threads` and `worker-threads` to match (or approach) your CPU’s thread count.
- By default, Paper only uses half your threads for chunk tasks; raising these can help ensure asynchronous chunk generation runs at full speed.

---

## Command Usage

The primary commands are:

```
/pregen <ParallelTasksMultiplier> <PrintUpdateDelay> <world> <Radius or "default">
/pregenoff [world]
```

### Examples
1. **`/pregen 4 10s world default`**
   - Pre-generates the overworld (`world`)
   - Uses 4 parallel chunk-loading tasks (on Paper, these will run asynchronously)
   - Prints progress logs every 10 seconds
   - `default` will use the world border as the target radius

2. **`/pregen 6 5s world 1000b`**
   - Pre-generates the overworld
   - 6 parallel tasks
   - Prints logs every 5 seconds
   - **1000b** means a 1000-block radius. Chunker calculates `(1000 / 16)²` to determine total chunks.

3. **`/pregen 2 2m world_nether 500c`**
   - Pre-generates the Nether
   - 2 parallel tasks
   - Prints logs every 2 minutes
   - **500c** means a 500-chunk radius, i.e. `500 × 500 = 250,000` chunks total.

4. **`/pregen 1 12h world_the_end 100r`**
   - Pre-generates The End
   - 1 parallel task
   - Prints logs every 12 hours
   - **100r** means a 100-region radius; each region is 32×32 chunks, so `100r` = `(100 × 32)² = 10,240,000` chunks.

### Command Parameters

- **`ParallelTasksMultiplier`**  
  Determines how many chunk-loading tasks run in parallel. On Paper, these tasks are truly async. On non-Paper, they effectively run in a tighter loop but still follow the same number-limiting logic.  
  For best results, keep this at or below your CPU’s thread count. Exceeding that is allowed but may not improve performance.

- **`<PrintUpdateDelay>`**  
  How often progress logs appear. Use a suffix `s`, `m`, or `h` for seconds, minutes, or hours. E.g., `5s`, `2m`, or `1h`.

- **`<world>`**  
  Pick the world you want to pre-generate. Tab-completion will suggest all known worlds on the server.

- **`<Radius>`**  
  The radius to fill, in blocks (`b`), chunks (`c`), or regions (`r`). For example:
  - `20000b`: 20,000-block radius  
  - `500c`: 500-chunk radius  
  - `30r`: 30-region radius  
  - `default`: generate up to the world border

- **`/pregenoff [world]`**  
  - If used without arguments: stops pre-generation in *all* worlds.  
  - If used with a world name: stops only that world’s pre-generation.

---

## Permissions
*(All default to OP)*

- **`chunker.pregen`**  
  Allows `/pregen` usage.
- **`chunker.pregenoff`**  
  Allows `/pregenoff` usage.
- **`chunker.*`**  
  Grants all Chunker permissions.

---

## Configuration: `settings.yml`
```YAML
# Configuration

# auto_run: Set to true if you want pre-generation to start automatically when no players are on the server.
# Acceptable values: true or false

# task_queue_timer: Determines how fast chunks are queued up. A value between 50-70 is recommended for modern AMD 5000 series and Intel 13th Gen CPUs in the Overworld,
# Adjust based on performance needs.

# parallel_tasks_multiplier: Sets the number of async tasks running concurrently. 'auto' will distribute the tasks based on your thread count.
# You can also set a specific integer value (e.g., 2, 4). It's recommended to stay below your total thread count.
# Example with 'auto' and 12 threads:
# world: 
#   parallel_tasks_multiplier: 4
# world_nether: 
#   parallel_tasks_multiplier: 4
# world_the_end: 
#   parallel_tasks_multiplier: 4

# print_update_delay: How often to print information (s-Seconds, m-Minutes, h-Hours). Default is 5s (5 seconds).

# radius: Defines how far the pre-generator should run (b-Blocks, c-Chunks, r-Regions) or 'default' to pre-generate until the world border.

# Settings
world:
  auto_run: false # Acceptable values: true or false
  task_queue_timer: 60 # Acceptable range: positive integer
  parallel_tasks_multiplier: auto # 'auto' or a positive integer value
  print_update_delay: 5s # Format: [value][s|m|h]. Example: 5s, 2h, 1d
  radius: default # Format: [value][b|c|r]. Example: 100b, 1c, 10r, or 'default'

world_nether:
  auto_run: false
  task_queue_timer: 60
  parallel_tasks_multiplier: auto
  print_update_delay: 5s
  radius: default

world_the_end:
  auto_run: false
  task_queue_timer: 60
  parallel_tasks_multiplier: auto
  print_update_delay: 5s
  radius: default
```

---

## Summary of Changes from Older Versions
- **No more “Virtual Threads” usage**: The plugin previously experimented with virtual threads but now uses standard concurrency with `CompletableFuture` on Paper servers.
- **Improved Schedulers**: All scheduling uses either a custom `AsyncDelayedScheduler` with `ForkJoinPool.commonPool()` or synchronous loops depending on your server type.
- **Minimal State Tracking**: Generation progress is stored in small text files per world (e.g., `world_pregenerator.txt`) to handle crashes gracefully.

---

## Quick Tips
1. **Start small**. If you’re unsure about performance, start with `parallel_tasks_multiplier = 1`, then gradually increase to find a good balance.
2. **Paper is fastest**. If you run a Paper-based server, you’ll see major speed benefits due to fully async chunk loading.
3. **Check logs**. Use the print delay to watch how many chunks are processed. If TPS drops too low, raise `task_queue_timer` or lower `parallel_tasks_multiplier`.
4. **Back up**. Always keep a backup before a large pre-generation, just in case.
5. **World border**. Consider using `default` radius to fill exactly within your border.

Enjoy faster world generation with less overhead and minimal memory usage!
