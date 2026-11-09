/*
 * Description: HashedWheelTimer-backed implementation with drift-correcting periodic scheduling and capacity limits
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.core.timewheel;

import com.ts.common.utils.UniqueIdGenerator;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
/**
 * {@link TimeWheel} implementation backed by Netty's {@link io.netty.util.HashedWheelTimer}.
 *
 * <p>Uses a single hashed-wheel timer with 512 slots and 100 ms tick duration,
 * giving a scheduling resolution of up to 100 ms. Periodic tasks are re-registered
 * after each execution using a planned-time accounting strategy that avoids drift:
 * the next fire time is computed from the <em>last planned</em> execution time,
 * not from the actual execution time, so short delays do not compound.
 *
 * <p>Memory management: a background cleanup executor removes expired
 * {@code TaskHolder} entries every 10 minutes. Task capacity is capped at
 * {@code MAX_TASK_CAPACITY} (100 000) to prevent unbounded growth.
 *
 * <p>Statistics counters ({@code tasksAdded}, {@code tasksExecuted}, etc.) are
 * exposed for the {@link com.ts.monitor.WorkflowMonitor} reporting path.
 *
 * @see CronScheduler
 */
public class NettyTimeWheel implements TimeWheel {
    private static final Logger logger = LogManager.getLogger(NettyTimeWheel.class);

    // Timer configuration
    private static final long DEFAULT_TICK_DURATION = 100;  // 100ms per tick
    private static final int DEFAULT_TICKS_PER_WHEEL = 512; // 512 slots in the wheel
    private static final TimeUnit DEFAULT_TICK_UNIT = TimeUnit.MILLISECONDS;

    // Memory management configuration
    private static final int MAX_TASK_CAPACITY = 100_000; // Maximum number of tasks
    private static final long CLEANUP_INTERVAL_MS = 600_000; // 10 minute task cleanup interval

    // The Netty HashedWheelTimer
    private final Timer wheelTimer;

    // Task storage with size limits - unified map for all task types
    private final Map<String, TaskHolder> taskMap = new ConcurrentHashMap<>();

    // Task cleanup scheduler
    private final ScheduledExecutorService cleanupExecutor;

    // State flag
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Statistics counters
    private final AtomicLong tasksAddedCounter = new AtomicLong(0);
    private final AtomicLong tasksExecutedCounter = new AtomicLong(0);
    private final AtomicLong tasksFailedCounter = new AtomicLong(0);
    private final AtomicLong tasksCancelledCounter = new AtomicLong(0);
    private final AtomicLong tasksExpiredCounter = new AtomicLong(0);
    private final Map<String, Long> nextExecutionTimes = new ConcurrentHashMap<>();

    /**
     * Constructor with default configuration
     */
    public NettyTimeWheel() {
        this(DEFAULT_TICK_DURATION, DEFAULT_TICK_UNIT, DEFAULT_TICKS_PER_WHEEL);
    }

