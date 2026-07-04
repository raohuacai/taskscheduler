/*
 * Description: Central orchestrator for workflow lifecycle: start, recover, cancel, and monitor workflow instances
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.core.engine;

import com.ts.Main;
import com.ts.common.utils.ParamUtils;
import com.ts.common.SeqId.SeqIdGenerator;
import com.ts.common.constants.Constants;
import com.ts.common.utils.JsonUtils;
import com.ts.core.dag.WorkflowDAG;
import com.ts.monitor.WorkflowMonitor;
import com.ts.exception.CycleDetectedException;
import com.ts.exception.ResourceLimitException;
import com.ts.exception.WorkflowException;
import com.ts.exception.WorkflowNotFoundException;
import com.ts.model.entity.*;
import com.ts.msg.NotificationFactory;
import com.ts.service.DefinitionService;
import com.ts.service.ExecutionService;
import com.ts.service.ParamsService;
import com.ts.task.TaskFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Central orchestrator for workflow lifecycle management.
 *
 * <p>Responsible for starting, recovering, cancelling, and monitoring individual
 * workflow runs. Each active run is represented by a {@link WorkflowInstance}
 * held in {@code runningWorkflows}. The engine enforces a global concurrency cap
 * ({@code MAX_RUNNING_WORKFLOWS}) and delegates task execution to a shared
 * {@link java.util.concurrent.ThreadPoolExecutor} managed by {@link ThreadPoolManager}.
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>Per-workflow mutex objects ({@code wfInstanceLocks}) prevent concurrent
 *       modifications to the same instance (e.g., simultaneous cancel + task-completion
 *       callbacks).</li>
 *   <li>A {@link java.util.concurrent.locks.ReadWriteLock} ({@code statsLock}) guards
 *       aggregate statistics that the monitor reads concurrently.</li>
 *   <li>Sub-workflow completion callbacks ({@code onSubWorkflowComplete/Failed}) are
 *       routed back through the parent {@link WorkflowInstance} using the stored
 *       {@code parentWfInstanceId} relationship.</li>
 * </ul>
 *
 * @see WorkflowInstance
 * @see ThreadPoolManager
 * @see com.ts.core.scheduler.CronScheduler
 */
public class WorkflowEngine {
    private static final Logger logger = LogManager.getLogger(WorkflowEngine.class);
    private static final int MAX_RUNNING_WORKFLOWS = 100;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 60;

    // Services
    private final DefinitionService definitionService;
    private final ExecutionService executionService;
    private final ParamsService paramsService;
    private final TaskFactory taskFactory;
    private final NotificationManager notificationManager;
    private final ThreadPoolManager threadPoolManager;
    private WorkflowMonitor monitor;

    // Execution resources
    private ThreadPoolExecutor executorService;

    // State management
    private final ConcurrentMap<String, WorkflowInstance> runningWorkflows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> wfInstanceLocks = new ConcurrentHashMap<>();
    private final ReadWriteLock statsLock = new ReentrantReadWriteLock();

    /**
     * Constructor with required dependencies
     */
    public WorkflowEngine(DefinitionService definitionService, ExecutionService executionService, ParamsService paramsService, TaskFactory taskFactory, NotificationFactory notificationFactory) {
        this.definitionService = definitionService;
        this.executionService = executionService;
        this.paramsService = paramsService;
        this.taskFactory = taskFactory;
        this.threadPoolManager = new ThreadPoolManager(paramsService);
        this.notificationManager = new NotificationManager(definitionService, executionService, paramsService, notificationFactory);

        executionService.setWorkflowEngine(this);
    }

    /**
     * Initialize the workflow engine
     */
    public void initialize() {
        executorService = threadPoolManager.createExecutorService();
        logger.info("Workflow engine initialized with thread pool manager");
    }

    /**
     * Shutdown the workflow engine
     */
    public void shutdown() {
        logger.info("Shutting down workflow engine");

        //cancelAllRunningWorkflows();
        cleanupResources();

        logger.info("Workflow engine shutdown complete");
    }

    //-------------------------------------------------------------------------------------
    // Workflow Lifecycle Methods
    //-------------------------------------------------------------------------------------

    /**
     * Start a new workflow
     *
     * @param workflowId The workflow definition ID
     * @param parameters Input parameters for the workflow
     * @return The workflow instance ID or null if start failed
     * @throws ResourceLimitException if maximum workflow limit is reached
     */
    public String start(String workflowId, Map<String, Object> parameters) {

        logger.info("Starting workflow: {}", workflowId);
        checkWorkflowLimit();
        if (parameters == null) {
            parameters = new HashMap<>();
        }
        String wfInstanceId = SeqIdGenerator.generateWfInstId();
        return startWorkflowInternal(workflowId, wfInstanceId, parameters);
    }

    /**
     * Recover an existing workflow
     *
     * @param wfInstanceId The workflow instance ID to recover
     * @return True if recovery successful, false otherwise
     */
    public boolean recover(String wfInstanceId) {
        logger.info("Recovering workflow instance: {}", wfInstanceId);

        if (runningWorkflows.containsKey(wfInstanceId)) {
            logger.warn("Workflow {} is already running in memory", wfInstanceId);
            return true;
        }

        return recoverWorkflowInternal(wfInstanceId);
    }

