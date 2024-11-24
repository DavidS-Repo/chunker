# Chunker
#### Supported MC Versions:
Minecraft 1.20.5+ (Java 21 or higher)

#### Supported Severs: 
Spigot, Bukkit, Paper, Pufferfish, Purpur


# Overview
Chunker is designed to be more efficient and resilient compared to traditional pre-generators. Traditional pre-generators often keep track of thousands of chunks in memory, leading to significant issues in the event of a crash. Chunker's approach minimizes the amount of data tracked, reducing the impact of crashes and improving overall performance.

It works best on Paper forks, where it utilizes asynchronous functionality for faster performance. Performance for Bukkit/Spigot has been more optimized than the default PaperLib implementation, so it should be about 2-3x faster for those compared to other pre-generators.

The pre-generation can be set to run by default when there are no players on the server, and it will shut down when any players connect. This option can be changed in the `settings.yml`. You can also modify the load each world will put on the server and distribute the load however you would like.

---

## Paper Config
- On non-Paper forks, the async functionality will not be used.
- I recommend that you update your Paper server's `paper-global.yml` file:

```yaml
chunk-system:
  gen-parallelism: default
  io-threads: 12
  worker-threads: 12
```
   - Adjust `io-threads` and `worker-threads` to match your CPU’s thread count. Default settings utilize only half.
   - Usage: `/pregen <ParallelTasksMultiplier> <PrintUpdateDelay in (Seconds/Minutes/Hours)> <world> <Radius (Blocks/Chunks/Regions)>`

## Command Usage
### Examples:
`/pregen 4 10s world default`
- Pre-generates the `overworld`
- 4 async parallel processes loading chunks
- Prints logs every 10 seconds
- `default` pre-generates to the world border of the selected world. You can use this if you set your world border manually using `/worldborder set #`

`/pregen 6 5s world 1000b`
- Pre-generates the `overworld`
- 6 async parallel processes loading chunks
- Prints logs every 5 seconds
- 1000 block radius (1000 / 16) = 62.5, rounded to 62 chunks, then squared to get total chunks, 62x62 = 3844 chunks that will need to be processed

`/pregen 2 2m world_nether 500c`
- Pre-generates the `nether`
- 2 async parallel processes loading chunks
- Prints logs every 2 minutes
- 500 chunk radius, squared to get total chunks, (500 * 500) = 250,000 chunks that will need to be processed

`/pregen 1 12h world_the_end 100r`
- Pre-generates `the end`
- 1 async parallel process loading chunks
- Prints logs every 12 hours
- 100 region radius. 1 region is (32 * 32) chunks. To get the radius, multiply (32 * 100) = 3200, squared to get total chunks, (3200 * 3200) = 10,240,000 chunks that will need to be processed

## Command Settings
### `ParallelTasksMultiplier`
- Stay below your thread count (can be exceeded if desired, but exceeding your thread count will cause a chunk backlog and the `Total time` won’t reflect accurately unless `task_queue_timer` is also modified).
- Limits the number of parallel chunk load tasks.

### Performance Examples:
- **ParallelTasksMultiplier = 6:**
  - `/pregen 6 5s world 100c`
  - Avg. ~100 ± 4 (on a 5600x CPU, depending on server activity and other system tasks)
  - Finished in 1 Minute 39 Seconds

![ParallelTasksMultiplier = 6](https://www.toolsnexus.com/mc/8_11_2024_pregen_6_5s_world_100c.png)

- **ParallelTasksMultiplier = 12:**
  - `/pregen 12 5s world 100c`
  - Avg. ~200 ± 4 (on a 5600x CPU, depending on server activity and other system tasks)
  - Finished in 49 Seconds

![ParallelTasksMultiplier = 12](https://www.toolsnexus.com/mc/8_11_2024_pregen_12_5s_world_100c.png)

#### Summary:
- Determines the load on your server. Scales depending on `task_queue_timer` in the settings.
- Lower load, fewer chunks per second. Good if you just want to run it in the background.
- Higher load, faster chunk processing.
- Start at 1 and increase by 1 until you encounter constant overload or reach your thread count.

## `World`
- Determines which world you want to pre-generate.
- Tab autocomplete will fetch all the vanilla worlds on the server and show them to you.
- Then you can choose which world you want to process.

## `Radius`
- Determines the radius of chunks that will be pre-generated.
- The supported units are blocks, chunks, and regions.
- To use it, add the letters `b`, `c`, or `r` next to the actual number.
- For example, **20000b** is a 20,000 block radius, **500c** is a 500 chunk radius, or **30r** is a 30 region radius.

## Commands:
- `/pregen`: `/pregen <ParallelTasksMultiplier> <PrintUpdateDelay in (Seconds/Minutes/Hours)> <world> <Radius in (Blocks/Chunks/Regions) or default>` Pre-generate any of the dimensions: overworld, nether, and end.
- `/pregenoff`: Turn off the pre-generation process for all worlds, or `/pregenoff [world]` to turn it off for a specific world.

## Permissions:
(**Default OP**)
- `chunker.pregen`: Grants permission to use the pre-generation command with customizable parameters.
- `chunker.pregenoff`: Grants permission to disable pre-generation using the `/pregenoff` command.
- `chunker.*`: Provides access to all Chunker commands.

## settings.yml
```
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
