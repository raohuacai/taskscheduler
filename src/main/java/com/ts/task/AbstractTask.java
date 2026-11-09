/*
 * Description: Skeletal Task implementation providing lifecycle orchestration, cancellation, and parameter binding
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.task;

import com.ts.common.constants.Constants;
import com.ts.common.utils.DateUtils;
import com.ts.common.utils.ParamUtils;
import com.ts.core.engine.WorkflowEngine;
import com.ts.exception.TaskExecutionException;
import com.ts.exception.TaskTimeoutException;
import com.ts.service.impl.TaskLogService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * Skeletal implementation of {@link Task} providing common lifecycle infrastructure
 * for all concrete task types.
 *
 * <p>Implements the full {@code execute()} orchestration: records the executing
 * thread (for interrupt-based cancellation), calls {@link #initialize(java.util.Map)}
 * to bind well-known parameters ({@code workflowId}, {@code taskId}, etc.),
 * then invokes {@code preAction} → {@code action} → {@code postAction} in sequence.
 * Cancellation is checked before each phase; if cancelled, execution stops cleanly.
 *
 * <p>Subclasses override {@link #preAction}, {@link #action}, and
 * {@link #postAction} to provide task-specific behaviour. {@link #cancel()}
 * sets the {@code canceled} flag and interrupts the executing thread if one
 * is active, so blocking I/O calls wake up promptly.
 *
 * <p>The {@code workflowEngine} field is injected by
 * {@link com.ts.task.DefaultTaskFactory} and allows tasks to persist parameter
 * updates back to the database.
 *
 * @see com.ts.task.timeout.TimeoutTask
 * @see com.ts.task.timeout.JavaTask
 */
public abstract class AbstractTask implements Task {
    private static final Logger logger = LogManager.getLogger(AbstractTask.class);

    /** Atomic flag set to {@code true} when {@link #cancel()} is called;
     *  checked between lifecycle phases to halt execution cleanly. */
    protected final AtomicBoolean canceled = new AtomicBoolean(false);

    /** The thread currently executing this task's lifecycle; held so that
     *  {@link #cancel()} can interrupt blocking I/O or sleep calls. */
    protected volatile Thread executingThread;

    /** Default task execution timeout in seconds (value: 3600 = 1 hour).
     *  Valid range: any positive integer; enforced by {@link com.ts.task.timeout.TimeoutTask}. */
    protected static final int DEFAULT_TIMEOUT = 3600;

    /** Seconds to wait after interrupting the executing thread before
     *  giving up on a graceful stop (value: 1 second). */
    protected static final int FORCE_TERMINATE_TIMEOUT = 1;

    /** Injected by {@link com.ts.task.DefaultTaskFactory} after instantiation;
     *  allows tasks to persist updated parameter values back to the database
     *  via {@link com.ts.core.engine.WorkflowEngine#updateTaskParams}. */
    protected WorkflowEngine workflowEngine;

    /** Effective task timeout in seconds, resolved from the {@code task_timeout}
     *  parameter; falls back to {@link #DEFAULT_TIMEOUT} when not specified. */
    protected int timeout;

    /** Workflow definition ID of the workflow that owns this task. */
    protected String workflowId = "";

    /** Human-readable name of the owning workflow definition. */
    protected String workflowName = "";

    /** Runtime instance ID of the workflow execution that owns this task. */
    protected String workflowInstanceId = "";

    /** Task definition ID identifying the task type and configuration. */
    protected String taskId = "";

    /** Human-readable name of this task definition. */
    protected String taskName = "";

    /** Runtime instance ID uniquely identifying this task execution. */
    protected String taskInstanceId = "";

    /** Task type group name (e.g. {@code SHELL}, {@code HTTP}, {@code SQL})
     *  used in log messages to identify the executing task class. */
    protected String taskType = "";

