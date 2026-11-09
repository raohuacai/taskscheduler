/*
 * Description: DAO interface for TaskDefinition CRUD
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao;

import com.ts.model.entity.TaskDefinition;

import java.util.List;
import java.util.Map;
/**
 * DAO interface for {@link com.ts.model.entity.TaskDefinition} CRUD operations.
 */
public interface TaskDefinitionDao {
    TaskDefinition getTask(String taskId);

    TaskDefinition getTaskByName(String workflowId, String taskName);

    List<TaskDefinition> getTasksByWorkflowId(String workflowId);

    boolean saveTask(TaskDefinition task);

    boolean updateTask(TaskDefinition task);

    boolean updatePredecessors(TaskDefinition task);

    boolean deleteTask(String taskId);

    boolean deleteTasksByWorkflowId(String workflowId);

}