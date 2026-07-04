/*
 * Description: AbstractTask extension adding interrupt-based hard timeout via a daemon monitor thread
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.task.timeout;

import com.ts.common.utils.ParamUtils;
import com.ts.exception.TaskExecutionException;
import com.ts.exception.TaskTimeoutException;
import com.ts.service.impl.TaskLogService;
import com.ts.task.AbstractTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link AbstractTask} extension that adds a hard timeout control mechanism.
 *
 * <p>When {@link #execute} is called, a timeout monitor is scheduled on a shared
 * single-thread daemon executor. If the task has not finished within {@code timeout}
 * seconds, the monitor interrupts the executing thread, causing the sleeping or
 * blocking operation to throw an {@link InterruptedException}, which is caught
 * and rethrown as a {@link com.ts.exception.TaskTimeoutException}.
 *
 * <p>On normal or error completion the timeout monitor is cancelled. On explicit
 * cancellation, the parent {@link AbstractTask#cancel()} is also called.
 *
 * <p>Recommended base class for tasks that cannot implement their own timeout
 * loop (e.g., {@link HttpTask}, {@link ScriptTask}) as opposed to
 * {@link com.ts.task.core.WaitTask}, which manages its own segmented-sleep loop.
 *
 * @see com.ts.task.core.WaitTask
 */
public abstract class TimeoutTask extends AbstractTask {
    private static final Logger logger = LogManager.getLogger(TimeoutTask.class);
    /**
     * Set to {@code true} by the timeout monitor thread the instant the deadline
     * elapses; read by the interrupted thread to distinguish a timeout interrupt
     * from a cancellation interrupt so the correct exception type is thrown.
     */
    protected final AtomicBoolean timeouted = new AtomicBoolean(false);