    /**
     * Cancel an existing workflow
     *
     * @param wfInstanceId The workflow instance ID to cancel
     * @return True if cancellation successful, false otherwise
     */
    public boolean cancel(String wfInstanceId) {
        logger.info("Cancelling workflow instance: {}", wfInstanceId);
        return cancelWorkflowInternal(wfInstanceId);
    }

    /**
     * Complete a workflow execution
     *
     * @param wfInstanceId The workflow instance ID to complete
     */
    public boolean completeWorkflow(String wfInstanceId) {
        logger.info("Completing workflow: {}", wfInstanceId);
        return completeWorkflowInternal(wfInstanceId);
    }

    //-------------------------------------------------------------------------------------
    // Task Management Methods
    //-------------------------------------------------------------------------------------

    /**
     * Retry a failed task
     *
     * @param taskInstanceId The task instance ID to retry
     * @return True if retry successful, false otherwise
     */
    public boolean retryTask(String taskInstanceId) {
        logger.info("Retrying task instance: {}", taskInstanceId);
        return executeTaskOperation(taskInstanceId, "retry");
    }

    /**
     * Skip a task
     *
     * @param taskInstanceId The task instance ID to skip
     * @return True if skip successful, false otherwise
     */
    public boolean skipTask(String taskInstanceId) {
        logger.info("Skipping task instance: {}", taskInstanceId);
        return executeTaskOperation(taskInstanceId, "skip");
    }

    /**
     * Cancel a running task
     *
     * @param taskInstanceId The task instance ID to cancel
     * @return True if cancellation successful, false otherwise
     */
    public boolean cancelTask(String taskInstanceId) {
        logger.info("Cancelling task instance: {}", taskInstanceId);
        return executeTaskOperation(taskInstanceId, "cancel");
    }

    //-------------------------------------------------------------------------------------
    // Query Methods
    //-------------------------------------------------------------------------------------

    /**
     * Get workflow instance status for subWorkflow tasks
     *
     * @param subWfInstanceId The workflow instance ID
     * @return The workflow instance or null if not found
     */
    public WfInstance getWfInstanceStatus(String subWfInstanceId) {
        return executionService.getWfInstance(subWfInstanceId);
    }

    /**
     * Checks whether the running workflow.
     *
     * @param workflowId workflowId identifier
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean isRunningWorkflow(String workflowId) {
        statsLock.readLock().lock();
        try {
            // Check if any of the running workflow instances have the given workflowId
            return runningWorkflows.values().stream().anyMatch(instance -> {
                WorkflowInstance workflowInstance = (WorkflowInstance) instance;
                return workflowInstance.getWorkflowId().equals(workflowId);
            });

        } finally {
            statsLock.readLock().unlock();
        }
    }

    /**
     * Get statistics for all currently running workflows
     * Used by WorkflowMonitor for generating reports
     *
     * @return Map of workflow statistics
     */
    public Map<String, Object> getRunningWorkflowsStats() {
        statsLock.readLock().lock();
        try {
            Map<String, Object> result = new HashMap<>();
            Map<String, WorkflowInstance> snapshot = new HashMap<>(runningWorkflows);

            for (Map.Entry<String, WorkflowInstance> entry : snapshot.entrySet()) {
                String wfInstanceId = entry.getKey();
                WorkflowInstance instance = entry.getValue();

                Map<String, Object> workflowStats = new HashMap<>();
                workflowStats.put("workflowId", instance.getWorkflowId());
                workflowStats.put("totalTasks", instance.getAllTaskInstanceIds().size());
                workflowStats.put("runningTasks", instance.getRunningTasks().size() + instance.getRunningWorkflowTaskIds().size());
                //workflowStats.put("runningWorkflowTasks", instance.getRunningWorkflowTaskIds().size());
                workflowStats.put("completedTasks", instance.getCompletedTaskInstanceIds().size());
                workflowStats.put("errorTasks", instance.getErrorTaskInstanceIds().size());

                result.put(wfInstanceId, workflowStats);
            }

            return result;
        } finally {
            statsLock.readLock().unlock();
        }
    }

    /**
     * Returns the executor service.
     *
     * @return the requested executorservice object, or null if not found
     */
    public ThreadPoolExecutor getExecutorService() {
        return executorService;
    }

    /**
     * Sets the monitor.
     *
     * @param monitor monitor value
     */
    public void setMonitor(WorkflowMonitor monitor) {
        this.monitor = monitor;
    }

    // Add sub workflow id to task parameter

    /**
     * Updates the task params.
     *
     * @param taskInstanceId instance identifier
     * @param updates        date/time value
     */
    public void updateTaskParams(String taskInstanceId, Map<String, Object> updates) {
        TaskParameter taskParameter = paramsService.getTaskParamsByInstanceId(taskInstanceId);
        Map<String, Object> params = JsonUtils.jsonToMap(taskParameter.getPvalue());
        params.putAll(updates);
        paramsService.saveTaskParameters(taskInstanceId, params);
    }