    /**
     * Constructor with custom configuration
     *
     * @param tickDuration  Duration of a tick
     * @param unit          Time unit of tick duration
     * @param ticksPerWheel Number of ticks per wheel
     */
    public NettyTimeWheel(long tickDuration, TimeUnit unit, int ticksPerWheel) {
        this.wheelTimer = new HashedWheelTimer(tickDuration, unit, ticksPerWheel);
        // Create single-threaded executor for cleanup tasks
        this.cleanupExecutor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread thread = new Thread(r, "time-wheel-cleanup");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    /**
     * Starts.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting optimized time wheel");
            ((HashedWheelTimer) wheelTimer).start();
            // Schedule periodic cleanup task
            cleanupExecutor.scheduleAtFixedRate(
                    this::cleanupExpiredTasks,
                    CLEANUP_INTERVAL_MS,
                    CLEANUP_INTERVAL_MS,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    @Override
    /**
     * Stops.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping time wheel");
            // Cancel all tasks
            taskMap.values().forEach(TaskHolder::cancel);
            taskMap.clear();
            nextExecutionTimes.clear();
            // Stop the wheel timer
            wheelTimer.stop();
            // Shutdown cleanup executor
            cleanupExecutor.shutdownNow();
        }
    }

    @Override
    /**
     * Adds the task.
     *
     * @param task  task value
     * @param delay  delay value
     * @param unit  unit value
     * @return the result as a String
     */
    public String addTask(Runnable task, long delay, TimeUnit unit) {
        checkRunning();
        checkCapacity();
        // Generate a unique task ID
        String taskId = UniqueIdGenerator.generateId(UniqueIdGenerator.IdType.TIME_WHEEL);
        // Create task holder
        TaskHolder holder = new TaskHolder(taskId, task, false);
        // Set expiration time (current time + delay + buffer(10 minute))
        long expireTimeMs = System.currentTimeMillis() + unit.toMillis(delay) + 600_000;
        holder.setExpireTime(expireTimeMs);
        // Create and schedule the timer task
        Timeout timeout = wheelTimer.newTimeout(createTimerTask(holder), delay, unit);
        holder.setTimeout(timeout);
        // Store for potential cancellation
        taskMap.put(taskId, holder);
        // Update statistics
        tasksAddedCounter.incrementAndGet();
        nextExecutionTimes.put(taskId, System.currentTimeMillis() + unit.toMillis(delay));

        logger.info("Added task {} with delay {} {}, expires at {}",
                taskId, delay, unit, expireTimeMs);
        return taskId;
    }

    @Override
    /**
     * Removes the task.
     *
     * @param taskId  identifier
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean removeTask(String taskId) {
        checkRunning();
        TaskHolder holder = taskMap.remove(taskId);
        if (holder != null) {
            holder.cancel();
            // Update statistics
            tasksCancelledCounter.incrementAndGet();
            nextExecutionTimes.remove(taskId);

            logger.debug("Removed task: {}", taskId);
            return true;
        }
        return false;
    }

    @Override
    /**
     * Schedules the at fixed rate.
     *
     * @param task  task value
     * @param initialDelay  initialDelay value
     * @param period  period value
     * @param unit  unit value
     * @return the result as a String
     */
    public String scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        checkRunning();
        checkCapacity();
        // Generate a unique task ID for this scheduled entry
        String taskId = UniqueIdGenerator.generateId(UniqueIdGenerator.IdType.TIME_WHEEL);
        // Calculate the first execution time and the initial expiry time
        long initialExecutionTime = System.currentTimeMillis() + unit.toMillis(initialDelay);
        long expireTimeMs = initialExecutionTime + unit.toMillis(period) * PeriodicTaskHolder.MAX_MISSED_EXECUTIONS;
        // Create the periodic task holder
        PeriodicTaskHolder holder = new PeriodicTaskHolder(taskId, task, initialDelay, period, unit);
        holder.setExpireTime(expireTimeMs);
        holder.setLastPlannedExecutionTime(initialExecutionTime - unit.toMillis(period)); // Initialize the planned execution time
        // Schedule the first execution
        Timeout timeout = wheelTimer.newTimeout(createTimerTask(holder), initialDelay, unit);
        holder.setTimeout(timeout);
        // Store the task holder in the map
        taskMap.put(taskId, holder);
        // Update statistics
        tasksAddedCounter.incrementAndGet();
        nextExecutionTimes.put(taskId, initialExecutionTime);

        logger.debug("Added periodic task {} with initial delay {} {} and period {} {}, expires at {}",
                taskId, initialDelay, unit, period, unit, expireTimeMs);
        return taskId;
    }

    /**
     * Creates a timer task with error handling and cleanup
     */
    private TimerTask createTimerTask(TaskHolder holder) {
        return timeout -> {
            try {
                logger.debug("Preparing to execute task: {}", holder.getTaskId());
                if (running.get() && !holder.isCancelled()) {
                    holder.run();
                    // Update statistics on successful execution
                    tasksExecutedCounter.incrementAndGet();

                    logger.debug("Task execution completed: {}", holder.getTaskId());
                } else {
                    logger.debug("Task has been skipped: {}, running status: {}, cancelled status: {}",
                            holder.getTaskId(), running.get(), holder.isCancelled());
                }
            } catch (Exception e) {
                // Update statistics on failed execution
                tasksFailedCounter.incrementAndGet();

                logger.error("Error executing task: " + holder.getTaskId(), e);
            } finally {
                if (!holder.isPeriodic() && !holder.isCancelled()) {
                    // Remove one-time task after execution
                    taskMap.remove(holder.getTaskId());
                    nextExecutionTimes.remove(holder.getTaskId());
                    logger.debug("One-time task has been removed from task map: {}", holder.getTaskId());
                }
            }
        };
    }

