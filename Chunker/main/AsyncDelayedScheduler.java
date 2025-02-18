package main;

import java.util.concurrent.*;
import java.util.function.Supplier;

public class AsyncDelayedScheduler {

	private volatile boolean isEnabled = true;

	/**
	 * Schedule a task with a delay.
	 *
	 * @param task  The task to execute
	 * @param delay The delay before execution
	 * @param unit  The time unit for the delay
	 */
	public void scheduleWithDelay(Runnable task, long delay, TimeUnit unit) {
		Executor delayedExecutor = CompletableFuture.delayedExecutor(delay, unit);
		CompletableFuture.runAsync(() -> {
			if (isEnabled) {
				task.run();
			}
		}, delayedExecutor).exceptionally(e -> {
			e.printStackTrace();
			return null;
		});
	}

	/**
	 * Schedule a task at a fixed rate with dynamic delay control.
	 *
	 * @param task              The task to execute
	 * @param initialDelay      The initial delay before first execution
	 * @param period            The period between executions
	 * @param unit              The time unit for delay and period
	 * @param isEnabledSupplier Supplier to determine if the task should run
	 */
	public void scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit, Supplier<Boolean> isEnabledSupplier) {
		Runnable scheduledTask = new Runnable() {
			@Override
			public void run() {
				if (isEnabledSupplier.get()) {
					try {
						task.run();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				scheduleWithDelay(this, period, unit);
			}
		};
		scheduleWithDelay(scheduledTask, initialDelay, unit);
	}

	/**
	 * Enable or disable the scheduler.
	 *
	 * @param enabled true to enable, false to disable
	 */
	public void setEnabled(boolean enabled) {
		this.isEnabled = enabled;
	}

	/**
	 * Check if the scheduler is enabled.
	 *
	 * @return true if enabled, false otherwise
	 */
	public boolean isEnabled() {
		return isEnabled;
	}

	/**
	 * Supplier for the scheduler's enabled status.
	 *
	 * @return a Supplier<Boolean> for the scheduler's status
	 */
	public Supplier<Boolean> isEnabledSupplier() {
		return () -> isEnabled;
	}
}
