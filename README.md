# Overview
Chunker is designed to be more efficient and resilient compared to traditional pre-generators. Traditional pre-generators often keep track of hundreds of thousands of chunks in memory, leading to significant issues in the event of a crash. Chunker's approach minimizes the amount of data tracked, reducing the impact of crashes and improving overall performance.

It works best on Paper servers, where it utilizes asynchronous functionality for faster performance.

> **_NOTE:_** This plugin will not support older versions because it uses features available only in Java 21 ([Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)). However, I plan to support future versions and improve performance further if I can.

## Key Differences

### 1. Reduced Memory Usage
Other pre-generators maintain a large in-memory database of chunks, which can grow to hundreds of thousands. This leads to high memory consumption and an increased risk of losing hours of progress during a crash. Chunker limits the amount of data tracked, only keeping necessary information in memory.

### 2. Minimal Data Loss
In case of a crash, traditional pre-generators can lose a significant amount of progress due to the extensive data they manage. Chunker is designed to handle crashes more gracefully. At most, it will lose progress equivalent to the number of `(cores * ParallelTasksMultiplier)` chunks. In cases where chunks were partially loaded before the crash, only a small number of partly loaded chunks are affected, again limited to `(cores * ParallelTasksMultiplier)`.

### 3. Efficient Chunk Processing
Chunker ensures that already processed chunks are not redone. This is achieved through a strategic method of determining the next chunk for generation. If a chunk is reloaded, it is instantly unloaded, avoiding redundant processing and saving resources.

### 4. Parallel Task Management
Chunker utilizes an efficient parallel task management system, leveraging the available CPU cores optimally. By using semaphores and executor services, Chunker runs parallel tasks without overwhelming the system. This balanced approach ensures smooth operation and better handling of concurrent tasks.

## How It Works

### Starting Point for Generation
Chunker begins pre-generation at chunk (x = 0, z = 0) and then expands outwards in a spiral pattern. This ensures that chunks near the spawn point are generated first, gradually moving outwards.

To split up the work, it use both odd and even tasks that run independently of each other, each with its own data. This allows for more efficient and organized chunk processing.

### Determining the Next Chunk
The next chunk is determined using a spiral traversal algorithm. The direction of traversal changes dynamically based on the position, ensuring efficient coverage of the world. Here’s how it works:

1. **Initial Direction and Position**: Start at (x = 0, z = 0) with an initial direction.
2. **Direction Changes**: Change direction based on the current position. If `x == z` or `(x < 0 && x == -z)` or `(x > 0 && x == 1 - z)`, the direction changes. This ensures a spiral pattern.
3. **Update Position**: Update `x` and `z` based on the current direction. Continue this until a chunk at an even coordinate (both `x` and `z` are even) is found for processing or an odd coordinate (both `x` and `z` are odd) is found for processing.

### Tracking Progress and Saving Data
Chunker keeps track of the following numbers to ensure minimal memory usage and efficient recovery in case of a crash:

1. **Current Coordinates (x, z)**: The current coordinates being processed.
2. **Direction (dx, dz)**: The current direction of traversal.
3. **Total Chunks Processed**: The total number of chunks processed so far.
4. **Chunks Processed in the Current Cycle**: The number of chunks processed in the current cycle.(resets every print cycle)

These numbers are periodically saved to a file after each load cycle (`(cores * ParallelTasksMultiplier)` chunks ) to ensure that progress can be resumed in case of a crash. The file contains:

1. **x, z Coordinates**: The current coordinates.
2. **dx, dz Directions**: The current directions.
3. **Total Chunks Processed**: To track overall progress.

#### Example Saved Data Format
`world_pregenerator_even.txt`
```
-96_-222_1_0
99039
```
- x: -96, z: -222, dx: 1, dz: 0
- `totalChunksProcessed`: 99039

`world_pregenerator_odd.txt`
```
-47_-221_1_0
98176
```
- x: -47, z: -221, dx: 1, dz: 0
- `totalChunksProcessed`: 98176

### Reasons for These Choices
- By only tracking essential data, Chunker minimizes memory usage.
- Periodically saving progress ensures minimal data loss in case of a crash.
- The spiral pattern ensures that chunks are processed in an orderly fashion, avoiding redundant processing and unnecessary checks and verifications.
- Changing directions based on the position ensures thorough and efficient coverage of the world.