    //-------------------------------------------------------------------------------------
    // Internal Implementation Methods
    //-------------------------------------------------------------------------------------

    /**
     * Internal method to start a workflow with transaction handling
     */
    protected String startWorkflowInternal(String workflowId, String wfInstanceId, Map<String, Object> parameters) {
        boolean startedTransaction = false;  // monitor start recorded
        boolean startRecorded = false;
        Object lock = wfInstanceLocks.computeIfAbsent(workflowId, k -> new Object());
        synchronized (lock) {
            try {
                // check is it running workflow
                if (isRunningWorkflow(workflowId)) {
                    logger.warn("Workflow engine rejected: Workflow {} has an active instance already running", workflowId);
                    throw new WorkflowException(String.format("Cannot start workflow: An active instance of workflow '%s' is currently running.", workflowId));
                }

                monitor.recordWorkflowStart(workflowId, wfInstanceId);

                startRecorded = true;
                // Check workflow definition and create instance
                WfDefinition workflow = definitionService.checkWorkflowId(workflowId);
                // try to get parent workflow instance id and save it
                String pWfInstanceId = parameters.getOrDefault(Constants.PARAM_PARENT + "." + Constants.PARAM_WORKFLOW_INSTANCE, wfInstanceId).toString();
                // Determine topParentInstanceId:
                // - If this is the top-level workflow (no parent) or the passed-in topParentInstanceId is empty,
                //   use the current wfInstanceId as topParentInstanceId.
                // - Otherwise inherit the topParentInstanceId passed in from the parent workflow.
                String incomingTopParentInstanceId = parameters.getOrDefault(Constants.PARAM_TOP_PARENT_WORKFLOW_INSTANCE, "").toString();
                String topParentInstanceId = (incomingTopParentInstanceId == null || incomingTopParentInstanceId.isEmpty()) ? wfInstanceId : incomingTopParentInstanceId;
                startedTransaction = executionService.beginTransaction();
                WfInstance wfInstance = executionService.createWfInstance(workflowId, wfInstanceId, pWfInstanceId, topParentInstanceId);
                // Propagate topParentInstanceId so sub-workflows can inherit it
                parameters.put(Constants.PARAM_TOP_PARENT_WORKFLOW_INSTANCE, topParentInstanceId);

                // Handle disabled workflows
                if (!Constants.FLAG_YES.equals(workflow.getEnableFlag())) {
                    logger.info("Workflow '{}' is disabled, skipping: {}", workflow.getWorkflowName(), workflowId);
                    executionService.updateStatusTransactional(wfInstanceId, Constants.STATUS_SKIPPED, "");
                    if (startedTransaction) executionService.commitTransaction();
                    monitor.recordWorkflowCompletion(wfInstanceId, false);
                    return wfInstanceId;
                } else {
                    logger.info("Workflow Id: {}, Workflow Name: {}, Instance Id: {}", workflowId, workflow.getWorkflowName(), wfInstanceId);
                }

                // Initialize workflow
                Map<String, Object> mergedParams = initInstanceAndParams(workflowId, wfInstanceId, parameters);
                WorkflowDAG dag = paramsService.buildWorkflowDAG(wfInstanceId);

                // Create workflow execution instance
                WorkflowInstance instance = createWorkflowInstance(wfInstance, mergedParams, dag);

                // Update workflow status to running
                executionService.updateStatusTransactional(wfInstanceId, Constants.STATUS_RUNNING, "");

                if (startedTransaction) {
                    executionService.commitTransaction();
                }
                // Update memory state and start workflow execution
                runningWorkflows.put(wfInstance.getWfInstanceId(), instance);
                instance.start();
                notificationManager.sendWfNotification(wfInstanceId, Constants.TYPE_WORKFLOW_START);
                logger.info("Workflow started: {}, {}, {}", workflowId, workflow.getWorkflowName(), wfInstanceId);
                return wfInstanceId;
            } catch (CycleDetectedException e) {
                rollbackAndLogError(startedTransaction, "Cycle detected in workflow DAG: {}", workflowId, e);
                executionService.updateStatus(wfInstanceId, Constants.STATUS_ERROR, "Cycle detected in workflow DAG: " + e.getMessage());
                cleanupFailedWorkflow(wfInstanceId, startRecorded);
                throw e;
            } catch (WorkflowNotFoundException e) {
                rollbackAndLogError(startedTransaction, "Workflow not found: {}", workflowId, e);
                executionService.updateStatus(wfInstanceId, Constants.STATUS_ERROR, "Workflow not found: " + e.getMessage());
                cleanupFailedWorkflow(wfInstanceId, startRecorded);
                throw e;
            } catch (Exception e) {
                rollbackAndLogError(startedTransaction, "Error starting workflow: {}", workflowId, e);
                executionService.updateStatus(wfInstanceId, Constants.STATUS_ERROR, "Error during workflow start");
                cleanupFailedWorkflow(wfInstanceId, startRecorded);
                throw e;
            } finally {
                wfInstanceLocks.remove(workflowId);
            }
        }
    }