    // Shared timeout-monitor thread pool using a single daemon thread
    private static final ScheduledExecutorService TIMEOUT_SCHEDULER =
            Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "TaskTimeoutWatcher");
                t.setDaemon(true);
                return t;
            });

    // Future handle for the current task's timeout monitor
    private volatile ScheduledFuture<?> timeoutWatcher;

    // Do not call super.execute(); instead, manually drive the lifecycle to control exception handling
    // This gives us full control over how exceptions propagate from each lifecycle phase
    @Override
    /**
     * Executes.
     *
     * @param parameters  parameter map
     * @throws Exception  if an error occurs
     */
    public void execute(Map<String, Object> parameters) throws Exception {
        long startTime = System.currentTimeMillis();
        try {
            // record current thread and start time
            executingThread = Thread.currentThread();
            // initialize timeout, task id etc.
            initialize(parameters);
            logger.info("{} Task execution started: {}, {}, {}, timeout: {}", taskType, taskId, taskName, taskInstanceId, timeout);
            // replace All Variables
            parameters = replaceAllVariables(parameters);
            // Start the timeout monitoring thread
            scheduleTimeoutWatcher(timeout);

            // Check if canceled before preAction
            if (canceled.get()) {
                logger.warn("Task is already canceled: {}, {}, {}", taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Task is already canceled");
                return;
            }
            // Pre-action
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Executing pre-action");
            long preStartTime = System.currentTimeMillis();
            logger.debug("Executing pre-action: {}, {}, {}", taskId, taskName, taskInstanceId);
            preAction(parameters);
            long preEndTime = System.currentTimeMillis();
            logger.debug("Pre-action completed in {} ms: {}, {}, {}", preEndTime - preStartTime, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Pre-action completed");

            // Check if canceled before action
            if (canceled.get()) {
                logger.warn("Task is already canceled: {}, {}, {}", taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Task is already canceled");
                return;
            }
            // Main action
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Executing main action");
            long actionStartTime = System.currentTimeMillis();
            logger.debug("Executing main action: {}, {}, {}", taskId, taskName, taskInstanceId);
            action(parameters);
            long actionEndTime = System.currentTimeMillis();
            logger.debug("Main action completed in {} ms: {}, {}, {}", actionEndTime - actionStartTime, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Main action completed");

            // Check if canceled before postAction
            if (canceled.get()) {
                logger.warn("Task is already canceled: {}, {}, {}", taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Task is already canceled");
                return;
            }
            // Post-action
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Executing post-action");
            long postStartTime = System.currentTimeMillis();
            logger.debug("Executing post-action: {}, {}, {}", taskId, taskName, taskInstanceId);
            postAction(parameters);
            long postEndTime = System.currentTimeMillis();
            logger.debug("Post-action completed in {} ms: {}, {}, {}", postEndTime - postStartTime, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Post-action completed");
        } catch (Exception e) {
            // Check whether the interruption was caused by a timeout
            // Or TaskTimeoutException raised by subclass
            if (timeouted.get() || (e instanceof TaskTimeoutException)) {
                // Timeout occurred
                logger.error("Task was interrupted due to timeout: {}, {}, {}", taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Task was interrupted due to timeout: " + e.getMessage());
                if (e instanceof TaskTimeoutException) {
                    throw e;
                } else {
                    throw new TaskTimeoutException("Task timed out after " + timeout + " seconds", e, taskId, taskInstanceId);
                }
            }
            // Interrupted caused by a cancel
            else if (e instanceof InterruptedException) {
                // Do not restore the interrupt status, as JDBC drivers (e.g. MySQL) check it
                // and would fail subsequent database operations.
                logger.warn("Task interrupted due to cancellation: {}, {}, {}", taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Task interrupted: " + e.getMessage());
                throw new TaskExecutionException("Task interrupted: " + e.getMessage(), e, taskId, taskInstanceId);
            }
            // Task Execution Exception
            else if (e instanceof TaskExecutionException) {
                // Normal Task Execution Exception
                logger.error("Task execution failed: {}, {}, {}", taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Task execution failed: " + e.getMessage());
                throw e;
            }
            // Other Exceptions
            else {
                logger.error("Task execution failed: {}, {}, {}", taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Task execution failed: " + e.getMessage());
                throw new TaskExecutionException("Task execution failed: " + e.getMessage(), e, taskId, taskInstanceId);
            }
        } finally {
            cleanupResources();
            long endTime = System.currentTimeMillis();
            logger.info("{} Task execution completed in {} ms: {}, {}, {}", taskType, endTime - startTime, taskId, taskName, taskInstanceId);
        }
    }

    /**
     * Schedules the timeout monitor that will interrupt the executing thread after the timeout elapses
     *
     * @param timeoutSeconds the timeout duration in seconds
     */
    private void scheduleTimeoutWatcher(int timeoutSeconds) {
        timeoutWatcher = TIMEOUT_SCHEDULER.schedule(() -> {
            try {
                // Only fire if the task has not already been cancelled and the timeout has elapsed
                if (!canceled.get()) {
                    logger.error("Task timed out after {} seconds: {}, {}, {}", timeoutSeconds, taskId, taskName, taskInstanceId);
                    TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Task timed out after " + timeoutSeconds + " seconds");
                    if (timeouted.compareAndSet(false, true)) {
                        // Interrupt the main execution thread to trigger the timeout handling path
                        if (executingThread != null) {
                            executingThread.interrupt();
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error in timeout watcher: {}, {}, {}, {}", timeoutSeconds, taskId, taskName, taskInstanceId, e);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Error in timeout watcher: " + e.getMessage());
            } finally {
                cancelTimeoutWatcher();
            }
        }, timeoutSeconds, TimeUnit.SECONDS);
    }

    //@Override
    //public boolean cancel() {
    // Cancel the timeout monitor

    /// /cancelTimeoutWatcher();
    // Invoke the parent class cancel() method
    //return super.cancel();
    //}
    @Override
    /**
     * Cleanup the resources.
     */
    public void cleanupResources() {
        cancelTimeoutWatcher();
        super.cleanupResources();
    }

    /**
     * Cancels the timeout monitor future
     */
    private void cancelTimeoutWatcher() {
        if (timeoutWatcher != null && !timeoutWatcher.isDone()) {
            timeoutWatcher.cancel(false);
        }
    }
}