package main;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class AsyncDelayedScheduler {

	private final AtomicBoolean isEnabled = new AtomicBoolean(true);
	private static final int maxTasks = ForkJoinPool.commonPool().getParallelism();
	private final Runnable[] taskBatch = new Runnable[maxTasks];
	private int taskCount = 0;
	private final Object lock = new Object();

	/**
	 * Schedule a task with a delay.
	 * Adds the task to a batch that will be submitted for execution after the delay.
	 *
	 * @param task  The task to execute
	 * @param delay The delay before execution
	 * @param unit  The time unit for the delay
	 */
	public void scheduleWithDelay(Runnable task, long delay, TimeUnit unit) {
		synchronized (lock) {
			if (taskCount == taskBatch.length) {
				Runnable[] tasksToRun = new Runnable[taskCount];
				System.arraycopy(taskBatch, 0, tasksToRun, 0, taskCount);
				taskCount = 0;
				scheduleBatchWithDelay(tasksToRun, tasksToRun.length, 0, unit);
			}
			taskBatch[taskCount++] = task;
		}
		CompletableFuture.delayedExecutor(delay, unit, ForkJoinPool.commonPool())
		.execute(() -> {
			Runnable[] tasksToRun;
			int tasksToRunCount;
			synchronized (lock) {
				tasksToRun = new Runnable[taskCount];
				System.arraycopy(taskBatch, 0, tasksToRun, 0, taskCount);
				tasksToRunCount = taskCount;
				taskCount = 0;
			}
			scheduleBatchWithDelay(tasksToRun, tasksToRunCount, 0, unit);
		});
	}

	/**
	 * Schedule multiple tasks with a delay.
	 *
	 * @param tasks   An array of tasks to execute
	 * @param count   The number of tasks to execute from the array
	 * @param delay   The delay before execution
	 * @param unit    The time unit for the delay
	 */
	public void scheduleBatchWithDelay(Runnable[] tasks, int count, long delay, TimeUnit unit) {
		CompletableFuture.delayedExecutor(delay, unit, ForkJoinPool.commonPool())
		.execute(() -> {
			if (isEnabled.get()) {
				for (int i = 0; i < count; i++) {
					try {
						tasks[i].run();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		});
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
		CompletableFuture.delayedExecutor(initialDelay, unit, ForkJoinPool.commonPool())
		.execute(() -> {
			if (isEnabledSupplier.get()) {
				try {
					task.run();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			scheduleAtFixedRate(task, period, period, unit, isEnabledSupplier);
		});
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
		scheduleAtFixedRate(task, initialDelay, period, unit, this::isEnabled);
	}

	/**
	 * Enable or disable the scheduler.
	 *
	 * @param enabled true to enable, false to disable
	 */
	public void setEnabled(boolean enabled) {
		isEnabled.set(enabled);
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
}