    /**
     * Clean up after a failed workflow start
     */
    private void cleanupFailedWorkflow(String wfInstanceId, boolean startRecorded) {
        runningWorkflows.remove(wfInstanceId);
        if (startRecorded) {
            monitor.recordWorkflowCompletion(wfInstanceId, false);
        }
        notificationManager.sendWfNotification(wfInstanceId, Constants.TYPE_WORKFLOW_ERROR);
    }

    /**
     * Internal method to recover a workflow with transaction handling
     */
    protected boolean recoverWorkflowInternal(String wfInstanceId) {
        boolean startedTransaction = false;
        Object lock = wfInstanceLocks.computeIfAbsent(wfInstanceId, k -> new Object());
        synchronized (lock) {
            try {
                //startedTransaction = executionService.beginTransaction();

                WorkflowInstance instance = getOrCreateWorkflowInstance(wfInstanceId);
                if (instance == null) {
                    //rollbackTransaction(startedTransaction);
                    logger.error("Failed to create workflow instance for recovery: {}", wfInstanceId);
                    runningWorkflows.remove(wfInstanceId);
                    return false;
                }

                //if (startedTransaction) {
                //    executionService.commitTransaction();
                //}

                monitor.recordWorkflowStart(instance.getWorkflowId(), wfInstanceId);
                instance.resume();
                return true;
            } catch (Exception e) {
                //rollbackAndLogError(startedTransaction, "Error recovering workflow: {}", wfInstanceId, e);
                logger.error("Error recovering workflow: {}", wfInstanceId, e);
                runningWorkflows.remove(wfInstanceId);
                return false;
            } finally {
                wfInstanceLocks.remove(wfInstanceId);
            }
        }
    }


    /**
     * Internal method to cancel a workflow with transaction handling
     */
    protected boolean cancelWorkflowInternal(String wfInstanceId) {
        boolean startedTransaction = false;
        WorkflowInstance instance = null;

        Object lock = wfInstanceLocks.computeIfAbsent(wfInstanceId, k -> new Object());
        synchronized (lock) {
            try {
                startedTransaction = executionService.beginTransaction();

                // Check workflow status
                WfInstance wfInstance = executionService.getWfInstance(wfInstanceId);
                if (wfInstance == null) {
                    rollbackTransaction(startedTransaction);
                    logger.warn("Cannot find workflow {} in database", wfInstanceId);
                    return false;
                }

                String wfStatus = wfInstance.getStatus();
                if (!Constants.STATUS_WAITING.equals(wfStatus) && !Constants.STATUS_RUNNING.equals(wfStatus)) {
                    rollbackTransaction(startedTransaction);
                    logger.warn("Cannot cancel workflow instance {} with status: {}", wfInstanceId, wfStatus);
                    return false;
                }

                // Get workflow instance
                instance = runningWorkflows.get(wfInstanceId);
                if (instance == null) {
                    // Handle restart server case (missing instance)
                    executionService.updateStatusTransactional(wfInstanceId, Constants.STATUS_CANCELED, "");

                    if (startedTransaction) {
                        executionService.commitTransaction();
                    }

                    //if (monitor != null) {
                    // monitor.recordWorkflowCompletion(wfInstanceId, false);
                    //}
                    // Notify the parent task that the sub-workflow has been cancelled
                    notifyParentTaskOnSubWorkflowFailure(wfInstanceId, "SubWorkflow canceled");
                    notificationManager.sendWfNotification(wfInstanceId, Constants.TYPE_WORKFLOW_CANCEL);

                    logger.warn("Cannot find workflow instance: {} - updated status in database", wfInstanceId);
                    return true;
                } else {
                    // Normal case - cancel running instance
                    boolean result = instance.cancel();
                    if (result) {
                        executionService.updateStatusTransactional(wfInstanceId, Constants.STATUS_CANCELED, "Workflow cancelled by user");

                        if (startedTransaction) {
                            executionService.commitTransaction();
                        }

                        runningWorkflows.remove(wfInstanceId);
                        //if (monitor != null) {
                        monitor.recordWorkflowCompletion(wfInstanceId, false);
                        //}
                        // Notify the parent task that the sub-workflow has been cancelled
                        notifyParentTaskOnSubWorkflowFailure(wfInstanceId, "SubWorkflow canceled");

                        notificationManager.sendWfNotification(wfInstanceId, Constants.TYPE_WORKFLOW_CANCEL);
                        logger.info("Workflow instance cancelled: {}", wfInstanceId);
                    } else {
                        rollbackTransaction(startedTransaction);
                        logger.warn("Failed to cancel workflow instance: {}", wfInstanceId);
                    }
                    return result;
                }
            } catch (Exception e) {
                rollbackAndLogError(startedTransaction, "Failed to cancel workflow instance: {}", wfInstanceId, e);
                return false;
            } finally {
                wfInstanceLocks.remove(wfInstanceId);
                try {
                    if (instance != null && instance.getParameters() != null) {
                        Main.getLockService().releaseLock(ParamUtils.getLockName(instance.getParameters()));
                        Main.getLockService().releaseAllLocksByOwner(ParamUtils.getLockOwner(instance.getParameters()));
                    }
                } catch (Exception e) {
                    logger.error("Failed to release lock: {}", wfInstanceId);
                }
            }
        }
    }

