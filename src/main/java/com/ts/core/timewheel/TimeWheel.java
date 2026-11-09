/*
 * Description: Contract for a time-wheel timer used by the cron scheduler
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.core.timewheel;

import java.util.concurrent.TimeUnit;
/**
 * Contract for a time-wheel timer used by the cron scheduler.
 *
 * <p>Supports scheduling one-shot and periodic tasks with millisecond precision.
 * The default implementation, {@link NettyTimeWheel}, wraps Netty's
 * {@link io.netty.util.HashedWheelTimer}.
 */
public interface TimeWheel {
    /**
     * Start the time wheel
     */
    void start();

    /**
     * Stop the time wheel
     */
    void stop();

    /**
     * Add a task to be executed after a delay
     *
     * @param task  Task to execute
     * @param delay Delay before execution
     * @param unit  Time unit of the delay
     * @return Task ID
     */
    String addTask(Runnable task, long delay, TimeUnit unit);

    /**
     * Remove a task from the time wheel
     *
     * @param taskId Task ID
     * @return true if task was removed, false otherwise
     */
    boolean removeTask(String taskId);

    /**
     * Schedule a task to be executed periodically
     *
     * @param task         Task to execute
     * @param initialDelay Initial delay before first execution
     * @param period       Period between executions
     * @param unit         Time unit of the delay and period
     * @return Task ID
     */
    String scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit);
}