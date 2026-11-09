/*
 * Description: DAO interface for task execution log persistence
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao;

import com.ts.model.entity.TaskLog;

import java.util.List;
/**
 * DAO interface for appending and reading task execution log entries.
 */
public interface TaskLogDao {
    /**
     * Create a new task log
     *
     * @param taskLog TaskLog object to create
     * @return true if successful, false otherwise
     */
    boolean createTaskLog(TaskLog taskLog);

    /**
     * Get task logs by task instance ID
     *
     * @param taskInstanceId Task instance ID
     * @return List of task logs for the specified task instance
     */
    List<TaskLog> getTaskLogsByTaskInstanceId(String taskInstanceId);
}