    /**
     * Internal method to complete a workflow with transaction handling
     */
    protected boolean completeWorkflowInternal(String wfInstanceId) {
        boolean startedTransaction = false;
        Object lock = wfInstanceLocks.computeIfAbsent(wfInstanceId, k -> new Object());
        synchronized (lock) {
            try {
                startedTransaction = executionService.beginTransaction();

                WfInstance wfInstance = executionService.getWfInstance(wfInstanceId);
                if (wfInstance == null) {
                    rollbackTransaction(startedTransaction);
                    logger.warn("Cannot complete workflow {}, instance not found in database", wfInstanceId);
                    return false;
                }
                String wfStatus = wfInstance.getStatus();

                if (!Constants.STATUS_RUNNING.equals(wfStatus)) {
                    rollbackTransaction(startedTransaction);
                    logger.debug("Cannot complete workflow {} with status: {}", wfInstanceId, wfStatus);
                    return false;
                }

                executionService.updateStatusTransactional(wfInstanceId, Constants.STATUS_COMPLETED, "");

                if (startedTransaction) {
                    executionService.commitTransaction();
                }

                runningWorkflows.remove(wfInstanceId);
                //if (monitor != null) {
                monitor.recordWorkflowCompletion(wfInstanceId, true);
                //}

                // Callback to the parent task (sub-workflow completion notification)
                notifyParentTaskOnSubWorkflowComplete(wfInstanceId);

                notificationManager.sendWfNotification(wfInstanceId, Constants.TYPE_WORKFLOW_END);
                logger.info("Workflow completed successfully: {}", wfInstanceId);
                return true;
            } catch (Exception e) {
                rollbackAndLogError(startedTransaction, "Failed to complete workflow: {}", wfInstanceId, e);
                return false;
            } finally {
                wfInstanceLocks.remove(wfInstanceId);
            }
        }
    }

    /**
     * Callback to the parent task when a sub-workflow completes normally.
     * Prioritizes tasks in RUNNING state that have already triggered a sub-workflow; compatible with legacy ERROR/RUNNING states.
     */
    private void notifyParentTaskOnSubWorkflowComplete(String subWfInstanceId) {
        try {
            WfParameter wfParameter = paramsService.getWfParamsByInstanceId(subWfInstanceId);
            Map<String, Object> wfParams = JsonUtils.jsonToMap(wfParameter.getPvalue());
            String parentTaskInsId = ParamUtils.getParameter(wfParams, Constants.PARAM_PARENT + "." + Constants.PARAM_TASK_INSTANCE, "");
            String parentWfInsId = ParamUtils.getParameter(wfParams, Constants.PARAM_PARENT + "." + Constants.PARAM_WORKFLOW_INSTANCE, "");

            if (parentTaskInsId.isEmpty() || parentWfInsId.isEmpty()) {
                // Top-level workflow; no parent callback needed
                return;
            }

            TaskInstance taskInstance = executionService.getTaskInstance(parentTaskInsId);
            String taskStatus = taskInstance.getStatus();

            if (!Constants.STATUS_RUNNING.equals(taskStatus)) {
                logger.debug("Parent task {} is in status {}, no callback needed", parentTaskInsId, taskStatus);
                return;
            }

            logger.info("SubWorkflow {} completed, notifying parent task {}", subWfInstanceId, parentTaskInsId);

            WorkflowInstance parentWorkflowInstance = runningWorkflows.get(parentWfInsId);
            if (parentWorkflowInstance == null) {
                // Parent workflow is not in memory (restart scenario); update DB directly and then recover
                executionService.updateTaskStatusTransactional(parentTaskInsId, Constants.STATUS_COMPLETED, "Recovered Workflow");
                logger.info("Change status for parentTaskInsId: {}, from {} to {}", parentTaskInsId, taskStatus, Constants.STATUS_COMPLETED);
                recoverWorkflowInternal(parentWfInsId);
            } else {
                parentWorkflowInstance.notifySubWorkflowCompleted(parentTaskInsId, subWfInstanceId);
            }
        } catch (Exception e) {
            logger.error("Error notifying parent task on sub-workflow {} completion", subWfInstanceId, e);
        }
    }

