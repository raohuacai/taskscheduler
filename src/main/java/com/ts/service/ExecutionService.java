/*
 * Description: Contract for workflow and task instance management during execution
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service;

import com.github.pagehelper.PageInfo;
import com.ts.core.engine.WorkflowEngine;
import com.ts.core.engine.WorkflowResult;
import com.ts.model.entity.TaskInstance;
import com.ts.model.entity.WfDefinition;
import com.ts.model.entity.WfInstance;

import java.util.Date;
import java.util.List;
import java.util.Map;
/**
 * Contract for workflow and task instance management during execution.
 *
 * <p>Covers creating and updating {@link com.ts.model.entity.WfInstance} and
 * {@link com.ts.model.entity.TaskInstance} records, and delegating
 * start/cancel/retry/skip operations to the {@link com.ts.core.engine.WorkflowEngine}.
 */
public interface ExecutionService {
    //Injection
    void setWorkflowEngine(WorkflowEngine workflowEngine);

    // Existing methods
    WfInstance getWfInstance(String wfInstanceId);

    PageInfo<WfInstance> search(String workflowId, String workflowName, String wfInstanceId, String parentWfInstanceId,
                                String topParentInstanceId, String status,
                                Date startDateFrom, Date startDateTo, int pageNum, int pageSize, String orderBy);

    WfInstance createWfInstance(String workflowId, String wfInstanceId, String pWfInstanceId, String topParentInstanceId);

    WfInstance updateStatus(String wfInstanceId, String status, String message);

    TaskInstance getTaskInstance(String taskInstanceId);

    List<TaskInstance> getTaskInstancesForWorkflow(String wfInstanceId);

    TaskInstance createTaskInstance(String workflowId, String wfInstanceId, String taskId, int index);

    WorkflowResult startWorkflow(String workflowId, Map<String, Object> parameters);

    boolean recoverWorkflow(String wfInstanceId);

    boolean cancelWorkflow(String wfInstanceId);

    boolean retryTask(String taskInstanceId);

    boolean skipTask(String taskInstanceId);

    boolean cancelTask(String taskInstanceId);

    // New transaction management methods
    boolean beginTransaction();

    void commitTransaction();

    void rollbackTransaction();

    boolean isTransactionActive();

    // New transactional operations
    TaskInstance updateTaskStatusTransactional(String taskInstanceId, String status, String message);

    WfInstance updateStatusTransactional(String wfInstanceId, String status, String message);
}