---

## Config
- On non-Paper servers, the async functionality will not be used.
- I recommended that you update your Paper server's `paper-global.yml` file:

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
`/pregen 6 5s world 1000b`
- Pre-generates the `overworld`
- (threads * 6) results in a maximum of 72 concurrent parallel tasks
- Prints logs every 5 seconds
- 1000 block radius, (1000 / 16) = 62.5, rounded to 62 chunks, then squared to get total chunks, 62x62 = 3844 chunks that will need to be processed

`/pregen 2 2m world_nether 500c`
- Pre-generates the `nether`
- (threads * 2) results in a maximum of 24 concurrent parallel tasks
- Prints logs every 2 minutes
- 500 chunk radius, squared to get total chunks, (500 * 500) = 250,000 chunks that will need to be processed

`/pregen 1 12h world_the_end 100r`
- Pre-generates `the end`
- (threads * 1) results in a maximum of 12 concurrent parallel tasks
- Prints logs every 12 hours
- 100 region radius, 1 region is (32 * 32) chunks, to get the radius we multiply (32 * 100) = 3200, squared to get total chunks, (3200 * 3200) = 10,240,000 chunks that will need to be processed

`/pregen 4 10s world default`
- Pre-generates the `overworld`
- (threads * 4) results in a maximum of 48 concurrent parallel tasks
- Prints logs every 10 seconds
- `default` pre-generates to the world border of the selected world, you can use this if you set your world border manually using `/worldborder set #`

## Command Settings
## `ParallelTasksMultiplier`
- **Recommendation:** Stay below your thread count.
- **Function:** Limits the number of parallel chunk load tasks.
- **Calculation:** Multiplied by the number of threads available at server initialization. For example, if your server starts with 12 threads, the maximum number of parallel tasks allowed when **ParallelTasksMultiplier** is set to 6 will be 72.

### Performance Examples:
- **ParallelTasksMultiplier = 1:**
  - **Command:** `/pregen 1 5s world 100c`
  - **Chunks per second:** ~100-130 (on a 5600x CPU, depending on server activity and other system tasks)
  - **Time:** Finished in 1.33 minutes

![ParallelTasksMultiplier = 1](https://www.toolsnexus.com/mc/1.33min.png)

- **ParallelTasksMultiplier = 6:**
  - **Command:** `/pregen 6 5s world 100c`
  - **Chunks per second:** ~130-200 (on a 5600x CPU, depending on server activity and other system tasks)
  - **Time:** Finished in 0.916 minutes

![ParallelTasksMultiplier = 6](https://www.toolsnexus.com/mc/0.916min.png)

#### Summary:
- **Load Management:** Determines the load on your server.
- **Small Number:** Lower load, fewer chunks per second.
- **Large Number:** Higher load, faster chunk processing.
- **Best Practice:** Start at 1 and increase by 1 until you encounter constant overload; that's when you know you have pushed it too far.

## `World`
- Determines what world you want to pre-generate.
- Tab autocomplete will fetch all the vanilla worlds on the server and show them to you.
- Then you can choose which world you want and off you go.

## `Radius`
- Determines the radius of chunks that will be pre-generated.
- The supported units are blocks, chunks, and regions.
- To use it, you just have to add the letters `b`, `c`, or `r` next to the actual number.
- For example, **20000b** is a 20,000 block radius, **500c** is a 500 chunk radius, or **30r** is a 30 region radius.

## Commands:
- `/pregen`: `/pregen <ParallelTasksMultiplier> <PrintUpdateDelay in (Seconds/Minutes/Hours)> <world> <Radius in (Blocks/Chunks/Regions) or default>` Pre-generate any of the dimensions: overworld, nether, and end.
- `/pregenoff`: Turn off the pre-generation process.

## Permissions:
(**Default OP**)
- `chunker.pregen`: Grants permission to use the pre-generation command with customizable parameters.
- `chunker.pregenoff`: Grants permission to disable pre-generation using the `/pregenoff` command.
- `chunker.*`: Provides access to all Chunker commands.
