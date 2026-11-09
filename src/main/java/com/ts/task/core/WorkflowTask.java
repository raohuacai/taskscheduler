/*
 * Description: Task type that launches a child (sub-)workflow and awaits its completion via callback
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.task.core;

import com.ts.common.constants.Constants;
import com.ts.common.utils.ParamUtils;
import com.ts.core.engine.WorkflowEngine;
import com.ts.exception.TaskExecutionException;
import com.ts.exception.WorkflowNotFoundException;
import com.ts.model.entity.WfInstance;
import com.ts.service.impl.TaskLogService;
import com.ts.task.AbstractTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
/**
 * Task type that starts a child (sub-) workflow and waits for it to complete.
 *
 * <p>In {@link #action}, calls {@link com.ts.core.engine.WorkflowEngine#startSubWorkflow}
 * to launch the child workflow, then stores the child instance ID in the task
 * parameter map and returns immediately. The parent {@link com.ts.core.engine.WorkflowInstance}
 * keeps this task in RUNNING state until the child workflow calls back via
 * {@link com.ts.core.engine.WorkflowInstance#onSubWorkflowComplete} or
 * {@link com.ts.core.engine.WorkflowInstance#onSubWorkflowFailed}.
 *
 * <p>Timeout is intentionally not applied to this task type — the child
 * workflow manages its own execution duration. Email notifications for the child
 * workflow are suppressed (flag forced to {@code N}) to avoid duplicate alerts.
 *
 * <p>Parameter inheritance: all parent workflow parameters are copied to the child,
 * plus any parameters prefixed with {@code sub.} (prefix stripped) which allow the
 * caller to pass child-specific overrides.
 *
 * @see com.ts.core.engine.WorkflowEngine
 * @see com.ts.core.engine.WorkflowInstance
 */
public class WorkflowTask extends AbstractTask {
    private static final Logger logger = LogManager.getLogger(WorkflowTask.class);

    private volatile String subWorkflowId;
    private Map<String, Object> subWorkflowParams;
    private volatile String subWfInstanceId;

    /**
     * Constructs a new instance, wiring together all required dependencies.
     *
     * @param workflowEngine the workflow engine used to start the sub-workflow
     */
    public WorkflowTask(WorkflowEngine workflowEngine) {
        this.workflowEngine = workflowEngine;  // assigned to AbstractTask field
    }

