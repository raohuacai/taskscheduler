/*
 * Description: Execution service bridging HTTP handlers to WorkflowEngine with transactional DB updates
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ts.common.constants.Constants;
import com.ts.common.utils.ParamUtils;
import com.ts.core.engine.WorkflowEngine;
import com.ts.core.engine.WorkflowResult;
import com.ts.core.transaction.TransactionManager;
import com.ts.core.transaction.TransactionManagerFactory;
import com.ts.dao.WfInstanceDao;
import com.ts.dao.TaskInstanceDao;
import com.ts.model.entity.TaskInstance;
import com.ts.model.entity.WfInstance;
import com.ts.service.ExecutionService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
/**
 * Implementation of {@link com.ts.service.ExecutionService} that bridges HTTP
 * handlers to the {@link com.ts.core.engine.WorkflowEngine} and persists
 * workflow/task instance state via MyBatis DAOs.
 *
 * <p>Status update methods ({@link #updateTaskStatusTransactional},
 * {@link #updateStatusTransactional}) wrap database writes in an explicit
 * {@link com.ts.core.transaction.TransactionManager} transaction: if no
 * transaction is already active, a new one is started and committed within
 * the method; otherwise the caller's transaction is reused.
 *
 * <p>Note: {@link #getWfInstance} throws {@link IllegalArgumentException} (not
 * returns {@code null}) when the instance is not found — callers should not check
 * for a null return.
 */
public class ExecutionServiceImpl implements ExecutionService {
    private static final Logger logger = LogManager.getLogger(ExecutionServiceImpl.class);
    private WorkflowEngine workflowEngine;
    private final WfInstanceDao wfInstanceDao;
    private final TaskInstanceDao taskInstanceDao;
    private final TransactionManager transactionManager;

    /**
     * Constructs a new ExecutionServiceImpl instance.
     *
     * @param wfInstanceDao  DAO dependency
     * @param taskInstanceDao  DAO dependency
     */
    public ExecutionServiceImpl(WfInstanceDao wfInstanceDao, TaskInstanceDao taskInstanceDao) {
        this.wfInstanceDao = wfInstanceDao;
        this.taskInstanceDao = taskInstanceDao;
        this.transactionManager = TransactionManagerFactory.getInstance();
    }

    /**
     * Sets the workflow engine.
     *
     * @param workflowEngine  engine instance
     */
    public void setWorkflowEngine(WorkflowEngine workflowEngine) {
        this.workflowEngine = workflowEngine;
    }

    @Override
    /**
     * Returns the workflow instance identified by {@code wfInstanceId}.
     *
     * <p>Note: throws {@link IllegalArgumentException} (not {@code null}) if the instance is not found.
     *
     * @param wfInstanceId  instance identifier
     * @return the requested wfinstance object, or null if not found
     */
    public WfInstance getWfInstance(String wfInstanceId) {
        if (wfInstanceId == null || wfInstanceId.isEmpty()) {
            throw new IllegalArgumentException("workflow instance ID cannot be null or empty");
        }

        // Get workflow instance
        WfInstance wfInstance = wfInstanceDao.getWfInstance(wfInstanceId);
        if (wfInstance == null) {
            throw new IllegalArgumentException("workflow instance not found: " + wfInstanceId);
        }
        return wfInstance;
    }

    @Override
    /**
     * Searches workflow instances with optional filters and returns a paginated result.
     *
     * @param workflowId  workflowId identifier
     * @param workflowName  workflowName name
     * @param wfInstanceId  instance identifier
     * @param parentWfInstanceId  instance identifier
     * @param topParentInstanceId  instance identifier
     * @param status  status value
     * @param startDateFrom  date/time value
     * @param startDateTo  date/time value
     * @param pageNum  page number (1-based)
     * @param pageSize  page size
     * @param orderBy  ORDER BY clause
     * @return a paginated result set
     */
    public PageInfo<WfInstance> search(String workflowId, String workflowName, String wfInstanceId, String parentWfInstanceId,
                                       String topParentInstanceId, String status,
                                       Date startDateFrom, Date startDateTo, int pageNum, int pageSize, String orderBy) {
        // Begin paginated query
        //PageHelper.startPage(pageNum, pageSize);
        PageHelper.startPage(pageNum, pageSize, (orderBy == null || orderBy.isEmpty()) ? "START_DATE DESC" : orderBy);
        // Invoke the existing query method with filters
        List<WfInstance> wfInstances = wfInstanceDao.getAllWfInstances(workflowId, workflowName, wfInstanceId, parentWfInstanceId, topParentInstanceId, status,
                startDateFrom, startDateTo);
        // Wrap results with pagination metadata
        return new PageInfo<>(wfInstances);
    }

