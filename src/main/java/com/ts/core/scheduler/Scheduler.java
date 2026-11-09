/*
 * Description: Contract for cron-based workflow schedulers
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.core.scheduler;
/**
 * Contract for cron-based workflow schedulers.
 *
 * <p>Implementations are responsible for loading schedule definitions from the
 * database, registering them with a time-wheel, and reacting to schedule changes
 * (add, update, delete) at runtime without requiring a restart.
 */
public interface Scheduler {
    /**
     * Initialize the scheduler
     */
    void initialize();

    /**
     * Schedule a workflow with a cron expression
     *
     * @param workflowId     Workflow ID
     * @param cronExpression Cron expression
     * @return true if scheduling was successful, false otherwise
     */
    boolean schedule(String workflowId, String cronExpression);

    /**
     * UnSchedule a ts
     *
     * @param workflowId Workflow ID
     * @return true if unScheduling was successful, false otherwise
     */
    boolean unSchedule(String workflowId);

    /**
     * Update an existing schedule
     *
     * @param workflowId     Workflow ID
     * @param cronExpression New cron expression
     * @return true if update was successful, false otherwise
     */
    boolean updateSchedule(String workflowId, String cronExpression);

    /**
     * Refresh all schedules from the database
     */
    void refreshAllSchedules();

    /**
     * Shutdown the scheduler
     */
    void shutdown();
}