    /**
     * Periodically clean up expired or cancelled tasks
     */
    private void cleanupExpiredTasks() {
        try {
            long now = System.currentTimeMillis();
            int removed = 0;
            for (Map.Entry<String, TaskHolder> entry : taskMap.entrySet()) {
                TaskHolder holder = entry.getValue();
                // Remove cancelled or expired tasks
                if (holder.isCancelled() || (holder.getExpireTime() > 0 && holder.getExpireTime() < now)) {
                    if (taskMap.remove(entry.getKey()) != null) {
                        logger.debug("Removed task: {}", entry.getKey());
                        nextExecutionTimes.remove(entry.getKey());
                        holder.cancel();
                        removed++;
                        // Update statistics
                        if (holder.getExpireTime() > 0 && holder.getExpireTime() < now) {
                            tasksExpiredCounter.incrementAndGet();
                        }
                    }
                }
            }
            if (removed > 0) {
                logger.debug("Cleaned up {} expired or cancelled tasks", removed);
            }
        } catch (Exception e) {
            logger.error("Error during task cleanup", e);
        }
    }

    /**
     * Check if the time wheel is running
     */
    private void checkRunning() {
        if (!running.get()) {
            throw new IllegalStateException("Time wheel is not running");
        }
    }

    /**
     * Check if we have capacity for new tasks
     */
    private void checkCapacity() {
        if (taskMap.size() >= MAX_TASK_CAPACITY) {
            throw new IllegalStateException("Time wheel task capacity exceeded: " + MAX_TASK_CAPACITY);
        }
    }

    /**
     * Get statistics about the time wheel and its tasks
     *
     * @return Map containing various statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        // General stats
        stats.put("running", running.get());
        stats.put("totalActiveTasks", taskMap.size());
        stats.put("oneTimeTasks", taskMap.values().stream().filter(holder -> !holder.isPeriodic()).count());
        stats.put("periodicTasks", taskMap.values().stream().filter(TaskHolder::isPeriodic).count());

        // Counters
        stats.put("tasksAdded", tasksAddedCounter.get());
        stats.put("tasksExecuted", tasksExecutedCounter.get());
        stats.put("tasksFailed", tasksFailedCounter.get());
        stats.put("tasksCancelled", tasksCancelledCounter.get());
        stats.put("tasksExpired", tasksExpiredCounter.get());

        // Next executions
        stats.put("nextExecutionTasks", new HashMap<>(nextExecutionTimes));

        // Task details
        Map<String, Object> taskDetails = new HashMap<>();
        for (Map.Entry<String, TaskHolder> entry : taskMap.entrySet()) {
            String taskId = entry.getKey();
            TaskHolder holder = entry.getValue();
            taskDetails.put(taskId + ".periodic", holder.isPeriodic());
            taskDetails.put(taskId + ".expireTime", holder.getExpireTime());
            taskDetails.put(taskId + ".cancelled", holder.isCancelled());
        }
        stats.put("taskDetails", taskDetails);

        return stats;
    }

    /**
     * Base class for task holders to manage task lifecycle
     */
    private static class TaskHolder {
        private final String taskId;
        private final Runnable task;
        private final boolean periodic;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile Timeout timeout;
        private volatile long expireTime = -1; // -1 means no expiration

        /**
         * Constructs a TaskHolder for a simple one-shot or periodic task.
         *
         * @param taskId   unique identifier for this task entry
         * @param task     the runnable to execute at each fire time
         * @param periodic {@code true} if the task should repeat; {@code false} for one-shot
         */
        public TaskHolder(String taskId, Runnable task, boolean periodic) {
            this.taskId = taskId;
            this.task = task;  // Use a strong reference to the Task instance
            this.periodic = periodic;
        }

        /**
         * Returns the task id.
         *
         * @return the result as a String
         */
        public String getTaskId() {
            return taskId;
        }

        /**
         * Checks whether the periodic.
         *
         * @return {@code true} if successful, {@code false} otherwise
         */
        public boolean isPeriodic() {
            return periodic;
        }

