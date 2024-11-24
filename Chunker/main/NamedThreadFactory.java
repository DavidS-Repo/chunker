package main;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom thread factory to name threads for easier debugging and optionally set them as daemon threads.
 */
public class NamedThreadFactory implements ThreadFactory {
	private final String baseName;
	private final boolean daemon;
	private final AtomicInteger count = new AtomicInteger(0);

	/**
	 * Constructor to initialize the thread factory.
	 *
	 * @param baseName The base name for threads created by this factory.
	 * @param daemon   If true, threads created will be daemon threads.
	 */
	public NamedThreadFactory(String baseName, boolean daemon) {
		this.baseName = baseName;
		this.daemon = daemon;
	}

	@Override
	public Thread newThread(Runnable r) {
		Thread thread = new Thread(r, baseName + "-" + count.incrementAndGet());
		thread.setDaemon(daemon);
		return thread;
	}
}