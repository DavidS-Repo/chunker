
# Overview
The Pre-Generator plugin allows for efficient world generation using the /pregen command. It works best on Paper servers, where it utilizes async functionality for faster performance.

Note: This plugin will not support older versions because it uses features available only in Java 21. However, I plan to support future versions and improve performance further.

## **Key Features**
 - **Async Pre-Generation**: Utilizes server threads for efficient world generation.
 - **Customizable Parameters**: Adjust parallel tasks, update delay, world, and radius.
---

Works best on paper servers, on none paper servers the async functionality will not be used. Recommend you go into your paper server paper-global.yml and update these

## Config
```yaml
chunk-system:
  gen-parallelism: default
  io-threads: 12
  worker-threads: 12
```
   - Adjust `io-threads` and `worker-threads` to match your CPU’s thread count. Default settings utilize only half.
   - Usage: /pregen `ParallelTasksMultiplier` `PrintUpdateDelayin(Seconds/Minutes/Hours)` `world` `Radius(Blocks/Chunks/Regions)`

## Command usage
### Examples:
`/pregen 6 5s world 1000b`
- Pre-Generate the `overworld` 
- (threads * 6) results in a max of 72 concurrent parallel_tasks
- Prints logs every 5 seconds
- 1000 block radius, (1000 / 16) = 62.5, rounded to 62 chunks, then squared to ge total chunks, 62x62 = 3844 Chunks that will need to be processed

`/pregen 2 2m world_nether 500c`
- Pre-Generate the `nether`
- (threads * 2) results in a max of 24 concurrent parallel_tasks
- Prints logs every 2 minutes
- 500 chunk radius, squared to ge total chunks, (500 * 500) = 250000 Chunks that will need to be processed

`/pregen 1 12h world_the_end 100r`
- Pre-Generate the `the end`
- (threads * 1) results in a max of 12 concurrent parallel_tasks
- Prints logs every 12 hours
- 100 regions radius, 1 regions is (32 * 32) chunks, to get the radius we multiply (32 * 100) = 3200 squared to ge total chunks, (3200 * 3200) = 10240000 Chunks that will need to be processed

`/pregen 4 10s world default`
- Pre-Generate the `overworld`
- (threads * 4) results in a max of 48 concurrent parallel_tasks
- Prints logs every 10 seconds
- `default` Pre-Generates to the world border of the selected world, you can use this if set your world border manually using /worldborder set #

## Command Settings
## `ParallelTasksMultiplier`
- **Recommendation:** Stay below your thread count.
- **Function:** Limits the number of parallel chunk load tasks. 
- **Calculation:** Multiplied by the number of threads available at server initialization. For example, If your server starts with 12 threads, the maximum number of parallel tasks allowed when **ParallelTasksMultiplier** is set to 6 will be 72.

### **Performance Examples:** 
- **ParallelTasksMultiplier = 1:**
- **Command:** `pregen 1 5s world 200c` 
- **Chunks per second:** ~100-130 (on a 5600x CPU, depending on server activity and other system tasks) 
- **Time:** Finished in 2.75 minutes 

![ParallelTasksMultiplier = 1](https://www.toolsnexus.com/mc/1.33min.png) 
- **ParallelTasksMultiplier = 6:**
- **Command:** `pregen 6 5s world 200c`
- **Chunks per second:** ~130-200 (on a 5600x CPU, depending on server activity and other system tasks)
- **Time:** Finished in 1.7 minutes

![ParallelTasksMultiplier = 6](https://www.toolsnexus.com/mc/0.916min.png)
#### **Summary:** 
- **Load Management:** Determines the load on your server.
- **Small Number:** Lower load, fewer chunks per second.
- **Large Number:** Higher load, faster chunk processing.
- **Best Practice:** Start at 1 and increase by 1 until you encounter constant overload; that's when you know you have pushed it too far.

## `World`
- Determines what world you want to pregenerate.
- Tab autocomplete will fetch all the vanilla worlds in the server and show them to you.
- Then you can choose which world you want and off you go.
## `Radius` 
- Determines the radius of chunks that will be pre-generated.
- The supported units are Blocks, Chunks, and Regions.
- To use it, you just have to add the letters b, c, or r next to the actual number. 
- For example, **20000b** is a 20000 block radius, **500c** is a 500 chunk radius, or **30r** is a 30 region radius.


## Commands:
- `/pregen`:  /pregen <ParallelTasksMultiplier> <PrintUpdateDelayin(Seconds/Minutes/Hours)> <world> <Radiusin(Blocks/Chunks/Regions) or default> Pregenerate any of the dimensions; overworld, nether, and end.
- `/pregenoff`: Turn off pregeneration process.

## Permissions:

(**Default OP**)
- `sg.pregen`: Grants permission to use the pre-generation command with customizable parameters.
- `sg.pregenoff`: Grants permission to disable pre-generation using the `/pregenoff` command.
- `sg.*`: Provides access to all SkyGrid commands.
