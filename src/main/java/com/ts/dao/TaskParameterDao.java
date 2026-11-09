/*
 * Description: DAO interface for task-level parameter definitions
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao;

import com.ts.model.entity.TaskParameter;

import java.util.List;
import java.util.Set;
/**
 * DAO interface for task-level parameter definitions.
 */
public interface TaskParameterDao {
    /**
     * Get task instance parameters by task instance ID
     *
     * @param taskInstanceId task instance ID
     * @return TaskParams object or null if not found
     */
    TaskParameter getTaskParams(String taskInstanceId);

    /**
     * Get task instance parameters by primary key
     *
     * @param TaskParamId task instance parameter ID
     * @return TaskParams object or null if not found
     */
    TaskParameter getTaskParamById(String TaskParamId);

    /**
     * Get task instance parameters by workflow instance ID
     *
     * @param wfInstanceId workflow instance ID
     * @return List of TaskParams objects or empty list if not found
     */
    List<TaskParameter> getTaskParamsByWfInstanceId(String wfInstanceId);

    /**
     * Create new task instance parameters
     *
     * @param taskParameter parameters to create
     * @return created parameter ID or null if failed
     */
    String createTaskParams(TaskParameter taskParameter);

    /**
     * Get task parameters by workflow instance ID and a set of task IDs.
     * More efficient than loading all tasks when only a few upstream outputs are needed.
     *
     * @param wfInstanceId workflow instance ID
     * @param taskIds      set of task definition IDs to fetch
     * @return list of matching TaskParameter records, never null
     */
    List<TaskParameter> getTaskParamsByWfInstanceIdAndTaskIds(String wfInstanceId, List<String> taskIds);

    /**
     * Update pvalue by task instance ID
     *
     * @param taskInstanceId task instance ID
     * @param pvalue         new parameter value
     * @return true if updated successfully, false otherwise
     */
    boolean updatePvalueByInstanceId(String taskInstanceId, String pvalue);
}
