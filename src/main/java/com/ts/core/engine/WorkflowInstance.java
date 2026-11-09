/*
 * Description: Per-execution state container that manages task scheduling, DAG traversal, and task lifecycle within one workflow run
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.core.engine;

import com.ts.common.constants.Constants;
import com.ts.common.utils.ConditionEvaluator;
import com.ts.common.utils.JsonUtils;
import com.ts.common.utils.ParamUtils;
import com.ts.core.dag.WorkflowDAG;
import com.ts.monitor.WorkflowMonitor;
import com.ts.exception.TaskExecutionException;
import com.ts.exception.TaskTimeoutException;
import com.ts.model.entity.*;
import com.ts.service.ExecutionService;
import com.ts.service.ParamsService;
import com.ts.service.impl.TaskLogService;
import com.ts.task.Task;
import com.ts.task.TaskFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Per-execution state container and task scheduler for a single workflow run.
 *
 * <p>Manages the complete lifecycle of one workflow instance: builds the task DAG,
 * determines which tasks are ready to execute (AND/OR dependency logic), submits
 * tasks to the shared executor, handles task completion/failure/timeout callbacks,
 * and decides when the workflow as a whole has finished.
 *
 * <p>Concurrency model:
 * <ul>
 *   <li>{@code runningTasks} — a ConcurrentMap of active {@link TaskFutureWrapper}s;
 *       entries are added in {@code submitTask()} and removed in
 *       {@code updateTaskStatus()}.</li>
 *   <li>{@code submitLock} — coarse lock that serialises the
 *       read-then-submit sequence in {@code checkAndSubmitNextTasks()}, preventing
 *       the same task from being submitted twice when two completions arrive
 *       concurrently.</li>
 *   <li>Per-task locks ({@code taskInstanceLocks}) guard individual task status
 *       transitions (retry / skip / cancel) against concurrent executor callbacks.</li>
 * </ul>
 *
 * <p>Sub-workflow integration: WORKFLOW-type tasks trigger a child
 * {@link WorkflowEngine#start} call and then immediately return from
 * {@code execute()}. The child workflow calls back via
 * {@link #onSubWorkflowComplete} or {@link #onSubWorkflowFailed} when it
 * terminates, allowing the parent instance to continue the DAG.
 *
 * @see WorkflowEngine
 * @see com.ts.core.dag.WorkflowDAG
 */
