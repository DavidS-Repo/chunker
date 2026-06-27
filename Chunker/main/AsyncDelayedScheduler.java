package main;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class AsyncDelayedScheduler {

	private final AtomicBoolean isEnabled = new AtomicBoolean(true);
	private static final AtomicInteger SCHEDULER_IDS = new AtomicInteger();
	private static final ThreadFactory VIRTUAL_THREAD_FACTORY = Thread.ofVirtual()
			.name("Chunker-task-", 0)
			.factory();

	private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r ->
			Thread.ofPlatform()
			.daemon(true)
			.name("Chunker-delay-scheduler-" + SCHEDULER_IDS.incrementAndGet())
			.unstarted(r));

	/**
	 * Schedule a task with a delay.
	 *
	 * @param task  The task to execute
	 * @param delay The delay before execution
	 * @param unit  The time unit for the delay
	 */
	public void scheduleWithDelay(Runnable task, long delay, TimeUnit unit) {
		if (!isEnabled.get()) return;
		try {
			timer.schedule(() -> runAsync(task), delay, unit);
		} catch (RejectedExecutionException ignored) {
		}
	}

	/**
	 * Schedule multiple tasks with a delay.
	 *
	 * @param tasks An array of tasks to execute
	 * @param count The number of tasks to execute from the array
	 * @param delay The delay before execution
	 * @param unit  The time unit for the delay
	 */
	public void scheduleBatchWithDelay(Runnable[] tasks, int count, long delay, TimeUnit unit) {
		if (count <= 0 || !isEnabled.get()) return;

		Runnable[] tasksToRun = new Runnable[count];
		System.arraycopy(tasks, 0, tasksToRun, 0, count);

		try {
			timer.schedule(() -> runAsync(() -> {
				if (!isEnabled.get()) return;

				for (int i = 0; i < count; i++) {
					runSafely(tasksToRun[i]);
				}
			}), delay, unit);
		} catch (RejectedExecutionException ignored) {
		}
	}

	/**
	 * Schedule a task at a fixed rate with dynamic delay control.
	 *
	 * @param task              The task to execute
	 * @param initialDelay      The initial delay before the first execution
	 * @param period            The period between executions
	 * @param unit              The time unit for delay and period
	 * @param isEnabledSupplier Supplier to determine if the task should run
	 */
	public void scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit, Supplier<Boolean> isEnabledSupplier) {
		scheduleNext(task, initialDelay, period, unit, isEnabledSupplier);
	}

	/**
	 * Schedule a task at a fixed rate.
	 *
	 * @param task         The task to execute
	 * @param initialDelay The initial delay before the first execution
	 * @param period       The period between executions
	 * @param unit         The time unit for delay and period
	 */
	public void scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
		scheduleAtFixedRate(task, initialDelay, period, unit, isEnabled::get);
	}

	/**
	 * Enable or disable the scheduler.
	 *
	 * @param enabled true to enable, false otherwise
	 */
	public void setEnabled(boolean enabled) {
		boolean wasEnabled = isEnabled.getAndSet(enabled);
		if (wasEnabled && !enabled) {
			timer.shutdownNow();
		}
	}

	/**
	 * Check if the scheduler is enabled.
	 *
	 * @return true if enabled, false otherwise
	 */
	public boolean isEnabled() {
		return isEnabled.get();
	}

	/**
	 * Supplier for the scheduler's enabled status.
	 *
	 * @return a Supplier<Boolean> for the scheduler's status
	 */
	public Supplier<Boolean> isEnabledSupplier() {
		return isEnabled::get;
	}

	private void scheduleNext(Runnable task, long delay, long period, TimeUnit unit, Supplier<Boolean> isEnabledSupplier) {
		if (!isEnabled.get() || !isEnabledSupplier.get()) return;

		try {
			timer.schedule(() -> runAsync(() -> {
				try {
					if (isEnabled.get() && isEnabledSupplier.get()) {
						task.run();
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					scheduleNext(task, period, period, unit, isEnabledSupplier);
				}
			}), delay, unit);
		} catch (RejectedExecutionException ignored) {
		}
	}

	private static void runAsync(Runnable task) {
		VIRTUAL_THREAD_FACTORY.newThread(() -> runSafely(task)).start();
	}

	private static void runSafely(Runnable task) {
		try {
			task.run();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
