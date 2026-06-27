package main;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.bukkit.World;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Represents a task responsible for pre-generating chunks in a specific world.
 */
public class PreGenerationTask {

	public volatile boolean enabled;
	public volatile boolean complete;
	public int worldId;
	public String worldName;
	public World world;
	public int parallelTasksMultiplier;
	public int timeValue;
	public int printTime;
	public long chunksPerSec;
	public int tasks;
	public char timeUnit;
	public long radius;
	public long targetSideChunks;
	public boolean forceChunkSafety;
	public final AtomicLong submittedChunks = new AtomicLong();
	public final AtomicInteger activeSafetyTasks = new AtomicInteger();
	public final AtomicBoolean terminationStarted = new AtomicBoolean();
	public final LongAdder totalChunksProcessed = new LongAdder();
	public final LongAdder chunksThisCycle = new LongAdder();
	public long localChunksThisCycle;
	public long taskQueueTimer;
	public long timerStart;
	public long timerEnd;
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
	public volatile boolean stopAfterCurrentRegion;
}