    /**
     * Callback to the parent task when a sub-workflow fails or is cancelled.
     * If the parent task is RUNNING and has already triggered the sub-workflow, advance it to ERROR.
     */
    private void notifyParentTaskOnSubWorkflowFailure(String subWfInstanceId, String reason) {
        try {
            WfParameter wfParameter = paramsService.getWfParamsByInstanceId(subWfInstanceId);
            if (wfParameter == null) return;
            Map<String, Object> wfParams = JsonUtils.jsonToMap(wfParameter.getPvalue());
            String parentTaskInsId = ParamUtils.getParameter(wfParams, Constants.PARAM_PARENT + "." + Constants.PARAM_TASK_INSTANCE, "");
            String parentWfInsId = ParamUtils.getParameter(wfParams, Constants.PARAM_PARENT + "." + Constants.PARAM_WORKFLOW_INSTANCE, "");

            if (parentTaskInsId.isEmpty() || parentWfInsId.isEmpty()) {
                return;
            }

            TaskInstance taskInstance = executionService.getTaskInstance(parentTaskInsId);
            String taskStatus = taskInstance.getStatus();

            if (!Constants.STATUS_RUNNING.equals(taskStatus)) {
                return;
            }

            logger.info("SubWorkflow {} failed/canceled, notifying parent task {} (reason={})", subWfInstanceId, parentTaskInsId, reason);

            WorkflowInstance parentWorkflowInstance = runningWorkflows.get(parentWfInsId);
            if (parentWorkflowInstance == null) {
                executionService.updateTaskStatusTransactional(parentTaskInsId, Constants.STATUS_ERROR, "SubWorkflow failed/canceled: " + reason);
                recoverWorkflowInternal(parentWfInsId);
            } else {
                parentWorkflowInstance.notifySubWorkflowFailed(parentTaskInsId, subWfInstanceId, reason);
            }
        } catch (Exception e) {
            logger.error("Error notifying parent task on sub-workflow {} failure", subWfInstanceId, e);
        }
    }

    /**
     * Execute a task operation (retry, skip, cancel) with proper lock handling
     */
    protected boolean executeTaskOperation(String taskInstanceId, String operation) {
        TaskInstance taskInstance = getTaskInstance(taskInstanceId);
        if (taskInstance == null) {
            return false;
        }

        String wfInstanceId = taskInstance.getWfInstanceId();
        return executeWithLock(wfInstanceId, () -> executeTaskOperationInternal(taskInstance, operation));
    }

    /**
     * Internal implementation of task operations with validation
     */
    protected boolean executeTaskOperationInternal(TaskInstance taskInstance, String operation) {
        String taskInstanceId = taskInstance.getTaskInstanceId();
        String wfInstanceId = taskInstance.getWfInstanceId();

        // ===== Check whether the workflow has already been cancelled =====
        WfInstance wfInstance = executionService.getWfInstance(wfInstanceId);
        //if (wfInstance != null && Constants.STATUS_CANCELED.equals(wfInstance.getStatus())) {
        if (wfInstance != null && isTerminalStatus(wfInstance.getStatus())) {
            logger.warn("Cannot {} task instance {} because its workflow {} is cancelled", operation, taskInstanceId, wfInstanceId);
            return false;
        }

        // Re-query task status in case it changed
        taskInstance = executionService.getTaskInstance(taskInstanceId);
        String taskStatus = taskInstance.getStatus();

        // Validate task status based on operation
        if (!isValidTaskStatus(operation, taskStatus)) {
            logger.warn("Cannot {} task instance {} with status: {}", operation, taskInstanceId, taskStatus);
            return false;
        }

        return performTaskOperation(taskInstance, operation);
    }

    /**
     * Validate if a task operation is allowed for the current task status.
     * Simplified: message parameter removed, CANCELED is allowed for retry/skip.
     */
    private boolean isValidTaskStatus(String operation, String taskStatus) {
        switch (operation) {
            case "retry":
                return Constants.STATUS_ERROR.equals(taskStatus) || Constants.STATUS_TIMEOUT.equals(taskStatus) || Constants.STATUS_CANCELED.equals(taskStatus);   // Allow retry for cancelled tasks

            case "skip":
                return Constants.STATUS_WAITING.equals(taskStatus) ||
                        //Constants.STATUS_RUNNING.equals(taskStatus) ||
                        Constants.STATUS_TIMEOUT.equals(taskStatus) || Constants.STATUS_ERROR.equals(taskStatus) || Constants.STATUS_CANCELED.equals(taskStatus);   // Allow skip for cancelled tasks

            case "cancel":
                return Constants.STATUS_RUNNING.equals(taskStatus);

            default:
                logger.error("Unknown task operation: {}", operation);
                return false;
        }
    }

    /**
     * Perform the actual task operation on a workflow instance
     */
    private boolean performTaskOperation(TaskInstance taskInstance, String operation) {
        String taskInstanceId = taskInstance.getTaskInstanceId();
        String wfInstanceId = taskInstance.getWfInstanceId();

        //boolean startedTransaction = false;
        try {
            // startedTransaction = executionService.beginTransaction();

            WorkflowInstance instance = getOrCreateWorkflowInstance(wfInstanceId);

            //if (startedTransaction) {
            //executionService.commitTransaction();
            //startedTransaction = false;
            //}

            if (instance == null) {
                logger.warn("Cannot {} task instance: {} - failed to create workflow instance for ID: {}", operation, taskInstanceId, wfInstanceId);
                return false;
            }

            boolean result = false;
            switch (operation) {
                case "retry":
                    result = instance.retryTask(taskInstanceId);
                    break;
                case "skip":
                    result = instance.skipTask(taskInstanceId);
                    break;
                case "cancel":
                    result = instance.cancelTask(taskInstanceId);
                    break;
            }

            if (result) {
                logger.info("Task {} succeeded for: {}", operation, taskInstanceId);
            } else {
                logger.warn("Failed to {} task instance: {}", operation, taskInstanceId);
            }

            return result;
        } catch (Exception e) {
            //rollbackAndLogError(startedTransaction, "Failed to {} task: {}", operation, taskInstanceId, e);
            logger.error("Failed to {} task: {}", operation, taskInstanceId, e);
            return false;
        }
    }