        /**
         * Checks whether the cancelled.
         *
         * @return {@code true} if successful, {@code false} otherwise
         */
        public boolean isCancelled() {
            return cancelled.get();
        }

        /**
         * Returns the expire time.
         *
         * @return the numeric result
         */
        public long getExpireTime() {
            return expireTime;
        }

        /**
         * Sets the expire time.
         *
         * @param expireTime  date/time value
         */
        public void setExpireTime(long expireTime) {
            this.expireTime = expireTime;
        }

        /**
         * Sets the timeout.
         *
         * @param timeout  date/time value
         */
        public void setTimeout(Timeout timeout) {
            this.timeout = timeout;
        }

        /**
         * Runs.
         */
        public void run() {
            if (task != null) {
                logger.debug("Running task {} ", taskId);
                task.run();
            } else {
                // Task was garbage collected, mark as cancelled
                logger.debug("Task was null, marking as cancelled {} ", taskId);
                cancel();
            }
        }

        /**
         * Cancels.
         */
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                if (timeout != null) {
                    timeout.cancel();
                }
            }
        }
    }

    /**
     * Extension of TaskHolder for periodic tasks
     */
    private class PeriodicTaskHolder extends TaskHolder {
        private final long period;
        private final TimeUnit unit;
        private volatile long lastPlannedExecutionTime;
        private static final int MAX_MISSED_EXECUTIONS = 5;

        /**
         * Constructs a PeriodicTaskHolder for a repeating task.
         * The first execution fires after {@code initialDelay} {@code unit}s;
         * subsequent executions repeat every {@code period} {@code unit}s.
         *
         * @param taskId       unique identifier for this task entry
         * @param task         the runnable to execute at each fire time
         * @param initialDelay delay before the first execution
         * @param period       interval between successive executions
         * @param unit         time unit for {@code initialDelay} and {@code period}
         */
        public PeriodicTaskHolder(String taskId, Runnable task, long initialDelay, long period, TimeUnit unit) {
            super(taskId, task, true);
            this.period = period;
            this.unit = unit;
            // Initialize planned time to firstExecutionTime minus one period
            this.lastPlannedExecutionTime = System.currentTimeMillis() + unit.toMillis(initialDelay) - unit.toMillis(period);
        }

        @Override
        /**
         * Runs.
         */
        public void run() {
            if (isCancelled() || !running.get()) {
                return;
            }
            try {
                // Execute the actual scheduled task
                super.run();

                // Calculate the next planned execution time
                long now = System.currentTimeMillis();
                long nextPlannedTime = lastPlannedExecutionTime + unit.toMillis(period);

                // Handle missed executions: if the current time has advanced too far past the planned time, skip to the next valid time point
                while (nextPlannedTime <= now - unit.toMillis(period)) {
                    nextPlannedTime += unit.toMillis(period);
                    logger.warn("Skipping missed execution for periodic task: {}", getTaskId());
                }

                long delay = Math.max(0, nextPlannedTime - now);

                // Schedule the next execution in the time wheel
                if (!isCancelled() && running.get()) {
                    Timeout timeout = wheelTimer.newTimeout(
                            createTimerTask(this),
                            delay,
                            TimeUnit.MILLISECONDS
                    );
                    setTimeout(timeout);
                    lastPlannedExecutionTime = nextPlannedTime; // Update the recorded planned execution time

                    // Update statistics for the next execution time
                    nextExecutionTimes.put(getTaskId(), nextPlannedTime);
                }

                // Extend the expiry time in the time wheel
                extendExpiration();
            } catch (Exception e) {
                logger.error("Periodic task execution failed: {}", getTaskId(), e);
            }
        }

        /**
         * Extends the expiry deadline to cover multiple future periods
         */
        private void extendExpiration() {
            long newExpireTime = lastPlannedExecutionTime + unit.toMillis(period) * (MAX_MISSED_EXECUTIONS + 1);
            setExpireTime(newExpireTime);
        }

        /**
         * Sets the last planned execution time.
         *
         * @param time  date/time value
         */
        public void setLastPlannedExecutionTime(long time) {
            this.lastPlannedExecutionTime = time;
        }
    }
}