public class WorkflowInstance {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowInstance.class);
    private static final String UPSTREAM_PARAM_PREFIX = "{" + Constants.PARAM_UPSTREAM_TASK_REF + ".";
    private static final Pattern UPSTREAM_PARAM_PATTERN =
            Pattern.compile("\\{" + Constants.PARAM_UPSTREAM_TASK_REF + "\\.([^.]+)\\.([^}]+)\\}");

    private final String wfInstanceId;
    private final String workflowId;
    private final String workflowName;
    private final WorkflowEngine workflowEngine;
    private final WfInstance wfInstance;
    private final ExecutionService executionService;
    private final ParamsService paramsService;
    private final TaskFactory taskFactory;
    private final Map<String, Object> parameters;
    private final WorkflowDAG dag;
    private final NotificationManager notificationManager;
    private final ThreadPoolExecutor executorService;
    private final WorkflowMonitor monitor;

    private volatile boolean cancelled = false;
    private volatile boolean completed = false;
    private final ConcurrentMap<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
    private final Set<String> runningWorkflowTaskIds = ConcurrentHashMap.newKeySet();
    private final Set<String> completedTaskInstanceIds = ConcurrentHashMap.newKeySet();
    private final Set<String> errorTaskInstanceIds = ConcurrentHashMap.newKeySet();
    private final Set<String> allTaskInstanceIds = ConcurrentHashMap.newKeySet();
    // Set of OR task instance IDs (used for OR-gate dependency evaluation)
    private final Set<String> allOrTaskInstanceIds = ConcurrentHashMap.newKeySet();
    // Lock object used for synchronization within this workflow instance
    private final ConcurrentHashMap<String, Object> taskInstanceLocks = new ConcurrentHashMap<>();
    private final Object submitLock = new Object();

    /**
     * FutureTask wrapper for task execution; holds a reference to the Task instance
     */
    public static class TaskFutureWrapper<T> extends FutureTask<T> {
        private final Task taskImpl;
        private final String taskInstanceId;

        /**
         * Constructs a TaskFutureWrapper, associating a {@link Task} implementation
         * with the underlying {@link java.util.concurrent.FutureTask}.
         *
         * @param runnable       the runnable encapsulating the task lifecycle call
         * @param result         the value returned by {@link java.util.concurrent.FutureTask#get()}
         *                       on normal completion (typically {@code null})
         * @param taskImpl       the {@link Task} instance being wrapped, used for
         *                       interrupt-based cancellation
         * @param taskInstanceId the task instance ID, used for logging and status updates
         */
        public TaskFutureWrapper(Runnable runnable, T result, Task taskImpl, String taskInstanceId) {
            super(runnable, result);
            this.taskImpl = taskImpl;
            this.taskInstanceId = taskInstanceId;
        }

        /**
         * Returns the task impl.
         *
         * @return the requested taskimpl object, or null if not found
         */
        public Task getTaskImpl() {
            return taskImpl;
        }

        /**
         * Returns the task instance id.
         *
         * @return the result as a String
         */
        public String getTaskInstanceId() {
            return taskInstanceId;
        }
    }

    /**
     * Constructs a new WorkflowInstance instance.
     *
     * @param workflowEngine      engine instance
     * @param wfInstance          wfInstance value
     * @param parameters          parameter map
     * @param dag                 dag value
     * @param executionService    service dependency
     * @param paramsService       service dependency
     * @param taskFactory         factory instance
     * @param notificationManager notificationManager value
     * @param executorService     service dependency
     * @param monitor             monitor value
     */
    public WorkflowInstance(
            WorkflowEngine workflowEngine,
            WfInstance wfInstance,
            Map<String, Object> parameters,
            WorkflowDAG dag,
            ExecutionService executionService,
            ParamsService paramsService,
            TaskFactory taskFactory,
            NotificationManager notificationManager,
            ThreadPoolExecutor executorService,
            WorkflowMonitor monitor
    ) {
        this.workflowEngine = workflowEngine;
        this.wfInstance = wfInstance;
        this.parameters = parameters;
        this.dag = dag;
        this.executionService = executionService;
        this.paramsService = paramsService;
        this.taskFactory = taskFactory;
        this.notificationManager = notificationManager;
        this.executorService = executorService;
        this.monitor = monitor;
        this.workflowId = wfInstance.getWorkflowId();
        this.workflowName = ParamUtils.getParameter(parameters, Constants.PARAM_WORKFLOW_NAME, "");
        this.wfInstanceId = wfInstance.getWfInstanceId();
        // Initialize the set of all task instance IDs for this workflow
        //executionService.getTaskInstancesForWorkflow(wfInstanceId).forEach(task -> allTaskInstanceIds.add(task.getTaskInstanceId()));
        // Populate allTaskInstanceIds by iterating over every task instance in the workflow
        executionService.getTaskInstancesForWorkflow(wfInstanceId).forEach(task -> {
            String taskInstanceId = task.getTaskInstanceId();
            allTaskInstanceIds.add(taskInstanceId);
            TaskParameter taskParam = paramsService.getTaskParamsByInstanceId(taskInstanceId);
            if (taskParam != null && Constants.TYPE_TASK_OR.equals(taskParam.getTaskType()) && taskParam.getEnableFlag().equals(Constants.FLAG_YES)) {
                allOrTaskInstanceIds.add(taskInstanceId);
            }
        });
    }

    /**
     * Starts.
     *
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean start() {
        logger.info("Running workflow: {}, {}, {}", workflowId, workflowName, wfInstanceId);
        checkAndSubmitNext();
        return true;
    }

    /**
     * Cancels.
     *
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean cancel() {
        if (completed) {
            // All tasks are done and the completion has been persisted. Reporting success
            // here would let the caller overwrite a finished workflow with CANCELED, so
            // refuse instead and let the completion path win.
            logger.warn("The workflow is already completed, cannot cancel.");
            return false;
        }
        if (cancelled) {
            logger.warn("The workflow is already cancelled.");
            return true;
        }
        cancelled = true;
        logger.info("Cancelling workflow: {}", wfInstanceId);

        // Cancel all currently running tasks
        // Snapshot then clear runningTasks to avoid ConcurrentModificationException during iteration
        Map<String, Future<?>> snapshot = new HashMap<>(runningTasks);
        runningTasks.clear();
        snapshot.forEach((taskInstanceId, future) -> {
            try {
                if (!future.isDone() && !future.isCancelled()) {
                    Task taskImpl = null;
                    // Retrieve the task implementation and invoke its cancel() method
                    if (future instanceof TaskFutureWrapper) {
                        TaskFutureWrapper<?> taskFuture = (TaskFutureWrapper<?>) future;
                        taskImpl = taskFuture.getTaskImpl();
                        if (taskImpl != null) {
                            logger.debug("Calling cancel() on task implementation for: {}", taskInstanceId);
                            taskImpl.cancel();
                        }
                    }

                    // Only cancel the Future if the task was not already successfully cancelled
                    if (taskImpl == null || !taskImpl.isCanceled()) {
                        future.cancel(true);
                    }


                }
                // Write the terminal RUNNING -> CANCELED transition first, so the child's
                // cancellation callback (notifyParentTaskOnSubWorkflowFailure) sees a
                // terminal status and does not transiently flip the task to ERROR.
                // The child must still be cancelled even if the status write fails.
                try {
                    updateTaskStatus(taskInstanceId, Constants.STATUS_CANCELED, "Workflow cancelled");
                } finally {
                    // Always probe: the sub-workflow may have been started before this
                    // instance registered it in runningWorkflowTaskIds. The helper is a
                    // no-op for non-WORKFLOW tasks and cancels the child (RUNNING -> CANCELED).
                    cancelSubWorkflowIfNeeded(taskInstanceId);
                }
            } catch (Exception e) {
                logger.error("Error canceling task: {}", taskInstanceId, e);
            }
        });
        // Uniformly clean up all sub-workflow tasks that are waiting for callback
        for (String taskInstanceId : new HashSet<>(runningWorkflowTaskIds)) {
            try {
                updateTaskStatus(taskInstanceId, Constants.STATUS_CANCELED, "Workflow cancelled");
            } finally {
                cancelSubWorkflowIfNeeded(taskInstanceId);
            }
        }
        return true;
    }

    /**
     * Retries the task.
     *
     * @param taskInstanceId instance identifier
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean retryTask(String taskInstanceId) {
        if (cancelled || completed) {
            logger.warn("Cannot retry task: {}, the workflow already cancelled or completed", taskInstanceId);
            return false;
        }
        if (runningTasks.containsKey(taskInstanceId) || runningWorkflowTaskIds.contains(taskInstanceId)) {
            logger.warn("Cannot retry task: {} - already running", taskInstanceId);
            return false;
        }

        //if(runningWorkflowTaskIds.contains(taskInstanceId)){
        //        	cancelSubWorkflowIfNeeded(taskInstanceId);
        //		}
        // Reset task status (original status should be ERROR or TIMEOUT)
        updateTaskStatus(taskInstanceId, Constants.STATUS_WAITING, "");
        // Re-submit the task for execution
        Future<?> future = submitTask(taskInstanceId);
        if (future != null) {
            return true;
        }
        return false;
    }

    /**
     * Skips the task.
     *
     * @param taskInstanceId instance identifier
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean skipTask(String taskInstanceId) {
        if (cancelled || completed) {
            logger.warn("Cannot skip task: {}, the workflow already cancelled or completed", taskInstanceId);
            return false;
        }

        // If the task is currently running, cancel it first
        Future<?> future = runningTasks.get(taskInstanceId);
        if (future != null && !future.isDone()) {
            try {
                // First invoke the Task's cancel() method
                if (future instanceof TaskFutureWrapper) {
                    TaskFutureWrapper<?> taskFuture = (TaskFutureWrapper<?>) future;
                    Task taskImpl = taskFuture.getTaskImpl();
                    if (taskImpl != null) {
                        logger.debug("Calling cancel() on task implementation for: {}", taskInstanceId);
                        taskImpl.cancel();
                    }
                }

                // Then cancel the underlying Future
                future.cancel(true);
            } catch (Exception e) {
                logger.error("Error skipping task: {}", taskInstanceId, e);
            }
        }
        // Mark the task as skipped
        updateTaskStatus(taskInstanceId, Constants.STATUS_SKIPPED, "Task skipped");
        // updateTaskStatus() has just removed the id from runningWorkflowTaskIds, so
        // membership can no longer be used as the trigger (it was always false here).
        // Always probe: the helper is a no-op for non-WORKFLOW tasks and for WORKFLOW
        // tasks whose child was never started (no subWorkflowInstanceId persisted yet).
        cancelSubWorkflowIfNeeded(taskInstanceId);

        // Check and submit subsequent tasks that may now be eligible to run
        checkAndSubmitNext();
        return true;
    }

    /**
     * Cancels the task.
     *
     * @param taskInstanceId instance identifier
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean cancelTask(String taskInstanceId) {
        if (cancelled || completed) {
            logger.warn("Cannot cancel task: {}, the workflow already cancelled or completed", taskInstanceId);
            return false;
        }

        Future<?> future = runningTasks.get(taskInstanceId);
        boolean isWaitingCallback = runningWorkflowTaskIds.contains(taskInstanceId);
        if (future == null && !isWaitingCallback) {
            logger.warn("Cannot cancel task: {}, it is not running", taskInstanceId);
            return false;
        }

        // future.isDone() means the task has finished for normal tasks — no need to cancel
        // But for WORKFLOW tasks, execute() returns immediately after triggering the sub-workflow, making future.done,
        // while the task is still RUNNING and the sub-workflow is still executing — so the sub-workflow must be cancelled
        if (future != null && future.isDone()) {
            TaskInstance taskInstance = executionService.getTaskInstance(taskInstanceId);
            if (!Constants.STATUS_RUNNING.equals(taskInstance.getStatus())) {
                logger.warn("Cannot cancel task: {}, it is not running", taskInstanceId);
                return false;
            }
        }

        try {
            if (future != null && !future.isDone()) {
                Task taskImpl = null;
                // First invoke the Task's cancel() method
                if (future instanceof TaskFutureWrapper) {
                    TaskFutureWrapper<?> taskFuture = (TaskFutureWrapper<?>) future;
                    taskImpl = taskFuture.getTaskImpl();
                    if (taskImpl != null) {
                        logger.debug("Calling cancel() on task implementation for: {}", taskInstanceId);
                        taskImpl.cancel();
                    }
                }

                // Then cancel the underlying Future
                if (taskImpl == null || !taskImpl.isCanceled()) {
                    future.cancel(true);
                }
            }

            updateTaskStatus(taskInstanceId, Constants.STATUS_CANCELED, "Task cancelled");
            // WORKFLOW task: the future may already be done (non-blocking execute returned)
            // and the id may not be registered yet, so always probe. No-op for other types.
            cancelSubWorkflowIfNeeded(taskInstanceId);
            return true;
        } catch (Exception e) {
            logger.error("Error canceling task: {}", taskInstanceId, e);
            return false;
        }
    }

    /**
     * If the task is of WORKFLOW type and has already triggered a sub-workflow, cancel the sub-workflow.
     * Called from cancelTask() and skipTask() to prevent an orphaned sub-workflow from completing and calling back into an already-terminated task.
     */
    private void cancelSubWorkflowIfNeeded(String taskInstanceId) {
        try {
            TaskParameter taskParameter = paramsService.getTaskParamsByInstanceId(taskInstanceId);
            if (taskParameter == null || !Constants.TYPE_GROUP_WORKFLOW.equals(taskParameter.getTaskType())) {
                return;
            }
            String subWfInstanceId = ParamUtils.getParameter(
                    JsonUtils.jsonToMap(taskParameter.getPvalue()), Constants.PARAM_TASK_SUBWORKFLOW_INSTANCE, "");
            if (subWfInstanceId.isEmpty()) {
                return;
            }
            WfInstance subWf = workflowEngine.getWfInstanceStatus(subWfInstanceId);
            if (subWf != null &&
                    (Constants.STATUS_RUNNING.equals(subWf.getStatus()) ||
                            Constants.STATUS_WAITING.equals(subWf.getStatus()))) {
                logger.info("Canceling sub-workflow {} due to parent task {} being canceled/skipped",
                        subWfInstanceId, taskInstanceId);
                workflowEngine.cancel(subWfInstanceId);
            }
            // Remove the pending-callback record defensively
            runningWorkflowTaskIds.remove(taskInstanceId);
        } catch (Exception e) {
            logger.error("Failed to cancel sub-workflow for task {}", taskInstanceId, e);
        }
    }

    /**
     * Restore the state.
     */
    public void restoreState() {
        logger.info("Restoring state for workflow: {}", wfInstanceId);
        // Clear the set
        allTaskInstanceIds.clear();
        completedTaskInstanceIds.clear();
        errorTaskInstanceIds.clear();
        runningWorkflowTaskIds.clear();
        allOrTaskInstanceIds.clear();

        List<TaskInstance> tasks = executionService.getTaskInstancesForWorkflow(wfInstanceId);
        for (TaskInstance task : tasks) {
            String taskInstanceId = task.getTaskInstanceId();
            String status = task.getStatus();
            allTaskInstanceIds.add(taskInstanceId);

            TaskParameter taskParam = paramsService.getTaskParamsByInstanceId(taskInstanceId);
            if (taskParam != null && Constants.TYPE_TASK_OR.equals(taskParam.getTaskType()) && taskParam.getEnableFlag().equals(Constants.FLAG_YES)) {
                allOrTaskInstanceIds.add(taskInstanceId);
            }

            if (Constants.STATUS_COMPLETED.equals(status) || Constants.STATUS_SKIPPED.equals(status)) {
                completedTaskInstanceIds.add(taskInstanceId);
            } else if (Constants.STATUS_ERROR.equals(status) || Constants.STATUS_CANCELED.equals(status) || Constants.STATUS_TIMEOUT.equals(status)) {
                errorTaskInstanceIds.add(taskInstanceId);
            } else if (Constants.STATUS_RUNNING.equals(status)) {
                restoreRunningTask(task);
            }
        }

        checkCompletion();
        logger.info("Restored workflow state: {} tasks total, {} completed, {} errors, {} running workflow tasks for {}",
                allTaskInstanceIds.size(), completedTaskInstanceIds.size(), errorTaskInstanceIds.size(),
                runningWorkflowTaskIds.size(), wfInstanceId);
    }

    /**
     * Recover a single task that was in RUNNING state at restart time:
     * - WORKFLOW type with a non-null subWfInstanceId: check actual sub-workflow status and decide
     * - All other cases: mark as ERROR (execution was genuinely interrupted before restart)
     */
    private void restoreRunningTask(TaskInstance task) {
        String taskInstanceId = task.getTaskInstanceId();
        TaskParameter taskParameter = paramsService.getTaskParamsByInstanceId(taskInstanceId);

        if (Constants.TYPE_GROUP_WORKFLOW.equals(taskParameter.getTaskType())) {
            String subWfInstanceId = ParamUtils.getParameter(JsonUtils.jsonToMap(taskParameter.getPvalue()), Constants.PARAM_TASK_SUBWORKFLOW_INSTANCE, "");

            if (!subWfInstanceId.isEmpty()) {
                // Task has already triggered a sub-workflow; decide based on sub-workflow's current status
                recoverWorkflowTask(taskInstanceId, subWfInstanceId);
            } else {
                // WORKFLOW type but subWfInstanceId is null, meaning the task was still in start() phase before restart
                logger.info("Task {} (WORKFLOW) left in RUNNING without subWfInstanceId - marking as ERROR", taskInstanceId);
                updateTaskStatus(taskInstanceId, Constants.STATUS_ERROR, "Task left in RUNNING state");
            }
        } else {
            // Non-WORKFLOW type: execution was genuinely interrupted before restart
            logger.info("Task {} left in RUNNING state - marking as ERROR", taskInstanceId);
            updateTaskStatus(taskInstanceId, Constants.STATUS_ERROR, "Task left in RUNNING state");
        }
    }

    /**
     * Recover a WORKFLOW type task using a three-way decision based on the sub-workflow's current state:
     * 1. Sub-workflow completed  → advance task directly to COMPLETED
     * 2. Sub-workflow failed      → advance task to ERROR
     * 3. Sub-workflow still running → recover sub-workflow, keep task RUNNING, register in runningWorkflowTaskIds for callback
     */
    private void recoverWorkflowTask(String taskInstanceId, String subWfInstanceId) {
        WfInstance subWf = executionService.getWfInstance(subWfInstanceId);
        if (subWf == null) {
            logger.warn("Sub-workflow instance {} not found during recovery for task {}", subWfInstanceId, taskInstanceId);
            updateTaskStatus(taskInstanceId, Constants.STATUS_ERROR,
                    "Sub-workflow instance not found during recovery: " + subWfInstanceId);
            return;
        }

        String subStatus = subWf.getStatus();
        if (Constants.STATUS_COMPLETED.equals(subStatus) || Constants.STATUS_SKIPPED.equals(subStatus)) {
            // Sub-workflow completed before restart; its callback was not processed — advance task now
            logger.info("Sub-workflow {} already completed, advancing task {} during recovery", subWfInstanceId, taskInstanceId);
            //completedTaskInstanceIds.add(taskInstanceId);
            //executionService.updateTaskStatusTransactional(taskInstanceId, Constants.STATUS_COMPLETED, "Recovered: sub-workflow completed");
            updateTaskStatus(taskInstanceId, Constants.STATUS_COMPLETED, "Recovered: sub-workflow completed");

        } else if (Constants.STATUS_ERROR.equals(subStatus) || Constants.STATUS_CANCELED.equals(subStatus)) {
            logger.warn("Sub-workflow {} failed/canceled (status={}), marking task {} as ERROR", subWfInstanceId, subStatus, taskInstanceId);
            updateTaskStatus(taskInstanceId, Constants.STATUS_ERROR, "Recovered: sub-workflow failed with status " + subStatus);

        } else {
            // Sub-workflow is still running (RUNNING/WAITING); recover it and keep task waiting for callback
            logger.info("Recovering workflow task {}, resuming sub-workflow {}", taskInstanceId, subWfInstanceId);
            boolean recovered = workflowEngine.recover(subWfInstanceId);
            if (recovered) {
                registerRecoveredWorkflowTask(taskInstanceId, subWfInstanceId);
            } else {
                // Mark task as ERROR when sub-workflow recovery fails
                logger.error("Failed to recover sub-workflow {} for task {}", subWfInstanceId, taskInstanceId);
                updateTaskStatus(taskInstanceId, Constants.STATUS_ERROR,
                        "Failed to recover sub-workflow: " + subWfInstanceId);
            }
        }
    }

    /**
     * Registers a WORKFLOW task for the sub-workflow callback after {@code recover()} returned true.
     *
     * <p>{@code recover()} may finish the child synchronously (e.g. all its tasks were already
     * terminal). In that case the completion callback runs <em>before</em> this method is called
     * and has already finalized the parent task; registering it again would leave the id in
     * {@code runningWorkflowTaskIds} forever and the workflow could never complete (R1).
     * The check and the registration therefore run under the same per-task lock used by
     * {@link #updateTaskStatus}, and the child status is re-read as the source of truth.
     */
    private void registerRecoveredWorkflowTask(String taskInstanceId, String subWfInstanceId) {
        Object lock = taskInstanceLocks.computeIfAbsent(taskInstanceId, k -> new Object());
        synchronized (lock) {
            // The completion callback may have finalized the task while recover() was running.
            if (completedTaskInstanceIds.contains(taskInstanceId) || errorTaskInstanceIds.contains(taskInstanceId)) {
                logger.info("Task {} was finalized while its sub-workflow was being recovered; not registering a callback",
                        taskInstanceId);
                return;
            }

            // recover() may have completed the child synchronously — re-read rather than trusting
            // the status observed at the beginning of recoverWorkflowTask().
            WfInstance subWf = workflowEngine.getWfInstanceStatus(subWfInstanceId);
            if (subWf == null) {
                updateTaskStatus(taskInstanceId, Constants.STATUS_ERROR,
                        "Sub-workflow instance not found during recovery: " + subWfInstanceId);
                return;
            }
            String subStatus = subWf.getStatus();
            if (Constants.STATUS_COMPLETED.equals(subStatus) || Constants.STATUS_SKIPPED.equals(subStatus)) {
                updateTaskStatus(taskInstanceId, Constants.STATUS_COMPLETED, "Recovered: sub-workflow completed");
            } else if (Constants.STATUS_ERROR.equals(subStatus) || Constants.STATUS_CANCELED.equals(subStatus)) {
                updateTaskStatus(taskInstanceId, Constants.STATUS_ERROR,
                        "Recovered: sub-workflow failed with status " + subStatus);
            } else {
                runningWorkflowTaskIds.add(taskInstanceId);
                logger.info("Task {} waiting for recovered sub-workflow {} callback", taskInstanceId, subWfInstanceId);
            }
        }
    }

    /**
     * Resume.
     */
    public void resume() {
        if (cancelled || completed) return;
        logger.info("Resuming workflow: {}", wfInstanceId);
        // Check and submit all eligible tasks for execution
        checkAndSubmitNext();
    }

    /**
     * Callback entry point invoked by WorkflowEngine.completeWorkflowInternal() when a sub-workflow completes successfully.
     * Advances the RUNNING task to COMPLETED and triggers scheduling of subsequent tasks.
     */
    public void notifySubWorkflowCompleted(String taskInstanceId, String subWfInstanceId) {
        logger.info("Sub-workflow {} completed, resuming task {}", subWfInstanceId, taskInstanceId);
        // updateTaskStatus internally removes the task from runningTasks and adds it to completedTaskInstanceIds
        updateTaskStatus(taskInstanceId, Constants.STATUS_COMPLETED, "Sub-workflow completed: " + subWfInstanceId);
        checkAndSubmitNext();
    }

    /**
     * Callback entry point invoked by WorkflowEngine on the error/cancel path when a sub-workflow fails or is cancelled.
     * Advances the pending task to ERROR and notifies the parent workflow of the failure.
     */
    public void notifySubWorkflowFailed(String taskInstanceId, String subWfInstanceId, String reason) {
        logger.error("Sub-workflow {} failed, marking task {} as ERROR, reason: {}", subWfInstanceId, taskInstanceId, reason);
        updateTaskStatus(taskInstanceId, Constants.STATUS_ERROR, "Sub-workflow failed: " + reason);
        checkAndSubmitNext();
    }

    /**
     * Check and submit the next batch of eligible tasks using fine-grained locking
     */
    /**
     * Check and submit the next batch of eligible tasks for execution
     */
    private void checkAndSubmitNext() {
        if (cancelled || completed) return;

        boolean taskSubmitted = false;
        for (String taskInstanceId : allTaskInstanceIds) {
            // Fast in-memory filter: skip obviously ineligible tasks to reduce unnecessary submitTask calls
            if (completedTaskInstanceIds.contains(taskInstanceId) ||
                    errorTaskInstanceIds.contains(taskInstanceId) ||
                    runningTasks.containsKey(taskInstanceId) ||
                    runningWorkflowTaskIds.contains(taskInstanceId)) {
                continue;
            }
            // Check upstream dependencies before submitting
            if (!canExecute(taskInstanceId)) {
                continue;
            }
            // Submit task; thread-safety is guaranteed by the lock and taskStatus check inside submitTask
            Future<?> future = submitTask(taskInstanceId);
            if (future != null) {
                taskSubmitted = true;
            }
        }

        if (!taskSubmitted) {
            synchronized (submitLock) {
                checkCompletion();
            }
        }
    }

    /**
     * Determines whether a task is eligible to execute based on its upstream dependencies
     */
    private boolean canExecute(String taskInstanceId) {
        List<String> predecessors = dag.getDirectPredecessors(taskInstanceId);
        // No upstream dependencies — task can execute immediately
        if (predecessors.isEmpty()) {
            return true;
        }

        // OR-gate logic: task executes when any one upstream dependency completes
        if (allOrTaskInstanceIds.contains(taskInstanceId)) {
            for (String predId : predecessors) {
                // The OR task is eligible as soon as at least one upstream task is COMPLETED or SKIPPED
                if (completedTaskInstanceIds.contains(predId)) {
                    return true;
                }
            }
            return false;
        }

        // AND logic: all upstream tasks must be COMPLETED or SKIPPED
        for (String predId : predecessors) {
            // If any upstream task has an error, this task cannot execute
            if (errorTaskInstanceIds.contains(predId)) {
                return false;
            }
            // If any upstream task is not yet complete, this task cannot execute
            if (!completedTaskInstanceIds.contains(predId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether the entire workflow has completed (all tasks in terminal states)
     */
    private void checkCompletion() {
        if (cancelled || completed) return;
        if (!isCompleted()) return;
        if (!errorTaskInstanceIds.isEmpty()) return;

        // Only latch 'completed' once the COMPLETED state has actually been persisted.
        // If the DB update fails (or the status was changed concurrently), keeping the flag
        // false lets a later checkCompletion()/recover() retry instead of leaving the
        // workflow wedged with all tasks done but the instance never released.
        if (workflowEngine.completeWorkflow(wfInstanceId)) {
            completed = true;
        } else {
            logger.warn("Failed to persist completion for workflow {}; will retry on the next evaluation", wfInstanceId);
        }
    }

    /**
     * Checks whether all tasks have been processed (reached a terminal state)
     */
    private boolean isCompleted() {
        if (!runningTasks.isEmpty()) return false;
        if (!runningWorkflowTaskIds.isEmpty()) return false;

        for (String taskInstanceId : allTaskInstanceIds) {
            // If a task is not in completedTaskInstanceIds, it is still in a non-terminal state
            if (!completedTaskInstanceIds.contains(taskInstanceId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * execute task
     */
    private Future<?> submitTask(String taskInstanceId) {
        if (cancelled || completed) return null;

        Object lock = taskInstanceLocks.computeIfAbsent(taskInstanceId, k -> new Object());
        try {
            synchronized (lock) {
                if (cancelled || completed) return null;
                TaskInstance task = executionService.getTaskInstance(taskInstanceId);
                String taskStatus = task.getStatus();
                // Check the current task status
                if (!Constants.STATUS_WAITING.equals(taskStatus) && !Constants.STATUS_ERROR.equals(taskStatus) && !Constants.STATUS_TIMEOUT.equals(taskStatus)) {
                    logger.warn("Cannot submit task instance {} whose state is: {}, ", taskInstanceId, taskStatus);
                    return null;
                }
                // record task submission here
                monitor.recordTaskSubmission(wfInstanceId, taskInstanceId);

                TaskParameter taskParam = paramsService.getTaskParamsByInstanceId(taskInstanceId);
                // Createtask instance
                String taskType = taskParam.getTaskType();
                Task taskImpl = taskFactory.createTask(taskType, workflowEngine);
                if (taskImpl == null) {
                    logger.error("Unknown task type: {}", taskType);
                    handleTaskFailure(taskInstanceId, null, "Unknown task type: " + taskType);
                    return null;
                }

                // Create the task execution wrapper (FutureTask)
                Runnable taskRunnable = () -> executeTask(taskImpl, taskInstanceId, taskParam);
                TaskFutureWrapper<Void> futureTask = new TaskFutureWrapper<>(
                        taskRunnable, null, taskImpl, taskInstanceId);

                // Update task instance status to RUNNING
                updateTaskStatus(taskInstanceId, Constants.STATUS_RUNNING, "");
                runningTasks.put(taskInstanceId, futureTask);

                // Submit the wrapper to the thread pool executor
                executorService.execute(futureTask);

                // Register post-completion callback for the task
                CompletableFuture<Void> completableFuture = new CompletableFuture<>();
                CompletableFuture.runAsync(() -> {
                    try {
                        futureTask.get();
                        completableFuture.complete(null);
                    } catch (CancellationException ce) {
                        completableFuture.cancel(true);
                    } catch (Exception e) {
                        completableFuture.completeExceptionally(e);
                    }
                });

                completableFuture.whenComplete((result, ex) -> {
                    if (!cancelled) {
                        checkAndSubmitNext();
                    }
                });


                return futureTask;
            }
        } catch (Exception e) {
            logger.error("Failed to submit task: {}", taskInstanceId, e);
            handleTaskFailure(taskInstanceId, null, "Failed to submit task: " + e.getMessage());
            // Send error notification
            notificationManager.sendTaskNotification(taskInstanceId, Constants.TYPE_TASK_ERROR);
            checkAndSubmitNext();
            return null;
        } finally {
            taskInstanceLocks.remove(taskInstanceId);
        }
    }

    /**
     * The concrete implementation of task execution, including lifecycle and error handling
     */
    private void executeTask(Task task, String taskInstanceId, TaskParameter taskParam) {
        // Parse and merge task parameters
        Map<String, Object> params = JsonUtils.jsonToMap(taskParam.getPvalue());
        try {
            logger.info("Executing task: {}", taskInstanceId);
            // Handle disabled tasks
            if (!Constants.FLAG_YES.equals(taskParam.getEnableFlag())) {
                logger.info("Task {} is disabled, skipping: {}", taskParam.getTaskName(), taskInstanceId);
                //updateTaskStatus(taskInstanceId, Constants.STATUS_SKIPPED, JsonUtils.toJson(params));
                updateTaskStatus(taskInstanceId, Constants.STATUS_SKIPPED, "Task skipped");

                return;
            }

            // 动态调度条件：值为 false 时跳过本次运行，但不影响任务本身"启用"状态, add by Rock at 20260807
            String condition = ParamUtils.getParameter(params, Constants.PARAM_TASK_CONDITION, "");
            if (!condition.trim().isEmpty()) {
                boolean shouldRun;
                try {
                    //shouldRun = ConditionEvaluator.evaluate(condition);
                    // Use the triggered date of top workflow
                    Date referenceDate = getReferenceDateFromParams(params);
                    logger.info("Evaluating condition for task {}: [{}] with reference date {}", taskInstanceId, condition, referenceDate);
                    TaskLogService.addTaskLog(wfInstanceId, taskInstanceId, "Condition evaluation: [" + condition + "] with triggeredDate " + referenceDate);
                    shouldRun = ConditionEvaluator.evaluate(condition, referenceDate);
                } catch (ConditionEvaluator.ConditionEvaluationException e) {
                    // fail-closed：表达式配置错误时不执行任务，而是显式报错，避免"写错了却一直跑"
                    String msg = "Invalid task_condition [" + condition + "]: " + e.getMessage();
                    logger.error("Task {} condition evaluation failed: {}", taskInstanceId, msg);
                    handleTaskFailure(taskInstanceId, taskParam, msg);
                    notificationManager.sendTaskNotification(taskInstanceId, Constants.TYPE_TASK_ERROR);
                    return;
                }
                if (!shouldRun) {
                    logger.info("Task {} condition not satisfied [{}], skipping", taskInstanceId, condition);
                    updateTaskStatus(taskInstanceId, Constants.STATUS_SKIPPED,
                            "Condition not satisfied: " + condition);
                    return;
                }
            }

            // Resolve upstream task parameter references {tasks.{taskId or taskName}.{paramName}} and write back to DB
            params = resolveUpstreamParams(params, taskParam);
            // execute task
            task.execute(params);

            // A task that was cancelled/skipped while executing (workflow-level cancel, single-task
            // cancel, or skip) returns cleanly from AbstractTask.execute() at a lifecycle boundary
            // instead of throwing. cancel/skip has already written CANCELED/SKIPPED to the DB —
            // do NOT overwrite it here with COMPLETED (or with a sub-workflow ERROR/COMPLETED).
            if (task != null && task.isCanceled()) {
                logger.info("Task {} was canceled during execution, keeping CANCELED/SKIPPED status", taskInstanceId);
                return;
            }

            if (Constants.TYPE_GROUP_WORKFLOW.equals(taskParam.getTaskType())) {
                finalizeWorkflowTask(taskInstanceId, taskParam, params);
            } else {
                logger.info("Task completed: {}", taskInstanceId);
                updateTaskStatus(taskInstanceId, Constants.STATUS_COMPLETED, "Task completed");
            }
        } catch (TaskExecutionException e) {
            // Processtask executionerror
            String errorMsg = "Task execution failed: " + e.getMessage();
            logger.error(errorMsg, e);
            if (params.containsKey(Constants.PARAM_TASK_OUTPUT)) {
                errorMsg = "Task execution failed: \n" + params.get(Constants.PARAM_TASK_OUTPUT);
            }
            // Task was cancelled by user
            if (task != null && task.isCanceled()) {
                if (!cancelled) {
                    // Task was cancelled by user, and send task notification here
                    // the status already change to Constants.TYPE_TASK_CANCEL
                    notificationManager.sendTaskNotification(taskInstanceId, Constants.TYPE_TASK_CANCEL);
                } else {
                    // Workflow was cancelled by user, need not do anything here
                    // Workflow-level cancellation does not require sending a per-task cancel notification
                }
            } else {
                // Task encountered an error
                handleTaskFailure(taskInstanceId, taskParam, errorMsg);
                // Send error notification
                notificationManager.sendTaskNotification(taskInstanceId, Constants.TYPE_TASK_ERROR);
            }
        } catch (TaskTimeoutException e) {
            // Handle task timeout error
            String errorMsg = "Task execution timeout: " + e.getMessage();
            logger.error(errorMsg, e);
            handleTaskTimeout(taskInstanceId, taskParam, errorMsg);
            // Send timeout notification
            notificationManager.sendTaskNotification(taskInstanceId, Constants.TYPE_TASK_TIMEOUT);
        } catch (Throwable t) {
            // Handle unexpected error
            String errorMsg = "Unexpected error in task: " + t.getMessage();
            logger.error(errorMsg, t);
            handleTaskFailure(taskInstanceId, taskParam, errorMsg);
            // Send error notification
            notificationManager.sendTaskNotification(taskInstanceId, Constants.TYPE_TASK_ERROR);
        }
    }

    /**
     * Finalizes a WORKFLOW-type task after its (non-blocking) {@code execute()} returned.
     *
     * <p>The whole decision runs under the task's per-task lock — the same lock taken by
     * {@link #updateTaskStatus} and therefore by the sub-workflow completion callback
     * ({@link #notifySubWorkflowCompleted}). Without it, a callback that fires between the
     * sub-workflow status read and {@code runningWorkflowTaskIds.add()} can remove the id
     * before it is added, leaving the workflow permanently "waiting for a callback" that
     * has already arrived.
     *
     * <p>Entering the lock also re-checks the in-memory terminal sets: if a callback,
     * cancel or skip already finalized this task, registering it again would wedge the
     * workflow (isCompleted() would never become true).
     */
    private void finalizeWorkflowTask(String taskInstanceId, TaskParameter taskParam, Map<String, Object> params) {
        Object lock = taskInstanceLocks.computeIfAbsent(taskInstanceId, k -> new Object());
        synchronized (lock) {
            if (cancelled) {
                logger.info("Workflow {} was cancelled while finalizing task {}; not registering a callback",
                        wfInstanceId, taskInstanceId);
                return;
            }
            if (completedTaskInstanceIds.contains(taskInstanceId) || errorTaskInstanceIds.contains(taskInstanceId)) {
                logger.info("Task {} already reached a terminal state while finalizing; not registering a callback",
                        taskInstanceId);
                return;
            }

            // Read the subWfInstanceId that was written by the task's action() method
            String subWfInstanceId = ParamUtils.getParameter(params, Constants.PARAM_TASK_SUBWORKFLOW_INSTANCE, "");
            if (subWfInstanceId.isEmpty()) {
                // Sub-workflow failed to start
                handleTaskFailure(taskInstanceId, taskParam, "Sub-workflow start failed: no instance id returned");
                return;
            }

            // A sub-workflow that has already reached a terminal state will never call back —
            // finalize this task now instead of registering it for a callback that never arrives.
            // This mirrors the three-way decision in recoverWorkflowTask() so the live path and
            // restart-recovery behave identically (COMPLETED/SKIPPED -> COMPLETED, ERROR/CANCELED -> ERROR).
            WfInstance subWf = workflowEngine.getWfInstanceStatus(subWfInstanceId);
            if (subWf == null) {
                handleTaskFailure(taskInstanceId, taskParam, "Sub-workflow instance not found: " + subWfInstanceId);
            } else if (Constants.STATUS_COMPLETED.equals(subWf.getStatus()) || Constants.STATUS_SKIPPED.equals(subWf.getStatus())) {
                TaskLogService.addTaskLog(wfInstanceId, taskInstanceId,
                        "Sub-workflow " + subWfInstanceId + " already " + subWf.getStatus() + ", completing task");
                logger.warn("Task {}: sub-workflow {} is already {} (no callback will arrive), completing task",
                        taskInstanceId, subWfInstanceId, subWf.getStatus());
                updateTaskStatus(taskInstanceId, Constants.STATUS_COMPLETED,
                        "Sub-workflow " + subWf.getStatus() + ": " + subWfInstanceId);
            } else if (Constants.STATUS_ERROR.equals(subWf.getStatus()) || Constants.STATUS_CANCELED.equals(subWf.getStatus())) {
                TaskLogService.addTaskLog(wfInstanceId, taskInstanceId,
                        "Sub-workflow " + subWfInstanceId + " ended with " + subWf.getStatus() + ", marking task ERROR");
                logger.warn("Task {}: sub-workflow {} ended with {}, marking task ERROR",
                        taskInstanceId, subWfInstanceId, subWf.getStatus());
                updateTaskStatus(taskInstanceId, Constants.STATUS_ERROR,
                        "Sub-workflow " + subWf.getStatus() + ": " + subWfInstanceId);
            } else {
                // Stay RUNNING and wait for the callback. Registered under the per-task lock,
                // so notifySubWorkflowCompleted() cannot remove it before this point.
                runningWorkflowTaskIds.add(taskInstanceId);
                TaskLogService.addTaskLog(wfInstanceId, taskInstanceId, "Sub-workflow started: " + subWfInstanceId + ", waiting for callback");
                logger.info("Task {} waiting for sub-workflow {} callback", taskInstanceId, subWfInstanceId);
            }
        }
    }

    /**
     * Resolves {tasks.{identifier}.{paramName}} placeholders in the params map by reading
     * completed upstream task parameters from the database.
     * {identifier} is matched first by taskId, then by taskName (first match wins).
     * Only direct predecessors declared in PREDECESSORS may be referenced.
     * After substitution the resolved map is written back to TASK_PARAMS.
     */
    private Map<String, Object> resolveUpstreamParams(Map<String, Object> params, TaskParameter taskParam) {
        boolean hasUpstreamRef = params.values().stream()
                .anyMatch(v -> v instanceof String && ((String) v).contains(UPSTREAM_PARAM_PREFIX));
        if (!hasUpstreamRef) {
            return params;
        }

        String taskId = taskParam.getTaskId();
        String taskInstanceId = taskParam.getTaskInstanceId();

        // Build predecessor taskId set from PREDECESSORS field
        Set<String> predecessorTaskIds = new HashSet<>();
        String predecessors = taskParam.getPredecessors();
        if (predecessors != null && !predecessors.trim().isEmpty()) {
            for (String pid : predecessors.split(",")) {
                String trimmed = pid.trim();
                if (!trimmed.isEmpty()) {
                    predecessorTaskIds.add(trimmed);
                }
            }
        }

        // Targeted DB query: load all direct predecessor TASK_PARAMS records at once.
        // predecessors are typically 1-3 tasks, so this is a small, bounded fetch.
        List<TaskParameter> upstreamTaskParams = paramsService.getTaskParamsByWfInstanceIdAndTaskIds(
                wfInstanceId, new ArrayList<>(predecessorTaskIds));

        // Build two lookup maps so {tasks.xxx.yyy} can match by taskId (primary) or taskName (fallback)
        Map<String, Map<String, Object>> byTaskId   = new HashMap<>();
        Map<String, Map<String, Object>> byTaskName = new HashMap<>();
        for (TaskParameter tp : upstreamTaskParams) {
            Map<String, Object> tpParams = JsonUtils.jsonToMap(tp.getPvalue());
            if (tp.getTaskId() != null) {
                byTaskId.put(tp.getTaskId(), tpParams);
            }
            if (tp.getTaskName() != null) {
                byTaskName.put(tp.getTaskName(), tpParams);
            }
        }

        // Resolve all placeholders: taskId lookup first, taskName fallback
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!(entry.getValue() instanceof String)) continue;
            String value = (String) entry.getValue();
            if (!value.contains(UPSTREAM_PARAM_PREFIX)) continue;

            Matcher matcher = UPSTREAM_PARAM_PATTERN.matcher(value);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String refIdentifier = matcher.group(1);
                String refParamName  = matcher.group(2);
                String placeholder   = matcher.group(0);

                Map<String, Object> upstreamParams = byTaskId.get(refIdentifier);
                if (upstreamParams == null) {
                    upstreamParams = byTaskName.get(refIdentifier);
                }
                if (upstreamParams == null) {
                    String msg = "Missing required parameter: " + placeholder + " references unknown or non-predecessor task " + refIdentifier;
                    TaskLogService.addTaskLog(wfInstanceId, taskInstanceId, msg);
                    logger.error("[{}] {}", taskInstanceId, msg);
                    throw new TaskExecutionException(msg, null, taskId, taskInstanceId);
                }
                if (!upstreamParams.containsKey(refParamName)) {
                    String msg = "Missing required parameter: " + placeholder + " not found in upstream task output";
                    TaskLogService.addTaskLog(wfInstanceId, taskInstanceId, msg);
                    logger.error("[{}] {}", taskInstanceId, msg);
                    throw new TaskExecutionException(msg, null, taskId, taskInstanceId);
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(upstreamParams.get(refParamName))));
            }
            matcher.appendTail(sb);
            entry.setValue(sb.toString());
        }

        // Write resolved params back so the DB reflects the actual values used at runtime
        paramsService.saveTaskParameters(taskInstanceId, params);
        return params;
    }

    /**
     * Common logic for handling task failure
     */
    private void handleTaskFailure(String taskInstanceId, TaskParameter taskParam, String errorMessage) {
        updateTaskStatus(taskInstanceId, Constants.STATUS_ERROR, errorMessage);
    }

    /**
     * Common logic for handling task timeout
     */
    private void handleTaskTimeout(String taskInstanceId, TaskParameter taskParam, String errorMessage) {
        updateTaskStatus(taskInstanceId, Constants.STATUS_TIMEOUT, errorMessage);
    }

    /**
     * Updates task instance status with transactional support
     */
    private void updateTaskStatus(String taskInstanceId, String status, String message) {
        Object lock = taskInstanceLocks.computeIfAbsent(taskInstanceId, k -> new Object());
        synchronized (lock) {
            try {
                // Use the transactional method to update task status
                executionService.updateTaskStatusTransactional(taskInstanceId, status, message);
                // Update in-memory status map
                if (Constants.STATUS_COMPLETED.equals(status) || Constants.STATUS_SKIPPED.equals(status)) {
                    completedTaskInstanceIds.add(taskInstanceId);
                    errorTaskInstanceIds.remove(taskInstanceId);
                    runningTasks.remove(taskInstanceId);
                    runningWorkflowTaskIds.remove(taskInstanceId);
                    monitor.recordTaskCompletion(wfInstanceId, taskInstanceId, true);
                } else if (Constants.STATUS_ERROR.equals(status) || Constants.STATUS_CANCELED.equals(status) ||
                        Constants.STATUS_TIMEOUT.equals(status)) {
                    errorTaskInstanceIds.add(taskInstanceId);
                    completedTaskInstanceIds.remove(taskInstanceId);
                    runningTasks.remove(taskInstanceId);
                    runningWorkflowTaskIds.remove(taskInstanceId);
                    monitor.recordTaskCompletion(wfInstanceId, taskInstanceId, false);
                } else if (Constants.STATUS_RUNNING.equals(status)) {
                    errorTaskInstanceIds.remove(taskInstanceId);
                    completedTaskInstanceIds.remove(taskInstanceId);
                    // Do not modify runningTasks or runningWorkflowTaskIds here; submitTask handles them during submission
                } else if (Constants.STATUS_WAITING.equals(status)) {
                    errorTaskInstanceIds.remove(taskInstanceId);
                    completedTaskInstanceIds.remove(taskInstanceId);
                    runningTasks.remove(taskInstanceId);
                    runningWorkflowTaskIds.remove(taskInstanceId);
                }
            } finally {
                // Only release the per-task lock entry for terminal statuses.
                // For intermediate transitions (RUNNING, WAITING), the lock is kept alive
                // until submitTask's outer finally removes it after the synchronized block exits,
                // preventing a race window where concurrent callers could bypass lock protection.
                if (isTerminalTaskStatus(status)) {
                    taskInstanceLocks.remove(taskInstanceId);
                }
                TaskLogService.addTaskLog(this.wfInstanceId, taskInstanceId, "Status to " + status);
            }
        }
    }

    private boolean isTerminalTaskStatus(String status) {
        return Constants.STATUS_COMPLETED.equals(status) || Constants.STATUS_SKIPPED.equals(status)
                || Constants.STATUS_ERROR.equals(status)    || Constants.STATUS_CANCELED.equals(status)
                || Constants.STATUS_TIMEOUT.equals(status);
    }

    private Date getReferenceDateFromParams(Map<String, Object> params) {
        Object obj = params.get(Constants.PARAM_WORKFLOW_TRIGGERDATE);
        if (obj instanceof Date) {
            return (Date) obj;
        }
        if (obj instanceof Long) {
            return new Date((Long) obj);
        }
        throw new IllegalStateException("Missing or invalid triggeredDate in task parameters");
    }

    /**
     * Returns the workflow definition ID
     *
     * @return the workflow definition ID
     */
    public String getWorkflowId() {
        return workflowId;
    }

    /**
     * Returns the workflow name.
     *
     * @return the result as a String
     */
    public String getWorkflowName() {
        return workflowName;
    }

    /**
     * Returns the merged parameter map used by this workflow instance,
     * which includes global, workflow-definition, and instance-level overrides.
     *
     * @return an unmodifiable view of the parameter map; never {@code null}
     */
    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * Returns the workflow instance ID
     *
     * @return workflow instanceID
     */
    public String getWfInstanceId() {
        return wfInstanceId;
    }

    /**
     * Returns whether this instance has reached a terminal lifecycle state in memory
     * (completion persisted, or cancellation started). A finished instance must never
     * be kept in the engine's runningWorkflows map: it would make isRunningWorkflow()
     * permanently true and silently block future cron/manual/sub-workflow starts.
     *
     * @return {@code true} if the instance is completed or cancelled
     */
    public boolean isFinished() {
        return completed || cancelled;
    }

    /**
     * Returns whether the workflow reached (and persisted) a successful completion.
     * Unlike {@link #isFinished()}, this excludes cancellation, which is transiently
     * true while {@code cancelWorkflowInternal()} is still finalizing the instance.
     *
     * @return {@code true} if completion has been persisted
     */
    public boolean isCompletionPersisted() {
        return completed;
    }

    /**
     * Returns the map of currently running task futures
     *
     * @return map of task instance ID to running FutureTask
     */
    public Map<String, Future<?>> getRunningTasks() {
        return Collections.unmodifiableMap(runningTasks);
    }

    /**
     * Returns the set of all task instance IDs in this workflow
     *
     * @return set of all task instance IDs
     */
    public Set<String> getAllTaskInstanceIds() {
        return Collections.unmodifiableSet(allTaskInstanceIds);
    }

    /**
     * Returns the set of task instance IDs that have reached a terminal state
     *
     * @return set of completed (terminal-state) task instance IDs
     */
    public Set<String> getCompletedTaskInstanceIds() {
        return Collections.unmodifiableSet(completedTaskInstanceIds);
    }

    /**
     * Returns the set of task instance IDs that ended in an error state
     *
     * @return set of errored task instance IDs
     */
    public Set<String> getErrorTaskInstanceIds() {
        return Collections.unmodifiableSet(errorTaskInstanceIds);
    }

    // Returns the set of WORKFLOW task instance IDs that are waiting for sub-workflow callback

    /**
     * Returns the running workflow task ids.
     *
     * @return the result as a String
     */
    public Set<String> getRunningWorkflowTaskIds() {
        return Collections.unmodifiableSet(runningWorkflowTaskIds);
    }
}