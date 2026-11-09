/*
 * Description: Cron-based workflow scheduler backed by NettyTimeWheel with hot-reload, retry, and maintenance support
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.core.scheduler;

import com.ts.core.engine.WorkflowEngine;
import com.ts.core.timewheel.TimeWheel;
import com.ts.dao.WfSchedulerDao;
import com.ts.model.entity.WfScheduler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.ParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
/**
 * Production implementation of {@link Scheduler} that manages cron-triggered
 * workflow executions via a {@link com.ts.core.timewheel.TimeWheel}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Loads all enabled {@link com.ts.model.entity.WfScheduler} records from
 *       the database at startup and registers them with the time wheel.</li>
 *   <li>Computes the next fire time using {@link CronExpression} and re-registers
 *       the task after each successful execution.</li>
 *   <li>Supports hot add/update/delete of schedules while the scheduler is running
 *       via a {@link java.util.concurrent.locks.ReadWriteLock}-protected schedule map.</li>
 *   <li>Runs a periodic maintenance task that removes stale entries and logs
 *       scheduler health statistics.</li>
 * </ul>
 *
 * <p>Retry policy: a single retry attempt is made after {@code RETRY_DELAY_MS}
 * if the first workflow start fails; this prevents transient DB errors from
 * silently dropping scheduled executions.
 *
 * @see CronExpression
 * @see com.ts.core.timewheel.NettyTimeWheel
 */
public class CronScheduler implements Scheduler {
    private static final Logger logger = LogManager.getLogger(CronScheduler.class);

    // Configuration constants
    private static final int MAX_SCHEDULER_CAPACITY = 10_000;
    private static final int CLEANUP_INTERVAL_MINUTES = 60;
    private static final int MAX_RETRY_ATTEMPTS = 1;
    private static final long RETRY_DELAY_MS = 5_000; // 5 seconds

    private final WfSchedulerDao wfSchedulerDao;
    private final WorkflowEngine workflowEngine;
    private final TimeWheel timeWheel;

    // Memory-efficient task storage with metadata
    private final Map<String, ScheduledWorkflow> scheduledWorkflows = new ConcurrentHashMap<>();

    // Thread-safety locks
    private final ReadWriteLock scheduleLock = new ReentrantReadWriteLock();
    private final Lock readLock = scheduleLock.readLock();
    private final Lock writeLock = scheduleLock.writeLock();

    // Scheduled executors for maintenance tasks
    private final ScheduledExecutorService maintenanceExecutor;

    // Statistics counters
    private final AtomicLong workflowsExecutedCounter = new AtomicLong(0);
    private final AtomicLong failedExecutionsCounter = new AtomicLong(0);
    private final AtomicLong maintenanceCyclesCounter = new AtomicLong(0);
    private final AtomicLong staleWorkflowsRemovedCounter = new AtomicLong(0);
    private final AtomicLong scheduleChangesCounter = new AtomicLong(0);