    //-------------------------------------------------------------------------------------
    // Helper Methods
    //-------------------------------------------------------------------------------------

    /**
     * Execute an operation with proper lock handling
     */
    private <T> T executeWithLock(String wfInstanceId, Callable<T> operation) {
        Object lock = wfInstanceLocks.computeIfAbsent(wfInstanceId, k -> new Object());
        synchronized (lock) {
            try {
                return operation.call();
            } catch (Exception e) {
                logger.error("Error during locked operation: {}", e.getMessage(), e);
                return null;
            } finally {
                wfInstanceLocks.remove(wfInstanceId);
            }
        }
    }

    /**
     * Get or create a workflow instance with transaction handling
     */
    private WorkflowInstance getOrCreateWorkflowInstance(String wfInstanceId) {
        // Check if already in memory
        WorkflowInstance instance = runningWorkflows.get(wfInstanceId);
        if (instance != null) {
            return instance;
        }

        // Create from persistent data
        boolean startedTransaction = false;
        try {
            startedTransaction = executionService.beginTransaction();

            // Get workflow instance
            WfInstance wfInstance = executionService.getWfInstance(wfInstanceId);
            if (wfInstance == null) {
                rollbackTransaction(startedTransaction);
                logger.error("Cannot find workflow instance with ID: {}", wfInstanceId);
                return null;
            }
            // for print workflow name
            try {
                WfDefinition wfDefinition = definitionService.getWorkflow(wfInstance.getWorkflowId());
                logger.info("Workflow Id: {}, Workflow Name: {}, Instance Id: {}", wfInstance.getWorkflowId(), wfDefinition.getWorkflowName(), wfInstanceId);
            } catch (Exception e) {
                logger.warn("Cannot find workflow definition with ID: {}", wfInstance.getWorkflowId());
            }

            // Check workflow status
            String status = wfInstance.getStatus();
            if (isTerminalStatus(status)) {
                rollbackTransaction(startedTransaction);
                logger.warn("Workflow instance {} is already in terminal state: {}", wfInstanceId, status);
                runningWorkflows.remove(wfInstanceId);
                return null;
            }

            // Recreate workflow instance
            Map<String, Object> parameters = paramsService.getWfParamsByIdAsMap(wfInstanceId);
            WorkflowDAG dag = paramsService.buildWorkflowDAG(wfInstanceId);
            instance = createWorkflowInstance(wfInstance, parameters, dag);
            instance.restoreState();

            // Update status if needed
            if (Constants.STATUS_WAITING.equals(wfInstance.getStatus())) {
                executionService.updateStatusTransactional(wfInstanceId, Constants.STATUS_RUNNING, "");
            }

            if (startedTransaction) {
                executionService.commitTransaction();
            }

            runningWorkflows.put(wfInstanceId, instance);
            logger.info("Recreated workflow instance for ID: {}", wfInstanceId);
            return instance;
        } catch (Exception e) {
            rollbackAndLogError(startedTransaction, "Failed to recreate workflow instance: {}", wfInstanceId, e);
            return null;
        }
    }

    /**
     * Create a workflow instance
     */
    private WorkflowInstance createWorkflowInstance(WfInstance wfInstance, Map<String, Object> parameters, WorkflowDAG dag) {
        return new WorkflowInstance(this, wfInstance, parameters, dag, executionService, paramsService, taskFactory, notificationManager, executorService, monitor);
    }

    /**
     * Initialize workflow instance and parameters
     */
    private Map<String, Object> initInstanceAndParams(String workflowId, String wfInstanceId, Map<String, Object> parameters) {
        logger.debug("Initializing instance and parameters for workflow: {} (instance: {})", workflowId, wfInstanceId);
        try {
            // Get workflow definition
            WfDefinition workflow = definitionService.getWorkflow(workflowId);

            // add workflow id and workflow name
            parameters.put(Constants.PARAM_WORKFLOW_ID, workflowId);
            parameters.put(Constants.PARAM_WORKFLOW_NAME, workflow.getWorkflowName());
            parameters.put(Constants.PARAM_WORKFLOW_INSTANCE, wfInstanceId);

            // Get and merge parameters
            Map<String, Object> globalParams = paramsService.getWfGlobalParamsAsMap();
            Map<String, Object> workflowParams = definitionService.getWorkflowParamsAsMap(workflowId);
            Map<String, Object> mergedParams = mergeParameters(parameters, globalParams, workflowParams);

            // Save workflow parameters
            paramsService.saveWfParameters(workflowId, wfInstanceId, workflow, mergedParams);

            // Create task instances and save parameters
            List<TaskDefinition> tasks = definitionService.getTasksByWorkflowId(workflowId);
            createTaskInstancesAndSaveParameters(workflowId, wfInstanceId, mergedParams, parameters, tasks);

            return mergedParams;
        } catch (Exception e) {
            logger.error("Error initializing workflow parameters: {}", workflowId, e);
            throw new RuntimeException("Failed to initialize workflow parameters: " + e.getMessage(), e);
        }
    }

