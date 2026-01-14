package main;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.bukkit.World;

import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;

/**
 * Represents a task responsible for pre-generating chunks in a specific world.
 */
public class PreGenerationTask {

	public boolean enabled = false;
	public boolean complete = false;
	public int worldId;
	public World world;
	public int parallelTasksMultiplier;
	public int timeValue;
	public int printTime;
	public int chunksPerSec;
	public int tasks;
	public char timeUnit;
	public long radius;
	public final LongAdder totalChunksProcessed = new LongAdder();
	public int chunksThisCycle = 0;
	public int localChunksThisCycle = 0;
	public long timerStart = 0;
	public long timerEnd = 0;
	public final RegionChunkIterator chunkIterator = new RegionChunkIterator();
	public final Object playerChunkLock = new Object();
	public final Object2ObjectOpenHashMap<UUID, LongOpenHashSet> playerChunkMap = new Object2ObjectOpenHashMap<>();
	public final Long2IntOpenHashMap playerChunkRefCount = new Long2IntOpenHashMap();
	public final LongOpenHashSet playerLoadedChunks = new LongOpenHashSet();
	public final LongOpenHashSet pinnedNewChunks = new LongOpenHashSet();
	public AsyncDelayedScheduler printScheduler;
	public AsyncDelayedScheduler taskSubmitScheduler;
	public AsyncDelayedScheduler cleanupScheduler;
	public int centerBlockX;
	public int centerBlockZ;
	public boolean stateHasCenter;
	public boolean stopAfterCurrentRegion;
}