    /**
     * Creates the wf instance.
     *
     * @param workflowId  workflowId identifier
     * @param WfInstanceId  instance identifier
     * @param pWfInstanceId  instance identifier
     * @param topParentInstanceId  instance identifier
     * @return the result of the operation
     */
    public WfInstance createWfInstance(String workflowId, String WfInstanceId, String pWfInstanceId, String topParentInstanceId) {
        // Create workflow instance
        WfInstance wfInstance = new WfInstance();
        wfInstance.setWfInstanceId(WfInstanceId);
        wfInstance.setWorkflowId(workflowId);
        wfInstance.setStartDate(new Date());
        wfInstance.setErrorFlag(Constants.FLAG_NO);
        wfInstance.setStatus(Constants.STATUS_WAITING);
        wfInstance.setParentWfInstanceId(pWfInstanceId);
        wfInstance.setTopParentInstanceId(topParentInstanceId);
        // Save workflow instance
        wfInstanceDao.createWfInstance(wfInstance);
        return wfInstance;
    }

    private WfInstance updateWFLStatusToError(String wfInstanceId, String status, String message) {
        // update status,end date and error flag
        WfInstance wfInstance = new WfInstance();
        wfInstance.setWfInstanceId(wfInstanceId);
        wfInstance.setEndDate(new Date());
        wfInstance.setErrorFlag(Constants.FLAG_YES);
        wfInstance.setStatus(status);
        wfInstance.setErrorMessage(message);
        // Save workflow instance
        wfInstanceDao.updateStatusToError(wfInstance);
        return wfInstance;
    }

    private WfInstance updateWFLStatusToRunning(String wfInstanceId) {
        // update status,end date and error flag
        WfInstance wfInstance = new WfInstance();
        wfInstance.setWfInstanceId(wfInstanceId);
        wfInstance.setStartDate(new Date());
        wfInstance.setEndDate(null);
        wfInstance.setErrorMessage("");
        wfInstance.setStatus(Constants.STATUS_RUNNING);
        // Save workflow instance
        wfInstanceDao.updateStatusToRunning(wfInstance);
        return wfInstance;
    }

    /**
     * Updates the status.
     *
     * @param wfInstanceId  instance identifier
     * @param status  status value
     * @param message  descriptive message
     * @return the result of the operation
     */
    public WfInstance updateStatus(String wfInstanceId, String status, String message) {
        if (Constants.STATUS_RUNNING.equals(status) || Constants.STATUS_WAITING.equals(status)) {
            return updateWFLStatusToRunning(wfInstanceId);
        } else if (Constants.STATUS_ERROR.equals(status)) {
            return updateWFLStatusToError(wfInstanceId, status, message);
        } else {
            // update status,end date and error flag
            WfInstance wfInstance = new WfInstance();
            wfInstance.setWfInstanceId(wfInstanceId);
            wfInstance.setEndDate(new Date());
            wfInstance.setStatus(status);
            // Save workflow instance
            wfInstanceDao.updateStatus(wfInstance);
            return wfInstance;
        }
    }

    /**
     * Returns the task instance.
     *
     * @param taskInstanceId  instance identifier
     * @return the requested taskinstance object, or null if not found
     */
    public TaskInstance getTaskInstance(String taskInstanceId) {
        if (taskInstanceId == null || taskInstanceId.isEmpty()) {
            throw new IllegalArgumentException("task instance ID cannot be null or empty");
        }
        TaskInstance taskInstance = taskInstanceDao.getTaskInstance(taskInstanceId);
        if (taskInstance == null) {
            throw new IllegalArgumentException("task instance not found: " + taskInstanceId);
        }
        return taskInstance;
    }

    /**
     * Returns the task instances for workflow.
     *
     * @param wfInstanceId  instance identifier
     * @return a list of matching records
     */
    public List<TaskInstance> getTaskInstancesForWorkflow(String wfInstanceId) {
        if (wfInstanceId == null || wfInstanceId.isEmpty()) {
            throw new IllegalArgumentException("workflow instance ID cannot be null or empty");
        }
        List<TaskInstance> taskInstances = taskInstanceDao.getTaskInstancesByWfInstanceId(wfInstanceId);
        if (taskInstances == null) {
            throw new IllegalArgumentException("task instance not found: " + wfInstanceId);
        }
        return taskInstances;
    }