    /**
     * Constructor with required dependencies
     *
     * @param wfSchedulerDao Scheduler DAO
     * @param workflowEngine Workflow engine
     * @param timeWheel      Time wheel
     */
    public CronScheduler(WfSchedulerDao wfSchedulerDao, WorkflowEngine workflowEngine, TimeWheel timeWheel) {
        this.wfSchedulerDao = wfSchedulerDao;
        this.workflowEngine = workflowEngine;
        this.timeWheel = timeWheel;

        // Create daemon executor for maintenance tasks
        this.maintenanceExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread thread = new Thread(r, "scheduler-maintenance");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    /**
     * Initialises.
     */
    public void initialize() {
        logger.info("Initializing optimized cron scheduler");

        // Start the time wheel
        timeWheel.start();

        // Schedule periodic maintenance
        maintenanceExecutor.scheduleAtFixedRate(
                this::performMaintenance,
                CLEANUP_INTERVAL_MINUTES,
                CLEANUP_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );

        // Load all schedules from the database
        refreshAllSchedules();

        logger.info("Cron scheduler initialized with {} scheduled workflows", scheduledWorkflows.size());
    }

    @Override
    /**
     * Schedules.
     *
     * @param workflowId  workflowId identifier
     * @param cronExpression  cronExpression value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean schedule(String workflowId, String cronExpression) {
        if (workflowId == null || cronExpression == null) {
            logger.error("Cannot schedule with null workflowId or cronExpression");
            return false;
        }

        logger.info("Scheduling workflow: {} with cron: {}", workflowId, cronExpression);

        // Validate cron expression before acquiring lock
        try {
            CronExpression.validateExpression(cronExpression);
        } catch (ParseException e) {
            logger.error("Invalid cron expression: " + cronExpression, e);
            return false;
        }

        writeLock.lock();
        try {
            // Check capacity
            if (scheduledWorkflows.size() >= MAX_SCHEDULER_CAPACITY) {
                logger.error("Maximum scheduler capacity reached: {}", MAX_SCHEDULER_CAPACITY);
                return false;
            }

            // Check if workflow is already scheduled
            if (scheduledWorkflows.containsKey(workflowId)) {
                logger.warn("Workflow is already scheduled: {}", workflowId);
                return false;
            }

            // Schedule the workflow
            boolean result = doScheduleWorkflow(workflowId, cronExpression);
            if (result) {
                scheduleChangesCounter.incrementAndGet();
            }
            return result;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    /**
     * Un the schedule.
     *
     * @param workflowId  workflowId identifier
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean unSchedule(String workflowId) {
        if (workflowId == null) {
            logger.error("Cannot unSchedule with null workflowId");
            return false;
        }

        logger.info("UnScheduling workflow: {}", workflowId);

        writeLock.lock();
        try {
            // Check if workflow is scheduled
            ScheduledWorkflow workflow = scheduledWorkflows.get(workflowId);
            if (workflow == null) {
                logger.warn("Workflow is not scheduled: {}", workflowId);
                return false;
            }

            // Cancel the task in the time wheel
            if (workflow.getTaskId() != null) {
                timeWheel.removeTask(workflow.getTaskId());
            }

            // Remove from the scheduled workflows map
            scheduledWorkflows.remove(workflowId);

            // Update statistics
            scheduleChangesCounter.incrementAndGet();

            logger.info("Successfully unscheduled workflow: {}", workflowId);
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    /**
     * Updates the schedule.
     *
     * @param workflowId  workflowId identifier
     * @param cronExpression  cronExpression value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateSchedule(String workflowId, String cronExpression) {
        if (workflowId == null || cronExpression == null) {
            logger.error("Cannot update schedule with null workflowId or cronExpression");
            return false;
        }

        logger.info("Updating schedule for workflow: {} with cron: {}", workflowId, cronExpression);

        // Validate cron expression before acquiring lock
        try {
            CronExpression.validateExpression(cronExpression);
        } catch (ParseException e) {
            logger.error("Invalid cron expression: " + cronExpression, e);
            return false;
        }

        writeLock.lock();
        try {
            // UnSchedule existing schedule
            ScheduledWorkflow workflow = scheduledWorkflows.get(workflowId);
            if (workflow != null) {
                if (workflow.getTaskId() != null) {
                    timeWheel.removeTask(workflow.getTaskId());
                }
            }

            // Schedule with new cron expression
            boolean result = doScheduleWorkflow(workflowId, cronExpression);
            if (result) {
                scheduleChangesCounter.incrementAndGet();
            }
            return result;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    /**
     * Refreshes the all schedules.
     */
    public void refreshAllSchedules() {
        logger.info("Refreshing all schedules");

        writeLock.lock();
        try {
            // UnSchedule all existing schedules
            for (String workflowId : new ArrayList<>(scheduledWorkflows.keySet())) {
                ScheduledWorkflow workflow = scheduledWorkflows.get(workflowId);
                if (workflow != null && workflow.getTaskId() != null) {
                    timeWheel.removeTask(workflow.getTaskId());
                }
            }
            scheduledWorkflows.clear();

            // Load all schedules from the database
            List<WfScheduler> schedules = wfSchedulerDao.getAllActiveSchedules();
            int successCount = 0;

            for (WfScheduler schedule : schedules) {
                try {
                    // Validate cron expression
                    CronExpression.validateExpression(schedule.getCron());

                    // Schedule the workflow
                    if (doScheduleWorkflow(schedule.getWorkflowId(), schedule.getCron())) {
                        successCount++;
                    }
                } catch (ParseException e) {
                    logger.error("Invalid cron expression for workflow: " + schedule.getWorkflowId(), e);
                }
            }

            // Update statistics
            scheduleChangesCounter.incrementAndGet();

            logger.info("Successfully scheduled {} out of {} workflows", successCount, schedules.size());
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    /**
     * Shuts down.
     */
    public void shutdown() {
        logger.info("Shutting down cron scheduler");

        writeLock.lock();
        try {
            // UnSchedule all workflows
            for (ScheduledWorkflow workflow : scheduledWorkflows.values()) {
                if (workflow.getTaskId() != null) {
                    timeWheel.removeTask(workflow.getTaskId());
                }
            }
            scheduledWorkflows.clear();

            // Shutdown maintenance executor
            maintenanceExecutor.shutdownNow();

            // Stop the time wheel
            timeWheel.stop();

            logger.info("Cron scheduler shutdown complete");
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Schedule a workflow with the time wheel (internal implementation)
     *
     * @param workflowId     Workflow ID
     * @param cronExpression Cron expression
     * @return true if successful, false otherwise
     */
    private boolean doScheduleWorkflow(String workflowId, String cronExpression) {
        try {
            // Create cron expression
            CronExpression cron = new CronExpression(cronExpression);

            // Create a single runnable to handle both execution and rescheduling
            ScheduledWorkflowTask workflowTask = new ScheduledWorkflowTask(workflowId, cron);

            // Calculate the initial delay
            Date now = new Date();
            Date nextRunTime = cron.getNextValidTimeAfter(now);

            if (nextRunTime == null) {
                logger.warn("No next run time for cron expression: {}", cronExpression);
                return false;
            }

            long initialDelay = nextRunTime.getTime() - now.getTime();
            if (initialDelay < 0) {
                initialDelay = 0;
            }

            // Schedule the workflow
            String taskId = timeWheel.addTask(workflowTask, initialDelay, TimeUnit.MILLISECONDS);

            // Create and store scheduled workflow
            ScheduledWorkflow workflow = new ScheduledWorkflow(
                    workflowId,
                    cronExpression,
                    taskId,
                    nextRunTime,
                    System.currentTimeMillis()
            );
            scheduledWorkflows.put(workflowId, workflow);

            logger.info("Scheduled workflow: {} to run at: {}", workflowId, nextRunTime);
            return true;
        } catch (ParseException e) {
            logger.error("Error parsing cron expression: " + cronExpression, e);
            return false;
        } catch (Exception e) {
            logger.error("Error scheduling workflow: " + workflowId, e);
            return false;
        }
    }

    /**
     * Perform maintenance tasks like cleaning up stale schedules
     */
    private void performMaintenance() {
        try {
            logger.debug("Performing scheduler maintenance");

            // Update statistics
            maintenanceCyclesCounter.incrementAndGet();

            // Clean up stale schedules
            cleanupStaleSchedules();

            // Check for database consistency
            validateScheduleConsistency();

            logger.debug("Maintenance completed");
        } catch (Exception e) {
            logger.error("Error during scheduler maintenance", e);
        }
    }

    /**
     * Clean up stale schedules that are no longer active
     */
    private void cleanupStaleSchedules() {
        List<String> staleWorkflows = new ArrayList<>();
        readLock.lock();
        try {
            List<String> activeWorkflowIds = wfSchedulerDao.getAllActiveWorkflowIds();
            Set<String> activeIds = new HashSet<>(activeWorkflowIds);
            for (String workflowId : scheduledWorkflows.keySet()) {
                if (!activeIds.contains(workflowId)) {
                    staleWorkflows.add(workflowId);
                }
            }
        } catch (Exception e) {
            logger.error("Error cleaning up stale schedules", e);
            return;
        } finally {
            readLock.unlock();
        }

        int removedCount = 0;
        for (String workflowId : staleWorkflows) {
            logger.warn("Unscheduled workflow {}, because it is no longer active in the database", workflowId);
            if (unSchedule(workflowId)) {
                removedCount++;
                staleWorkflowsRemovedCounter.incrementAndGet();
            }
        }

        if (removedCount > 0) {
            logger.info("Cleaned up {} stale scheduled workflows", removedCount);
        }
    }

    /**
     * Validate that all scheduled workflows are consistent with the database
     */
    private void validateScheduleConsistency() {
        List<String> missingWorkflows = new ArrayList<>();
        List<String> inconsistentWorkflows = new ArrayList<>();
        List<String> missingInMemory = new ArrayList<>();
        Map<String, String> dbScheduleMap = new HashMap<>();

        readLock.lock();
        try {
            List<WfScheduler> dbSchedules = wfSchedulerDao.getAllActiveSchedules();
            for (WfScheduler schedule : dbSchedules) {
                dbScheduleMap.put(schedule.getWorkflowId(), schedule.getCron());
            }

            for (String workflowId : scheduledWorkflows.keySet()) {
                if (!dbScheduleMap.containsKey(workflowId)) {
                    missingWorkflows.add(workflowId);
                }
            }

            for (Map.Entry<String, String> entry : dbScheduleMap.entrySet()) {
                String workflowId = entry.getKey();
                String dbCron = entry.getValue();
                ScheduledWorkflow workflow = scheduledWorkflows.get(workflowId);
                if (workflow != null && !workflow.getCronExpression().equals(dbCron)) {
                    inconsistentWorkflows.add(workflowId);
                }
            }

            for (String workflowId : dbScheduleMap.keySet()) {
                if (!scheduledWorkflows.containsKey(workflowId)) {
                    missingInMemory.add(workflowId);
                }
            }
        } catch (Exception e) {
            logger.error("Error validating schedule consistency", e);
            return;
        } finally {
            readLock.unlock();
        }

        for (String workflowId : missingWorkflows) {
            logger.warn("Removing workflow {} not found in database", workflowId);
            unSchedule(workflowId);
            staleWorkflowsRemovedCounter.incrementAndGet();
        }

        for (String workflowId : inconsistentWorkflows) {
            String dbCron = dbScheduleMap.get(workflowId);
            logger.warn("Updating inconsistent schedule for workflow {} to {}", workflowId, dbCron);
            updateSchedule(workflowId, dbCron);
        }

        for (String workflowId : missingInMemory) {
            String cronExpression = dbScheduleMap.get(workflowId);
            logger.warn("Adding missing schedule for workflow {} with cron {}", workflowId, cronExpression);
            schedule(workflowId, cronExpression);
        }
    }

    /**
     * Get statistics about the scheduler and its workflows
     *
     * @return Map containing various statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // General stats
        stats.put("totalScheduledWorkflows", scheduledWorkflows.size());
        stats.put("workflowsExecuted", workflowsExecutedCounter.get());
        stats.put("failedExecutions", failedExecutionsCounter.get());
        stats.put("maintenanceCycles", maintenanceCyclesCounter.get());
        stats.put("staleWorkflowsRemoved", staleWorkflowsRemovedCounter.get());
        stats.put("scheduleChanges", scheduleChangesCounter.get());

        // Detailed workflow information
        Map<String, Object> workflowDetails = new HashMap<>();
        for (Map.Entry<String, ScheduledWorkflow> entry : scheduledWorkflows.entrySet()) {
            String workflowId = entry.getKey();
            ScheduledWorkflow workflow = entry.getValue();

            Map<String, Object> details = new HashMap<>();
            details.put("cronExpression", workflow.getCronExpression());
            details.put("nextRunTime", workflow.getNextRunTime());
            details.put("lastUpdated", new Date(workflow.getLastUpdated()));

            workflowDetails.put(workflowId, details);
        }
        stats.put("scheduledWorkflows", workflowDetails);

        return stats;
    }

    /**
     * Combined task class to handle both workflow execution and rescheduling
     */
    private class ScheduledWorkflowTask implements Runnable {
        private final String workflowId;
        private final CronExpression cronExpression;
        private final AtomicInteger retryCount = new AtomicInteger(0);

        /**
         * Constructs a ScheduledWorkflowTask for a simple periodic trigger.
         *
         * @param workflowId     the workflow definition ID to launch
         * @param cronExpression the parsed cron expression driving the schedule
         */
        public ScheduledWorkflowTask(String workflowId, CronExpression cronExpression) {
            this.workflowId = workflowId;
            this.cronExpression = cronExpression;
        }

        @Override
        /**
         * Runs.
         */
        public void run() {
            boolean retrying = false;
            try {
                // check is it running workflow
                if (!workflowEngine.isRunningWorkflow(workflowId)) {
                    // Execute the workflow
                    executeWorkflow();
                    // Reset retry count after successful execution
                    retryCount.set(0);
                    // Update statistics for successful execution
                    workflowsExecutedCounter.incrementAndGet();
                } else {
                    logger.warn("Workflow engine rejected: Workflow {} has an active instance already running, schedule next time", workflowId);
                }
            } catch (Exception e) {
                // Update statistics for failed execution
                failedExecutionsCounter.incrementAndGet();

                logger.error("Error executing workflow: " + workflowId, e);

                // Schedule retry; skip the normal next-cron registration to avoid double-firing.
                // The retry task's own finally block will call scheduleNextExecution().
                if (retryCount.incrementAndGet() <= MAX_RETRY_ATTEMPTS) {
                    scheduleRetry();
                    retrying = true;
                }
            } finally {
                if (!retrying) {
                    scheduleNextExecution();
                }
            }
        }

        /**
         * Execute the workflow and handle exceptions
         */
        private void executeWorkflow() {
            logger.info("Starting scheduled workflow: {}", workflowId);

            try {
                workflowEngine.start(workflowId, null);
            } catch (Exception e) {
                logger.error("Error executing workflow: " + workflowId, e);
                throw e; // Rethrow to trigger retry logic
            }
        }

        /**
         * Schedule a retry attempt
         */
        private void scheduleRetry() {
            logger.info("Scheduling retry attempt {} for workflow: {}",
                    retryCount.get(), workflowId);

            // Schedule a retry
            String retryTaskId = timeWheel.addTask(this, RETRY_DELAY_MS, TimeUnit.MILLISECONDS);

            // Update task ID in scheduled workflow
            writeLock.lock();
            try {
                ScheduledWorkflow workflow = scheduledWorkflows.get(workflowId);
                if (workflow != null) {
                    workflow.setTaskId(retryTaskId);
                }
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * Schedule the next execution of this workflow
         */
        private void scheduleNextExecution() {
            writeLock.lock();
            try {
                // Check if the workflow is still scheduled
                if (!scheduledWorkflows.containsKey(workflowId)) {
                    logger.info("Workflow no longer scheduled, not rescheduling: {}", workflowId);
                    return;
                }

                // Calculate the next run time
                Date now = new Date();
                Date nextRunTime = cronExpression.getNextValidTimeAfter(now);

                if (nextRunTime == null) {
                    logger.warn("No future execution for workflow: {}", workflowId);
                    scheduledWorkflows.remove(workflowId);
                    return;
                }

                long delay = nextRunTime.getTime() - now.getTime();
                if (delay < 0) {
                    delay = 0;
                }

                // Create a new task for the next execution
                String taskId = timeWheel.addTask(this, delay, TimeUnit.MILLISECONDS);

                // Update the scheduled workflow
                ScheduledWorkflow workflow = scheduledWorkflows.get(workflowId);
                workflow.setTaskId(taskId);
                workflow.setNextRunTime(nextRunTime);
                workflow.setLastUpdated(System.currentTimeMillis());

                logger.info("Rescheduled workflow: {} to run at: {}", workflowId, nextRunTime);
            } catch (Exception e) {
                logger.error("Error rescheduling workflow: " + workflowId, e);
            } finally {
                writeLock.unlock();
            }
        }
    }

    /**
     * Data class to store scheduled workflow information
     */
    private static class ScheduledWorkflow {
        private final String workflowId;
        private final String cronExpression;
        private String taskId;
        private Date nextRunTime;
        private long lastUpdated;

        /**
         * Constructs a ScheduledWorkflowTask with all scheduling state fields.
         *
         * @param workflowId     the workflow definition ID to launch
         * @param cronExpression the parsed cron expression driving the schedule
         * @param taskId         the time-wheel task ID assigned during registration
         * @param nextRunTime    the pre-computed next fire time
         * @param lastUpdated    the timestamp of the last schedule load or update
         */
        public ScheduledWorkflow(String workflowId, String cronExpression,
                                 String taskId, Date nextRunTime, long lastUpdated) {
            this.workflowId = workflowId;
            this.cronExpression = cronExpression;
            this.taskId = taskId;
            this.nextRunTime = nextRunTime;
            this.lastUpdated = lastUpdated;
        }

        /**
         * Returns the workflow definition ID that this schedule will trigger.
         *
         * @return the workflow definition ID; never {@code null}
         */
        public String getWorkflowId() { return workflowId; }

        /**
         * Returns the raw cron expression string (6-field format) for this schedule.
         *
         * @return the cron expression string; never {@code null}
         */
        public String getCronExpression() {
            return cronExpression;
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
         * Sets the task id.
         *
         * @param taskId  identifier
         */
        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        /**
         * Returns the next run time.
         *
         * @return the requested nextruntime object, or null if not found
         */
        public Date getNextRunTime() {
            return nextRunTime;
        }

        /**
         * Sets the next run time.
         *
         * @param nextRunTime  date/time value
         */
        public void setNextRunTime(Date nextRunTime) {
            this.nextRunTime = nextRunTime;
        }

        /**
         * Returns the last updated.
         *
         * @return the numeric result
         */
        public long getLastUpdated() {
            return lastUpdated;
        }

        /**
         * Sets the last updated.
         *
         * @param lastUpdated  date/time value
         */
        public void setLastUpdated(long lastUpdated) {
            this.lastUpdated = lastUpdated;
        }
    }
}
