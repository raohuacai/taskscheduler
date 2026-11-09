/*
 * Description: Contract for workflow and task definition CRUD and DAG management
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service;

import com.github.pagehelper.PageInfo;
import com.ts.core.dag.WorkflowDAG;
import com.ts.model.entity.TaskDefinition;
import com.ts.model.entity.WfDefinition;

import java.util.List;
import java.util.Map;
/**
 * Contract for workflow and task definition management.
 *
 * <p>Covers CRUD operations on {@link com.ts.model.entity.WfDefinition} and
 * {@link com.ts.model.entity.TaskDefinition}, DAG save/retrieve, and paginated
 * search.
 */
public interface DefinitionService {
    // Workflow Definition
    String createWorkflow(WfDefinition workflowDefinition);

    boolean updateWorkflow(WfDefinition workflowDefinition);

    boolean deleteWorkflow(String workflowId);

    WfDefinition getWorkflow(String workflowId);

    Map<String, Object> getWorkflowParamsAsMap(String workflowId);

    PageInfo<WfDefinition> search(String workflowId, String workflowName, int pageNum, int pageSize, String orderBy);

    Map<String, Object> getWorkflowDAG(String workflowId);

    WorkflowDAG buildWorkflowDAG(String workflowId);

    WfDefinition checkWorkflowId(String workflowId);

    WfDefinition checkWorkflowParams(WfDefinition workflowDefinition);

    // Task Definition
    String createTask(String workflowId, TaskDefinition taskDefinition);

    boolean updateTask(TaskDefinition taskDefinition);

    boolean updatePredecessors (TaskDefinition taskDefinition);

    boolean deleteTask(String taskId);

    TaskDefinition getTask(String taskId);

    List<TaskDefinition> getTasksByWorkflowId(String workflowId);

    TaskDefinition checkTaskId(String taskId);

    TaskDefinition checkTaskParams(TaskDefinition taskDefinition);
}