    @Override
    /**
     * Executes.
     *
     * @param parameters  parameter map
     * @throws Exception  if an error occurs
     */
    public void execute(Map<String, Object> parameters) throws Exception {
        long startTime = System.currentTimeMillis();
        try {
            // record current thread and start time
            executingThread = Thread.currentThread();
            // initialize timeout, task id etc.
            initialize(parameters);
            logger.info("{} Task execution started: {}, {}, {}", taskType, taskId, taskName, taskInstanceId);
            // replace All Variables
            parameters = replaceAllVariables(parameters);

            // Check if canceled before preAction
            if (canceled.get()) {
                logger.warn("Task is already canceled: {}, {}, {}", taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Task is already canceled");
                return;
            }
            // Pre-action
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Executing pre-action");
            long preStartTime = System.currentTimeMillis();
            logger.debug("Executing pre-action: {}, {}, {}", taskId, taskName, taskInstanceId);
            preAction(parameters);
            long preEndTime = System.currentTimeMillis();
            logger.debug("Pre-action completed in {} ms: {}, {}, {}", preEndTime - preStartTime, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Pre-action completed");

            // Check if canceled before action
            if (canceled.get()) {
                logger.warn("Task is already canceled: {}, {}, {}", taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Task is already canceled");
                return;
            }
            // Main action
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Executing main action");
            long actionStartTime = System.currentTimeMillis();
            logger.debug("Executing main action: {}, {}, {}", taskId, taskName, taskInstanceId);
            action(parameters);
            long actionEndTime = System.currentTimeMillis();
            logger.debug("Main action completed in {} ms: {}, {}, {}", actionEndTime - actionStartTime, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Main action completed");

            // Check if canceled before postAction
            if (canceled.get()) {
                logger.warn("Task is already canceled: {}, {}, {}", taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Task is already canceled");
                return;
            }
            // Post-action
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Executing post-action");
            long postStartTime = System.currentTimeMillis();
            logger.debug("Executing post-action: {}, {}, {}", taskId, taskName, taskInstanceId);
            postAction(parameters);
            long postEndTime = System.currentTimeMillis();
            logger.debug("Post-action completed in {} ms: {}, {}, {}", postEndTime - postStartTime, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Post-action completed");

        } catch (Exception e) {
            // TaskTimeoutException raised by subclass
            if ((e instanceof TaskTimeoutException)) {
                // Timeout occurred
                logger.error("Task timeout: {}, {}, {}", taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Task timeout: " + e.getMessage());
                throw e;
            }
            // Interrupted caused by a cancel
            else if (e instanceof InterruptedException) {
                // Do not restore the interrupt status, as JDBC drivers (e.g. MySQL) check it
                // and would fail subsequent database operations.
                logger.warn("Task interrupted due to cancellation: {}, {}, {}", taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Task interrupted: " + e.getMessage());
                throw new TaskExecutionException("Task interrupted: " + e.getMessage(), e, taskId, taskInstanceId);
            }
            // Task Execution Exception
            else if (e instanceof TaskExecutionException) {
                // Normal Task Execution Exception
                logger.error("Task execution failed: {}, {}, {}", taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Task execution failed: " + e.getMessage());
                throw e;
            }
            // Other Exceptions
            else {
                logger.error("Task execution failed: {}, {}, {}", taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Task execution failed: " + e.getMessage());
                throw new TaskExecutionException("Task execution failed: " + e.getMessage(), e, taskId, taskInstanceId);
            }
        } finally {
            cleanupResources();
            long endTime = System.currentTimeMillis();
            logger.info("{} Task execution completed in {} ms: {}, {}, {}", taskType, endTime - startTime, taskId, taskName, taskInstanceId);
        }
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
        // Default implementation does nothing
    }

    @Override
    /**
      * Executes the core task logic.
      * Called by {@link AbstractTask#execute} after {@link #preAction}.
      *
      * @param parameters  merged task parameter map
      * @throws Exception  if the task logic fails
      */
    public abstract void action(Map<String, Object> parameters) throws Exception;

    @Override
    /**
     * Post the action.
     *
     * @param parameters  parameter map
     * @throws Exception  if an error occurs
     */
    public void postAction(Map<String, Object> parameters) throws Exception {
        // Default implementation does nothing
    }

    @Override
    /**
     * Cancels.
     *
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean cancel() {
        logger.info("Canceling task: {}, {}, {}", taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Canceling task");
        if (canceled.compareAndSet(false, true)) {
            if (executingThread != null) {
                executingThread.interrupt();
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the canceled.
     *
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean isCanceled() {
        return canceled.get();
    }

    /**
     * Cleanup the resources.
     */
    public void cleanupResources() {
        executingThread = null;
        // Clear any residual interrupt flag to protect subsequent JDBC/SQL operations.
        // Because we deliberately do NOT restore the interrupt status after catching
        // InterruptedException, this call is a no-op in the normal flow, but serves
        // as a safety net against accidental flag leakage.
        Thread.interrupted();
    }

    /**
     * Binds well-known workflow and task context parameters from the merged parameter
     * map into the protected fields used throughout the task lifecycle.
     *
     * <p>Called once at the start of {@link #execute} before any lifecycle phase.
     * Resolves {@link #timeout} from {@code task_timeout} (falling back to
     * {@link #DEFAULT_TIMEOUT}), and populates {@link #workflowId},
     * {@link #taskId}, {@link #taskInstanceId}, and related identity fields.
     *
     * @param parameters  the merged task parameter map supplied by the workflow engine
     */
    protected void initialize(Map<String, Object> parameters) {
        timeout = ParamUtils.getTimeout(parameters, Constants.PARAM_TASK_TIMEOUT, DEFAULT_TIMEOUT);
        workflowId = ParamUtils.getParameter(parameters, Constants.PARAM_WORKFLOW_ID, "");
        workflowName = ParamUtils.getParameter(parameters, Constants.PARAM_WORKFLOW_NAME, "");
        workflowInstanceId = ParamUtils.getParameter(parameters, Constants.PARAM_WORKFLOW_INSTANCE, "");
        taskId = ParamUtils.getParameter(parameters, Constants.PARAM_TASK_ID, "");
        taskName = ParamUtils.getParameter(parameters, Constants.PARAM_TASK_NAME, "");
        taskInstanceId = ParamUtils.getParameter(parameters, Constants.PARAM_TASK_INSTANCE, "");
        taskType = ParamUtils.getParameter(parameters, Constants.PARAM_TASK_TYPE, "");
    }

    protected Map<String, Object>  replaceAllVariables(Map<String, Object> parameters) {
        Map<String, Object> dateParams = new HashMap<>();
        String currentDate = DateUtils.formatDate(new Date(), "MM/dd/yyyy");
        String currentDateTime = DateUtils.formatDate(new Date(), "MM/dd/yyyy HH:mm:ss");
        dateParams.put("dataDate", currentDate);
        dateParams.put("dataTime", currentDateTime);
        saveParams(parameters, dateParams);
         return ParamUtils.replaceAllVariables(parameters);
    }

    /**
     * Save a single parameter to both in-memory map and database.
     * Falls back to memory-only when workflowEngine is not available.
     *
     * @param parameters the in-memory parameter map
     * @param key        parameter key
     * @param value      parameter value
     */
    protected void saveParam(Map<String, Object> parameters, String key, Object value) {
        parameters.put(key, value);
        if (workflowEngine != null && taskInstanceId != null && !taskInstanceId.isEmpty()) {
            try {
                Map<String, Object> updates = new java.util.HashMap<>();
                updates.put(key, value);
                workflowEngine.updateTaskParams(taskInstanceId, updates);
            } catch (Exception e) {
                logger.warn("Failed to persist param [{}] for task [{}]: {}", key, taskInstanceId, e.getMessage());
            }
        }
    }

    /**
     * Save multiple parameters to both in-memory map and database in a single DB write.
     * Falls back to memory-only when workflowEngine is not available.
     *
     * @param parameters the in-memory parameter map
     * @param updates    key-value pairs to save
     */
    protected void saveParams(Map<String, Object> parameters, Map<String, Object> updates) {
        parameters.putAll(updates);
        if (workflowEngine != null && taskInstanceId != null && !taskInstanceId.isEmpty()) {
            try {
                workflowEngine.updateTaskParams(taskInstanceId, updates);
            } catch (Exception e) {
                logger.warn("Failed to persist params {} for task [{}]: {}", updates.keySet(), taskInstanceId, e.getMessage());
            }
        }
    }

}