    @Override
    /**
      * Prepares resources and reads task parameters before the main action.
      * Called by {@link AbstractTask#execute} before {@link #action}.
      *
      * @param parameters  merged task parameter map supplied by the workflow engine
      * @throws Exception  if a required parameter is missing or resource initialisation fails
      */
    public void preAction(Map<String, Object> parameters) throws Exception {
        super.preAction(parameters);
        // Retrieve sub-workflow ID
        subWorkflowId = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TASK_SUBWORKFLOWID, taskId, taskInstanceId);
        // Create the parameter map for the sub-workflow invocation
        subWorkflowParams = collectSubWorkflowParams(parameters);
        // Force the sub-workflow's notification email flag to N (suppress redundant notifications)
        subWorkflowParams.put(Constants.PARAM_NOTICE_EMAIL, Constants.FLAG_NO);
        //subWorkflowParams.put(Constants.PARAM_ERROR_EMAIL, Constants.FLAG_NO);
        subWorkflowParams.put(Constants.PARAM_NOTICE_WEBHOOK, Constants.FLAG_NO);
        //subWorkflowParams.put(Constants.PARAM_ERROR_WEBHOOK, Constants.FLAG_NO);
    }

    @Override
    /**
      * Executes the core task logic.
      * Called by {@link AbstractTask#execute} after {@link #preAction}.
      *
      * @param parameters  merged task parameter map
      * @throws Exception  if the task logic fails
      */
    public void action(Map<String, Object> parameters) throws Exception {
        try {
            // Startsub-workflow
            logger.info("Starting SubWorkflow: {} ; Parent Task: {}, {}, {}", subWorkflowId, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Starting SubWorkflow: " + subWorkflowId);
            subWfInstanceId = workflowEngine.start(subWorkflowId, subWorkflowParams);

            if (subWfInstanceId == null) {
                throw new TaskExecutionException("Failed to start SubWorkflow", null, taskId, taskInstanceId);
            }

            // Persist subWfInstanceId to task parameters for callback and recovery (in-memory + DB write)
            saveParam(parameters, Constants.PARAM_TASK_SUBWORKFLOW_INSTANCE, subWfInstanceId);

            logger.info("SubWorkflow {} started, task {} will be suspended by engine", subWfInstanceId, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "SubWorkflow started: " + subWfInstanceId + ", task suspending");

        } catch (WorkflowNotFoundException e) {
            logger.error("SubWorkflow not found: {}", subWorkflowId);
            throw new TaskExecutionException("SubWorkflow not found: " + subWorkflowId, e, taskId, taskInstanceId);
        } catch (TaskExecutionException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error in SubWorkflow execution: {} (instance: {})", subWorkflowId, subWfInstanceId);
            throw new TaskExecutionException(e.getMessage() + " : " + subWorkflowId, e, taskId, taskInstanceId);
        }
    }

    @Override
    /**
     * Cleanup the resources.
     */
    public void cleanupResources() {
        // cancel trigger clean sub ts
        if (canceled.get()) {
            cleanupSubWorkflow();
        }
        super.cleanupResources();
    }

    /**
     * Collects the parameter map to be passed to the sub-workflow
     */
    private Map<String, Object> collectSubWorkflowParams(Map<String, Object> parameters) {
        Map<String, Object> subWorkflowParams = new HashMap<>();
        // Copy parent workflow parameters into the sub-workflow parameter map
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getKey().startsWith("sub.")) {
                String paramName = entry.getKey().substring(4); // Strip the "sub." prefix
                subWorkflowParams.put(paramName, entry.getValue());
            }
        }
        // Add parent workflow context parameters
        subWorkflowParams.put(Constants.PARAM_PARENT + "." + Constants.PARAM_TASK_TIMEOUT, parameters.get(Constants.PARAM_TASK_TIMEOUT));
        subWorkflowParams.put(Constants.PARAM_PARENT + "." + Constants.PARAM_WORKFLOW_ID, parameters.get(Constants.PARAM_WORKFLOW_ID));
        subWorkflowParams.put(Constants.PARAM_PARENT + "." + Constants.PARAM_WORKFLOW_NAME, parameters.get(Constants.PARAM_WORKFLOW_NAME));
        subWorkflowParams.put(Constants.PARAM_PARENT + "." + Constants.PARAM_WORKFLOW_INSTANCE, parameters.get(Constants.PARAM_WORKFLOW_INSTANCE));
        subWorkflowParams.put(Constants.PARAM_PARENT + "." + Constants.PARAM_TASK_ID, parameters.get(Constants.PARAM_TASK_ID));
        subWorkflowParams.put(Constants.PARAM_PARENT + "." + Constants.PARAM_TASK_NAME, parameters.get(Constants.PARAM_TASK_NAME));
        subWorkflowParams.put(Constants.PARAM_PARENT + "." + Constants.PARAM_TASK_INSTANCE, parameters.get(Constants.PARAM_TASK_INSTANCE));
        // add triggeredBy
        subWorkflowParams.put(Constants.PARAM_WORKFLOW_TRIGGERBY, parameters.get(Constants.PARAM_WORKFLOW_TRIGGERBY));
        // add triggeredDate
        subWorkflowParams.put(Constants.PARAM_WORKFLOW_TRIGGERDATE, parameters.get(Constants.PARAM_WORKFLOW_TRIGGERDATE));
        // add top core instance id
        subWorkflowParams.put(Constants.PARAM_TOP_PARENT_WORKFLOW_INSTANCE, parameters.get(Constants.PARAM_TOP_PARENT_WORKFLOW_INSTANCE));
        return subWorkflowParams;
    }

    /**
     * Cancels or cleans up the associated sub-workflow
     */
    private void cleanupSubWorkflow() {
        if (subWfInstanceId != null) {
            try {
                // Clear any lingering interrupt flag before DB operations —
                // JDBC drivers may fail if the thread is in interrupted state.
                Thread.interrupted();
                // Retrieve the current sub-workflow status
                WfInstance subWfInstance = workflowEngine.getWfInstanceStatus(subWfInstanceId);
                // If the sub-workflow is still running or waiting, cancel it
                if (subWfInstance != null &&
                        (Constants.STATUS_RUNNING.equals(subWfInstance.getStatus()) ||
                                Constants.STATUS_WAITING.equals(subWfInstance.getStatus()))) {
                    workflowEngine.cancel(subWfInstanceId);
                    logger.info("Canceled SubWorkflow: {}, Parent Task: {}, {}, {}", subWorkflowId, taskId, taskName, taskInstanceId);
                }
            } catch (Exception e) {
                logger.error("Error cleaning up SubWorkflow: {}, Parent Task: {}, {}, {}", subWfInstanceId, taskId, taskName, taskInstanceId, e);
            }
        }
    }
}
