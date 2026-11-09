/*
 * Description: DAO interface for task-type registry
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao;

import com.ts.model.entity.TaskType;

import java.util.List;
/**
 * DAO interface for the task-type registry ({@code TS_TASK_TYPE} table).
 */
public interface TaskTypeDao {
    /**
     * Get all task types from the database
     *
     * @return List of TaskType objects
     */
    List<TaskType> getAllTaskTypes();

    /**
     * Get task type by name
     *
     * @param typeName Type name (primary key)
     * @return TaskType object or null if not found
     */
    TaskType getTaskTypeByName(String typeName);

    /**
     * Get task types by group
     *
     * @param typeGroup Type group (JAVA/SCRIPT/SQL)
     * @return List of TaskType objects
     */
    List<TaskType> search(String typeGroup, String typeName);

    /**
     * Insert a new task type
     *
     * @param taskType TaskType object
     * @return Number of rows affected
     */
    int insertTaskType(TaskType taskType);

    /**
     * Update an existing task type
     *
     * @param taskType TaskType object
     * @return Number of rows affected
     */
    int updateTaskType(TaskType taskType);

    /**
     * Delete a task type
     *
     * @param typeName Type name (primary key)
     * @return Number of rows affected
     */
    int deleteTaskType(String typeName);

}