    /**
     * Creates the task instance.
     *
     * @param workflowId  workflowId identifier
     * @param wfInstanceId  instance identifier
     * @param taskId  identifier
     * @param index  index value
     * @return the result of the operation
     */
    public TaskInstance createTaskInstance(String workflowId, String wfInstanceId, String taskId, int index) {
        TaskInstance taskInstance = new TaskInstance();
        // taskInstanceId = wfInstanceId + 00index
        String taskInstanceId = wfInstanceId + '_' + String.format("%02d", index);
        if (index > 99) {
            taskInstanceId = wfInstanceId + '_' + String.format("%05d", index);
        }
        taskInstance.setTaskInstanceId(taskInstanceId);
        taskInstance.setTaskId(taskId);
        taskInstance.setWfInstanceId(wfInstanceId);
        taskInstance.setWorkflowId(workflowId);
        taskInstance.setStartDate(new Date());
        taskInstance.setErrorFlag(Constants.FLAG_NO);
        taskInstance.setStatus(Constants.STATUS_WAITING);
        taskInstanceDao.createTaskInstance(taskInstance);
        return taskInstance;
    }

    private TaskInstance updateTaskStatusToError(String taskInstanceId, String status, String message) {
        // update status,end date and error flag
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setTaskInstanceId(taskInstanceId);
        taskInstance.setEndDate(new Date());
        taskInstance.setErrorFlag(Constants.FLAG_YES);
        taskInstance.setStatus(status);
        taskInstance.setErrorMessage(message);
        // Save task instance
        taskInstanceDao.updateStatusToError(taskInstance);
        return taskInstance;
    }

    private TaskInstance updateTaskStatusToRunning(String taskInstanceId, String status) {
        // update status,end date and error flag
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setTaskInstanceId(taskInstanceId);
        taskInstance.setStartDate(new Date());
        taskInstance.setEndDate(null);
        taskInstance.setStatus(status);
        taskInstance.setErrorMessage("");
        // Save task instance
        taskInstanceDao.updateStatusToRunning(taskInstance);
        return taskInstance;
    }

    /**
     * Starts the workflow.
     *
     * @param workflowId  workflowId identifier
     * @param parameters  parameter map
     * @return the result of the operation
     */
    public WorkflowResult startWorkflow(String workflowId, Map<String, Object> parameters) {
        if (workflowId == null || workflowId.isEmpty()) {
            return WorkflowResult.failure("Workflow ID cannot be null or empty");
        }
        try {
            // Start workflow
            String wfInstanceId = workflowEngine.start(workflowId, parameters);
            logger.info("Started workflow: {}, {}, {}", workflowId, ParamUtils.getParameter(parameters, Constants.PARAM_WORKFLOW_NAME), wfInstanceId);
            return WorkflowResult.success(wfInstanceId);
        } catch (Exception e) {
            logger.error(e.getMessage());
            return WorkflowResult.failure(e.getMessage());
        }
    }

    /**
     * Recovers the workflow.
     *
     * @param wfInstanceId  instance identifier
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean recoverWorkflow(String wfInstanceId) {
        if (wfInstanceId == null || wfInstanceId.isEmpty()) {
            throw new IllegalArgumentException("workflow instance ID cannot be null or empty");
        }
        // Recover workflow
        boolean result = workflowEngine.recover(wfInstanceId);

        logger.info("Recovered workflow instance: {}", wfInstanceId);
        return result;
    }

    /**
     * Cancels the workflow.
     *
     * @param wfInstanceId  instance identifier
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean cancelWorkflow(String wfInstanceId) {
        if (wfInstanceId == null || wfInstanceId.isEmpty()) {
            throw new IllegalArgumentException("workflow instance ID cannot be null or empty");
        }
        // Cancel workflow
        boolean result = workflowEngine.cancel(wfInstanceId);

        logger.info("Canceled workflow instance: {}", wfInstanceId);
        return result;
    }

    /**
     * Retries the task.
     *
     * @param taskInstanceId  instance identifier
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean retryTask(String taskInstanceId) {
        if (taskInstanceId == null || taskInstanceId.isEmpty()) {
            throw new IllegalArgumentException("task instance ID cannot be null or empty");
        }

        // Retry task
        boolean result = workflowEngine.retryTask(taskInstanceId);

        logger.info("Retried task instance: {}", taskInstanceId);
        return result;
    }

    /**
     * Skips the task.
     *
     * @param taskInstanceId  instance identifier
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean skipTask(String taskInstanceId) {
        if (taskInstanceId == null || taskInstanceId.isEmpty()) {
            throw new IllegalArgumentException("task instance ID cannot be null or empty");
        }

        // Skip task
        boolean result = workflowEngine.skipTask(taskInstanceId);

        logger.info("Skipped task instance: {}", taskInstanceId);
        return result;
    }

    /**
     * Cancels the task.
     *
     * @param taskInstanceId  instance identifier
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean cancelTask(String taskInstanceId) {
        if (taskInstanceId == null || taskInstanceId.isEmpty()) {
            throw new IllegalArgumentException("task instance ID cannot be null or empty");
        }

        // Cancel task
        boolean result = workflowEngine.cancelTask(taskInstanceId);

        logger.info("Canceled task instance: {}", taskInstanceId);
        return result;
    }

    // Transaction management helper methods
    /**
     * Begins the transaction.
     *
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean beginTransaction() {
        try {
            transactionManager.beginTransaction();
            return true;
        } catch (Exception e) {
            logger.error("Failed to begin transaction", e);
            return false;
        }
    }

    /**
     * Commits the transaction.
     */
    public void commitTransaction() {
        transactionManager.commitTransaction();
    }