    /**
     * Merge parameters with proper precedence
     */
    private Map<String, Object> mergeParameters(Map<String, Object> parameters, Map<String, Object> globalParams, Map<String, Object> workflowParams) {
        Map<String, Object> mergedParams = new HashMap<>();
        if (globalParams != null) {
            mergedParams.putAll(globalParams);
        }
        if (workflowParams != null) {
            mergedParams.putAll(workflowParams);
        }
        if (parameters != null) {
            mergedParams.putAll(parameters);
        }
        return mergedParams;
    }

    /**
     * Create task instances and save parameters
     */
    private void createTaskInstancesAndSaveParameters(String workflowId, String wfInstanceId, Map<String, Object> mergedWorkflowParams, Map<String, Object> parameters, List<TaskDefinition> taskDefinitionList) {
        int index = 1;
        for (TaskDefinition taskDef : taskDefinitionList) {
            // Create task instance
            TaskInstance taskInstance = executionService.createTaskInstance(workflowId, wfInstanceId, taskDef.getTaskId(), index++);

            // add task id and task name
            parameters.put(Constants.PARAM_TASK_ID, taskInstance.getTaskId());
            parameters.put(Constants.PARAM_TASK_NAME, taskDef.getTaskName());
            parameters.put(Constants.PARAM_TASK_INSTANCE, taskInstance.getTaskInstanceId());
            parameters.put(Constants.PARAM_TASK_TYPE, taskDef.getTaskType());
            // recode to task log table
            //TaskLogService.addTaskLog(wfInstanceId, taskInstance.getTaskInstanceId(), "Status to " + taskInstance.getStatus());

            // Merge parameters
            Map<String, Object> taskParams = JsonUtils.jsonToMap(taskDef.getPvalue());
            Map<String, Object> mergedTaskParams = new HashMap<>(mergedWorkflowParams);

            if (taskParams != null) {
                mergedTaskParams.putAll(taskParams);
                mergedTaskParams.putAll(parameters);
            }

            // Save task parameters
            paramsService.saveTaskParameters(workflowId, wfInstanceId, taskInstance, taskDef, mergedTaskParams);
        }
    }

    /**
     * Cancel all running workflows (used during shutdown)
     */
    private void cancelAllRunningWorkflows() {
        runningWorkflows.forEach((wfInstanceId, instance) -> {
            try {
                Object lock = wfInstanceLocks.computeIfAbsent(wfInstanceId, k -> new Object());
                synchronized (lock) {
                    boolean cancelled = instance.cancel();
                    if (cancelled) {
                        executionService.updateStatusTransactional(wfInstanceId, Constants.STATUS_CANCELED, "Engine shutdown");
                    }
                }
            } catch (Exception e) {
                logger.error("Error canceling workflow during shutdown: {}", wfInstanceId, e);
            } finally {
                wfInstanceLocks.remove(wfInstanceId);
            }
        });

        runningWorkflows.clear();
        wfInstanceLocks.clear();
    }

    /**
     * Clean up executor resources
     */
    private void cleanupResources() {
        // Shutdown executor service
        if (threadPoolManager != null) {
            threadPoolManager.shutdownExecutorService(executorService, SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        // Shutdown notification executor
        if (notificationManager != null) {
            notificationManager.shutdown();
        }

        // Stop workflow stats
        if (monitor != null) {
            try {
                monitor.stop();
                logger.info("Workflow stats stopped");
            } catch (Exception e) {
                logger.error("Error stopping workflow stats", e);
            }
        }
    }

    /**
     * Get a task instance with validation
     */
    private TaskInstance getTaskInstance(String taskInstanceId) {
        try {
            TaskInstance taskInstance = executionService.getTaskInstance(taskInstanceId);
            if (taskInstance == null) {
                logger.warn("Cannot find task instance: {}", taskInstanceId);
                return null;
            }
            return taskInstance;
        } catch (Exception e) {
            logger.error("Error retrieving task instance: {}", taskInstanceId, e);
            return null;
        }
    }

    /**
     * Check if maximum workflow limit is reached
     */
    protected void checkWorkflowLimit() {
        if (runningWorkflows.size() >= MAX_RUNNING_WORKFLOWS) {
            logger.error("Maximum number of running workflows reached: {}", MAX_RUNNING_WORKFLOWS);
            throw new ResourceLimitException("Maximum workflow limit reached");
        }
    }

    /**
     * Check if a workflow status is terminal
     */
    private boolean isTerminalStatus(String status) {
        return Constants.STATUS_ERROR.equals(status) || Constants.STATUS_CANCELED.equals(status) || Constants.STATUS_COMPLETED.equals(status);
    }

    /**
     * Roll back a transaction if started
     */
    private void rollbackTransaction(boolean startedTransaction) {
        if (startedTransaction) {
            executionService.rollbackTransaction();
        }
    }

    /**
     * Roll back a transaction and log an error
     */
    private void rollbackAndLogError(boolean startedTransaction, String message, Object param, Exception e) {
        rollbackTransaction(startedTransaction);
        logger.error(message, param, e);
    }

}