package main;

import org.bukkit.World;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.LongAdder;

/**
 * Represents a task responsible for pre-generating chunks in a specific world.
 */
public class PreGenerationTask {

	/** 
	 * Flag indicating whether the pre-generation task is enabled.
	 */
	public boolean enabled = false;

	/** 
	 * Flag indicating whether the pre-generation task has been completed.
	 */
	public boolean complete = false;

	/** 
	 * Identifier for the world associated with this task.
	 */
	public int worldId;

	/** 
	 * The Bukkit world instance where chunks are being pre-generated.
	 */
	public World world;

	/** 
	 * Multiplier to determine the number of parallel tasks.
	 */
	public int parallelTasksMultiplier;

	/** 
	 * Value representing the time configuration for the task.
	 */
	public int timeValue;

	/** 
	 * Interval at which progress or status updates are printed.
	 */
	public int printTime;

	/** 
	 * Number of chunks processed per second.
	 */
	public int chunksPerSec;

	/** 
	 * Total number of tasks to be executed.
	 */
	public int tasks;

	/** 
	 * Number of chunks processed in each batch.
	 */
	public int batchSize;

	/** 
	 * Unit of time used for scheduling (e.g., seconds, minutes).
	 */
	public char timeUnit;

	/** 
	 * Radius around the center point within which chunks are pre-generated.
	 */
	public long radius;

	/** 
	 * Total number of chunks processed, using LongAdder for thread-safe increments.
	 */
	public final LongAdder totalChunksProcessed = new LongAdder();

	/** 
	 * Number of chunks processed in the current cycle.
	 */
	public int chunksThisCycle = 0;

	/** 
	 * Number of locally processed chunks in the current cycle.
	 */
	public int localChunksThisCycle = 0;

	/** 
	 * Start time of the current timer cycle in milliseconds.
	 */
	public long timerStart = 0;

	/** 
	 * End time of the current timer cycle in milliseconds.
	 */
	public long timerEnd = 0;

	/** 
	 * Iterator for iterating through region chunks.
	 */
	public final RegionChunkIterator chunkIterator = new RegionChunkIterator();

	/** 
	 * Set of chunks that have been loaded by players.
	 */
	public final Set<Long> playerLoadedChunks = ConcurrentHashMap.newKeySet();

	/** 
	 * Mapping of player UUIDs to their respective sets of loaded chunks.
	 */
	public final ConcurrentHashMap<UUID, Set<Long>> playerChunkMap = new ConcurrentHashMap<>();

	/** 
	 * Scheduler responsible for printing task progress at regular intervals.
	 */
	public ScheduledExecutorService printScheduler;

	/** 
	 * Executor service for submitting tasks for execution.
	 */
	public ScheduledExecutorService taskSubmit;

	/** 
	 * Executor service for performing the actual chunk pre-generation tasks.
	 */
	public ExecutorService taskDo;
}