    /**
     * Rolls back the transaction.
     */
    public void rollbackTransaction() {
        transactionManager.rollbackTransaction();
    }

    /**
     * Checks whether the transaction active.
     *
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean isTransactionActive() {
        return transactionManager.isTransactionActive();
    }

    /**
     * Updates the task status transactional.
     *
     * @param taskInstanceId  instance identifier
     * @param status  status value
     * @param message  descriptive message
     * @return the result of the operation
     */
    public TaskInstance updateTaskStatusTransactional(String taskInstanceId, String status, String message) {
        boolean startedTransaction = false;
        try {
            // If no active transaction exists, start a new one
            if (!isTransactionActive()) {
                startedTransaction = beginTransaction();
            }

            TaskInstance taskInstance;
            if (Constants.STATUS_RUNNING.equals(status) || Constants.STATUS_WAITING.equals(status)) {
                taskInstance = updateTaskStatusToRunning(taskInstanceId, status);
            } else if (Constants.STATUS_ERROR.equals(status) || Constants.STATUS_TIMEOUT.equals(status)) {
                taskInstance = updateTaskStatusToError(taskInstanceId, status, message);
            } else {
                // Update status, end date, and error flag in the database
                taskInstance = new TaskInstance();
                taskInstance.setTaskInstanceId(taskInstanceId);
                taskInstance.setEndDate(new Date());
                taskInstance.setStatus(status);
                taskInstance.setErrorMessage(message);
                taskInstanceDao.updateStatus(taskInstance);
            }

            // If we started the transaction in this method, commit it
            if (startedTransaction) {
                commitTransaction();
            }

            return taskInstance;
        } catch (Exception e) {
            // If we started the transaction in this method, roll it back
            if (startedTransaction) {
                rollbackTransaction();
            }
            logger.error("Transactional update of task instance status failed: " + taskInstanceId, e);
            throw new RuntimeException("Failed to update task instance status: " + e.getMessage(), e);
        }
    }

    /**
     * Updates the status transactional.
     *
     * @param wfInstanceId  instance identifier
     * @param status  status value
     * @param message  descriptive message
     * @return the result of the operation
     */
    public WfInstance updateStatusTransactional(String wfInstanceId, String status, String message) {
        boolean startedTransaction = false;
        try {
            // If no active transaction exists, start a new one
            if (!isTransactionActive()) {
                startedTransaction = beginTransaction();
            }

            WfInstance wfInstance;
            if (Constants.STATUS_RUNNING.equals(status)) {
                wfInstance = updateWFLStatusToRunning(wfInstanceId);
            } else if (Constants.STATUS_ERROR.equals(status)) {
                wfInstance = updateWFLStatusToError(wfInstanceId, status, message);
            } else {
                // Update status and end date in the database
                wfInstance = new WfInstance();
                wfInstance.setWfInstanceId(wfInstanceId);
                wfInstance.setEndDate(new Date());
                wfInstance.setStatus(status);
                wfInstanceDao.updateStatus(wfInstance);
            }

            // If we started the transaction in this method, commit it
            if (startedTransaction) {
                commitTransaction();
            }

            return wfInstance;
        } catch (Exception e) {
            // If we started the transaction in this method, roll it back
            if (startedTransaction) {
                rollbackTransaction();
            }
            logger.error("Transactional update of workflow instance status failed: " + wfInstanceId, e);
            throw new RuntimeException("Failed to update workflow instance status: " + e.getMessage(), e);
        }
    }

}

