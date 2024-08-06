package main;

import org.bukkit.World;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

public class PreGenerationTask {

    public boolean enabled = false, complete = false, firstPrint;
    public String currentWorldName;
    public World world;
    public ScheduledExecutorService scheduler, taskScheduler;
    public int parallelTasksMultiplier, timeValue, printTime, chunksPerSec, tasks;
    public char timeUnit;
    public long radius;
    public int x = 0, z = 0, dx = 0, dz = -1, currentX, currentZ;
    public long totalChunksProcessed = 0;
    public int chunksThisCycle = 0, localChunksThisCycle = 0;
    public long TimerStart = 0, TimerEnd = 0;

    public final Set<String> scheduledChunks = ConcurrentHashMap.newKeySet();
    public final Set<String> playerLoadedChunks = ConcurrentHashMap.newKeySet();
}