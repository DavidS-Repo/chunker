# Chunker - Official Support & Downloads

Join our **Discord Community** for help, support, issues, feedback, and discussions!
Click here to join: **[Join Our Discord](https://discord.gg/FUx7fk4PsA)**

## Contributions

**PRs welcome**

This repository is public. Fork the repo, create a branch, and open a pull request. I will merge changes that work and do not introduce issues. If I need changes I will leave feedback.

Quick checklist
- Link the related issue if any
- Add a short description and steps to test
- Build must pass with `mvn clean package` (Java 21)

## Supported MC Versions:
- Minecraft 1.20.5+  
- Requires Java 21 or higher

## Supported Servers:
- Spigot, Bukkit, Paper, Pufferfish, Purpur, Leaf, and Folia

<div style="display: flex; justify-content: space-between; width: 100% !important;">
    <img src="https://www.davids-repo.dev/mc/chunker1.png" style="width: 49% !important; height: auto;">
    <img src="https://www.davids-repo.dev/mc/chunker2.png" style="width: 49% !important; height: auto;">
</div>

## Overview
Chunker is designed to be more efficient and resilient than traditional pre-generators. Many pre-generators track thousands of chunks in memory, risking significant rollback or data loss if the server crashes. Chunker, by contrast, only tracks minimal state (like the current region’s position and how many chunks are completed), so crashes have less impact on the generation process.

It works best on Paper forks because it can utilize asynchronous chunk-loading (`CompletableFuture`) for faster performance. On non-Paper servers (Bukkit/Spigot and others), it falls back to a synchronous chunk-loading method but still performs significantly faster than many default solutions.

You can configure Chunker to run automatically **only** when there are no players online and to halt immediately if a player joins. This behavior is controlled by `auto_run` in `settings.yml`. You can also adjust how aggressively each world’s tasks run by changing `task_queue_timer` and `parallel_tasks_multiplier`. If you want to keep the server load minimal, set low concurrency or run only when the server is empty.

## Optimized JVM Launch Parameters (`start.bat`):

---
<details>
  <summary>
      View run.bat Source Code
  </summary>

```bash
@echo off
for %%f in (*.jar) do set JAR=%%f
REM Launching Java with Aikar's flags
java ^
 -Xms1G ^
 -Xmx=30G ^
 -XX:+UseG1GC ^
 -XX:+UnlockExperimentalVMOptions ^
 -XX:G1NewSizePercent=30 ^
 -XX:G1MaxNewSizePercent=40 ^
 -XX:G1HeapRegionSize=8M ^
 -XX:G1ReservePercent=20 ^
 -XX:G1HeapWastePercent=5 ^
 -XX:G1MixedGCCountTarget=4 ^
 -XX:InitiatingHeapOccupancyPercent=15 ^
 -XX:G1MixedGCLiveThresholdPercent=90 ^
 -XX:G1RSetUpdatingPauseTimePercent=5 ^
 -XX:SurvivorRatio=32 ^
 -XX:+PerfDisableSharedMem ^
 -XX:MaxTenuringThreshold=1 ^
 -XX:+OptimizeStringConcat ^
 -XX:+UseCompressedOops ^
 -XX:+DisableExplicitGC ^
 -XX:+AlwaysPreTouch ^
 -XX:+ParallelRefProcEnabled ^
 -XX:+UseNUMA ^
 -XX:ParallelGCThreads=16 ^
 -XX:ConcGCThreads=16 ^
 -XX:MaxGCPauseMillis=50 ^
 -Dusing.aikars.flags=https://mcflags.emc.gs ^
 -Daikars.new.flags=true ^
 -jar "%JAR%" --nogui
pause
```

</details>

- `Xms1G` and `Xmx30G` should be updated to match your minimum (`Xms`) and max memory (`Xmx`) for your own server.
- Update both `XX:ParallelGCThreads` and `XX:ConcGCThreads` to match your number of threads.
---

## Paper Config
> **Note:** On non-Paper servers, asynchronous functionality will **not** be used.  
> However, if you **are** on a Paper-based server (including Pufferfish, Purpur, etc.), you can take extra steps to optimize pre-generation:

In your `paper-global.yml` (or equivalent), consider increasing the parallelism for chunk generation and I/O:

```yaml
chunk-loading-advanced:
  auto-config-send-distance: true
  player-max-concurrent-chunk-generates: -1
  player-max-concurrent-chunk-loads: -1

chunk-loading-basic:
  player-max-chunk-generate-rate: -1.0
  player-max-chunk-load-rate: -1.0
  player-max-chunk-send-rate: -1.0

chunk-system:
  gen-parallelism: default
  io-threads: 16
  worker-threads: 16

region-file-cache-size: 16
```

- **Adjust** `io-threads` and `worker-threads` to match (or approach) your CPU’s thread count.
- By default, Paper only uses half your threads for chunk tasks; raising these can help ensure asynchronous chunk generation runs at full speed.
- Lower `region-file-cache-size` if running pregen on a fresh world with no players online.

## Command Usage

The primary commands are:

```text
/pregen <ParallelTasksMultiplier> <PrintUpdateDelay> <world> <Radius or "default">
/pregenoff [world]
```

### Examples
1. `/pregen 4 10s world default`
   - Pre-generates the overworld (`world`)  
   - Uses 4 parallel chunk-loading tasks (on Paper, these run asynchronously)  
   - Prints progress logs every 10 seconds  
   - `default` uses the world border as the radius

2. `/pregen 6 5s world 1000b`
   - Pre-generates the overworld  
   - 6 parallel tasks  
   - Prints logs every 5 seconds  
   - **1000b** = 1000-block radius → Chunker calculates `(1000 / 16)²`

3. `/pregen 2 2m world_nether 500c`
   - Pre-generates the Nether  
   - 2 parallel tasks  
   - Logs every 2 minutes  
   - **500c** = 500-chunk radius → `500 × 500 = 250,000 chunks`

4. `/pregen 1 12h world_the_end 100r`
   - Pre-generates The End  
   - 1 parallel task  
   - Logs every 12 hours  
   - **100r** = 100-region radius → `(100 × 32)² = 10,240,000 chunks`

### Command Parameters
- **ParallelTasksMultiplier**: Determines how many chunk-loading tasks run in parallel. On Paper, these tasks are async. Recommended: Keep at or below your CPU’s thread count.
- **PrintUpdateDelay**: How often progress logs appear. Add suffix `s`, `m`, or `h`.
- **<world>**: The world to pre-generate. Tab-completion supported.
- **<Radius>**: The target radius with a suffix:
  - `b` – Blocks (e.g., `20000b`)
  - `c` – Chunks (e.g., `500c`)
  - `r` – Regions (e.g., `30r`)
  - `default` – Uses world border
- **/pregenoff [world]**:
  - No args = stops all worlds  
  - With world name = stops that world only

## Permissions
(Defaults to OP)

- `chunker.pregen` – Allows use of `/pregen`
- `chunker.pregenoff` – Allows use of `/pregenoff`
- `chunker.*` – Grants all Chunker permissions

## Configuration: settings.yml

```yaml
# World Configuration for Chunker Plugin

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

# center: Sets where the pregen spiral starts.
# - 'default' uses the world border center, or the world spawn if no border center is set.
# - '~ ~' always uses the current world spawn.
# - 'x z' uses fixed block coordinates, for example: "0 0" or "1500 -500".

# World Settings
# exampleWorldName:
#   auto_run: false # Acceptable values: true or false
#   task_queue_timer: 60 # Acceptable range: positive integer
#   parallel_tasks_multiplier: auto # 'auto' or a positive integer value
#   print_update_delay: 5s # Format: [value][s|m|h]. Example: 5s, 2h, 1d
#   radius: default # Format: [value][b|c|r]. Example: 100b, 1c, 10r, or 'default'
#   center: default # 'default', '~ ~', or 'x z' (block coords as two numbers, e.g. "0 0")

world:
  center: default
  auto_run: false
  task_queue_timer: 60
  parallel_tasks_multiplier: auto
  print_update_delay: 5s
  radius: default
world_nether:
  center: default
  auto_run: false
  task_queue_timer: 60
  parallel_tasks_multiplier: auto
  print_update_delay: 5s
  radius: default
world_the_end:
  center: default
  auto_run: false
  task_queue_timer: 60
  parallel_tasks_multiplier: auto
  print_update_delay: 5s
  radius: default

```

## Quick Tips
1. Begin with `parallel_tasks_multiplier = 1`, then adjust.
2. Async loading (Paper and Folia) = much better performance.
3. Use `print_update_delay` of 10s+ to avoid excessive log output during long runs.
