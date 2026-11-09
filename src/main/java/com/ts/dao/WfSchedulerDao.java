/*
 * Description: DAO interface for WfScheduler CRUD
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao;

import com.ts.model.entity.WfScheduler;

import java.util.List;
/**
 * DAO interface for {@link com.ts.model.entity.WfScheduler} CRUD operations.
 */
public interface WfSchedulerDao {
    /**
     * Get scheduler by workflow ID
     *
     * @param workflowId Workflow ID
     * @return Scheduler or null if not found
     */
    WfScheduler getSchedule(String workflowId);

    /**
     * Get all scheduled workflows
     *
     * @return List of scheduled workflows
     */
    List<WfScheduler> getAllActiveSchedules();

    List<WfScheduler> getAllSchedules(String workflowId, String workflowName);

    /**
     * Save scheduler
     *
     * @param schedule Scheduler to save
     * @return true if successful
     */
    boolean createSchedule(WfScheduler schedule);

    /**
     * Update scheduler
     *
     * @param schedule Scheduler to update
     * @return true if successful
     */
    boolean updateSchedule(WfScheduler schedule);

    /**
     * Delete scheduler
     *
     * @param workflowId Workflow ID
     * @return true if successful
     */
    boolean deleteSchedule(String workflowId);

    /**
     * Get IDs of all active workflows
     *
     * @return List of active workflow IDs
     */
    List<String> getAllActiveWorkflowIds();

}