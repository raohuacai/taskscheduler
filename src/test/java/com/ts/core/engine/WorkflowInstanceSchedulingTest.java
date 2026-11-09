package com.ts.core.engine;

import com.ts.common.constants.Constants;
import com.ts.core.dag.WorkflowDAG;
import com.ts.monitor.WorkflowMonitor;
import com.ts.model.entity.TaskInstance;
import com.ts.model.entity.TaskParameter;
import com.ts.model.entity.WfInstance;
import com.ts.service.ExecutionService;
import com.ts.service.ParamsService;
import com.ts.service.impl.TaskLogService;
import com.ts.task.Task;
import com.ts.task.TaskFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the core scheduling logic of {@link WorkflowInstance}:
 * dependency gating (AND / OR), completion detection, and the
 * retry/skip/cancel operations.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowInstanceSchedulingTest {

    private static final String WF_INSTANCE_ID = "wf-inst-1";

    @Mock
    private WorkflowEngine workflowEngine;
    @Mock
    private ExecutionService executionService;
    @Mock
    private ParamsService paramsService;
    @Mock
    private TaskFactory taskFactory;
    @Mock
    private NotificationManager notificationManager;
    @Mock
    private ThreadPoolExecutor executorService;
    @Mock
    private WorkflowMonitor monitor;
    @Mock
    private WorkflowDAG dag;

    private MockedStatic<TaskLogService> taskLogMock;
    private WorkflowInstance instance;

    @BeforeEach
    void setUp() {
        taskLogMock = mockStatic(TaskLogService.class);
        WfInstance wfInstance = new WfInstance();
        wfInstance.setWfInstanceId(WF_INSTANCE_ID);
        wfInstance.setWorkflowId("WF1");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(Constants.PARAM_WORKFLOW_NAME, "WF");
        when(executionService.getTaskInstancesForWorkflow(WF_INSTANCE_ID))
                .thenReturn(Collections.emptyList());
        instance = new WorkflowInstance(
                workflowEngine, wfInstance, parameters, dag,
                executionService, paramsService, taskFactory,
                notificationManager, executorService, monitor);
    }

    @AfterEach
    void tearDown() {
        taskLogMock.close();
    }

    // ---------- reflection helpers ----------

    @SuppressWarnings("unchecked")
    private Set<String> fieldSet(String name) throws Exception {
        Field f = WorkflowInstance.class.getDeclaredField(name);
        f.setAccessible(true);
        return (Set<String>) f.get(instance);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Future<?>> fieldRunningTasks() throws Exception {
        Field f = WorkflowInstance.class.getDeclaredField("runningTasks");
        f.setAccessible(true);
        return (Map<String, Future<?>>) f.get(instance);
    }

    private void setFlag(String name, boolean value) throws Exception {
        Field f = WorkflowInstance.class.getDeclaredField(name);
        f.setAccessible(true);
        f.setBoolean(instance, value);
    }

    private boolean getFlag(String name) throws Exception {
        Field f = WorkflowInstance.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.getBoolean(instance);
    }

    private void invokeCheckCompletion() throws Exception {
        Method m = WorkflowInstance.class.getDeclaredMethod("checkCompletion");
        m.setAccessible(true);
        m.invoke(instance);
    }

    private TaskParameter workflowTaskParam(String subWfInstanceId) {
        TaskParameter tp = new TaskParameter();
        tp.setTaskType(Constants.TYPE_GROUP_WORKFLOW);
        tp.setPvalue("{\"subWorkflowInstanceId\":\"" + subWfInstanceId + "\"}");
        return tp;
    }

    private void invokeRecoverWorkflowTask(String taskInstanceId, String subWfInstanceId) throws Exception {
        Method m = WorkflowInstance.class.getDeclaredMethod("recoverWorkflowTask", String.class, String.class);
        m.setAccessible(true);
        m.invoke(instance, taskInstanceId, subWfInstanceId);
    }

    private boolean invokeCanExecute(String taskInstanceId) throws Exception {
        Method m = WorkflowInstance.class.getDeclaredMethod("canExecute", String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(instance, taskInstanceId);
    }

    private boolean invokeIsCompleted() throws Exception {
        Method m = WorkflowInstance.class.getDeclaredMethod("isCompleted");
        m.setAccessible(true);
        return (boolean) m.invoke(instance);
    }

    // ==================== canExecute — no predecessors ====================

    @Test
    void canExecute_noPredecessors_returnsTrue() throws Exception {
        when(dag.getDirectPredecessors("t1")).thenReturn(Collections.emptyList());
        assertTrue(invokeCanExecute("t1"));
    }

    // ==================== canExecute — AND semantics ====================

    @Test
    void canExecute_and_allCompleted_returnsTrue() throws Exception {
        when(dag.getDirectPredecessors("t3")).thenReturn(List.of("t1", "t2"));
        fieldSet("completedTaskInstanceIds").addAll(Set.of("t1", "t2"));

        assertTrue(invokeCanExecute("t3"));
    }

    @Test
    void canExecute_and_pendingPredecessor_returnsFalse() throws Exception {
        when(dag.getDirectPredecessors("t3")).thenReturn(List.of("t1", "t2"));
        fieldSet("completedTaskInstanceIds").add("t1");   // t2 still pending

        assertFalse(invokeCanExecute("t3"));
    }

    @Test
    void canExecute_and_errorPredecessor_returnsFalse() throws Exception {
        when(dag.getDirectPredecessors("t3")).thenReturn(List.of("t1", "t2"));
        fieldSet("completedTaskInstanceIds").addAll(Set.of("t1", "t2"));
        fieldSet("errorTaskInstanceIds").add("t1");

        assertFalse(invokeCanExecute("t3"));
    }

    // ==================== canExecute — OR semantics ====================

    @Test
    void canExecute_or_anyCompleted_returnsTrue() throws Exception {
        when(dag.getDirectPredecessors("or-task")).thenReturn(List.of("p1", "p2"));
        fieldSet("allOrTaskInstanceIds").add("or-task");
        fieldSet("completedTaskInstanceIds").add("p1");   // p2 still pending

        assertTrue(invokeCanExecute("or-task"));
    }

    @Test
    void canExecute_or_noneCompleted_returnsFalse() throws Exception {
        when(dag.getDirectPredecessors("or-task")).thenReturn(List.of("p1", "p2"));
        fieldSet("allOrTaskInstanceIds").add("or-task");

        assertFalse(invokeCanExecute("or-task"));
    }

    // ==================== isCompleted ====================

    @Test
    void isCompleted_noTasks_returnsTrue() throws Exception {
        assertTrue(invokeIsCompleted());
    }

    @Test
    void isCompleted_allTerminal_returnsTrue() throws Exception {
        fieldSet("allTaskInstanceIds").addAll(Set.of("t1", "t2"));
        fieldSet("completedTaskInstanceIds").addAll(Set.of("t1", "t2"));

        assertTrue(invokeIsCompleted());
    }

    @Test
    void isCompleted_pendingTask_returnsFalse() throws Exception {
        fieldSet("allTaskInstanceIds").addAll(Set.of("t1", "t2"));
        fieldSet("completedTaskInstanceIds").add("t1");

        assertFalse(invokeIsCompleted());
    }

    @Test
    void isCompleted_runningTask_returnsFalse() throws Exception {
        fieldSet("allTaskInstanceIds").add("t1");
        fieldSet("completedTaskInstanceIds").add("t1");
        fieldRunningTasks().put("t1", mock(Future.class));

        assertFalse(invokeIsCompleted());
    }

    @Test
    void isCompleted_pendingWorkflowCallback_returnsFalse() throws Exception {
        fieldSet("allTaskInstanceIds").add("t1");
        fieldSet("completedTaskInstanceIds").add("t1");
        fieldSet("runningWorkflowTaskIds").add("t1");

        assertFalse(invokeIsCompleted());
    }

    // ==================== retryTask ====================

    @Test
    void retryTask_notRunning_submitsTask() {
        TaskInstance ti = new TaskInstance();
        ti.setTaskInstanceId("task-x");
        ti.setStatus(Constants.STATUS_ERROR);
        when(executionService.getTaskInstance("task-x")).thenReturn(ti);
        TaskParameter tp = mock(TaskParameter.class);
        // executeTask runs asynchronously on the (mocked) executor and never executes here,
        // so only the fields consumed synchronously by submitTask are stubbed.
        when(tp.getTaskType()).thenReturn("SHELL");
        when(paramsService.getTaskParamsByInstanceId("task-x")).thenReturn(tp);
        when(taskFactory.createTask(anyString(), any())).thenReturn(mock(Task.class));

        boolean ok = instance.retryTask("task-x");

        assertTrue(ok);
        // first transition back to WAITING, then to RUNNING inside submitTask
        verify(executionService).updateTaskStatusTransactional("task-x", Constants.STATUS_WAITING, "");
        verify(executionService).updateTaskStatusTransactional("task-x", Constants.STATUS_RUNNING, "");
    }

    @Test
    void retryTask_alreadyRunning_returnsFalse() throws Exception {
        fieldRunningTasks().put("task-x", mock(Future.class));

        assertFalse(instance.retryTask("task-x"));
        verify(executionService, never()).updateTaskStatusTransactional(anyString(), anyString(), anyString());
    }

    @Test
    void retryTask_cancelledWorkflow_returnsFalse() throws Exception {
        setFlag("cancelled", true);

        assertFalse(instance.retryTask("task-x"));
    }

    // ==================== skipTask ====================

    @Test
    void skipTask_marksSkipped() {
        assertTrue(instance.skipTask("task-x"));
        verify(executionService).updateTaskStatusTransactional("task-x", Constants.STATUS_SKIPPED, "Task skipped");
    }

    @Test
    void skipTask_cancelledWorkflow_returnsFalse() throws Exception {
        setFlag("cancelled", true);
        assertFalse(instance.skipTask("task-x"));
    }

    // ==================== cancelTask ====================

    @Test
    void cancelTask_notRunning_returnsFalse() {
        assertFalse(instance.cancelTask("task-x"));
    }

    @Test
    void cancelTask_running_marksCancelled() throws Exception {
        Future<?> future = mock(Future.class);
        when(future.isDone()).thenReturn(false);
        fieldRunningTasks().put("task-x", future);

        assertTrue(instance.cancelTask("task-x"));
        verify(executionService).updateTaskStatusTransactional("task-x", Constants.STATUS_CANCELED, "Task cancelled");
    }

    // ==================== cancel ====================

    @Test
    void cancel_returnsTrueAndSetsFlag() throws Exception {
        assertTrue(instance.cancel());
        Field f = WorkflowInstance.class.getDeclaredField("cancelled");
        f.setAccessible(true);
        assertTrue(f.getBoolean(instance));
    }

    // ==================== F1: sub-workflow cancellation on skip / cancel ====================

    /**
     * Regression for F1: updateTaskStatus() removes the id from runningWorkflowTaskIds,
     * so the old "if (contains) cancelSubWorkflowIfNeeded()" check could never fire.
     * Skipping a WORKFLOW task must cancel its running child.
     */
    @Test
    void skipTask_workflowTask_cancelsSubWorkflow() throws Exception {
        fieldSet("runningWorkflowTaskIds").add("task-x");
        when(paramsService.getTaskParamsByInstanceId("task-x")).thenReturn(workflowTaskParam("sub-1"));
        WfInstance sub = new WfInstance();
        sub.setStatus(Constants.STATUS_RUNNING);
        when(workflowEngine.getWfInstanceStatus("sub-1")).thenReturn(sub);

        assertTrue(instance.skipTask("task-x"));

        verify(workflowEngine).cancel("sub-1");
    }

    /**
     * The child may have been started even though the task was never registered in
     * runningWorkflowTaskIds (the registration race). cancelTask() must still cancel it.
     */
    @Test
    void cancelTask_workflowTaskNotRegisteredButChildRunning_cancelsSubWorkflow() throws Exception {
        Future<?> future = mock(Future.class);
        when(future.isDone()).thenReturn(true);
        fieldRunningTasks().put("task-x", future);

        TaskInstance ti = new TaskInstance();
        ti.setStatus(Constants.STATUS_RUNNING);
        when(executionService.getTaskInstance("task-x")).thenReturn(ti);
        when(paramsService.getTaskParamsByInstanceId("task-x")).thenReturn(workflowTaskParam("sub-1"));
        WfInstance sub = new WfInstance();
        sub.setStatus(Constants.STATUS_RUNNING);
        when(workflowEngine.getWfInstanceStatus("sub-1")).thenReturn(sub);

        assertTrue(instance.cancelTask("task-x"));

        verify(workflowEngine).cancel("sub-1");
    }

    // ==================== F3: completion only latches after it is persisted ====================

    @Test
    void checkCompletion_completeFails_keepsRetrying() throws Exception {
        fieldSet("allTaskInstanceIds").add("t1");
        fieldSet("completedTaskInstanceIds").add("t1");
        when(workflowEngine.completeWorkflow(WF_INSTANCE_ID)).thenReturn(false, true);

        invokeCheckCompletion();
        assertFalse(getFlag("completed"), "a failed DB completion must not latch the in-memory flag");

        invokeCheckCompletion();
        assertTrue(getFlag("completed"));
        verify(workflowEngine, times(2)).completeWorkflow(WF_INSTANCE_ID);
    }

    @Test
    void cancel_whenCompleted_returnsFalseWithoutTouchingTasks() throws Exception {
        setFlag("completed", true);

        assertFalse(instance.cancel(), "a completed workflow must not be reported as successfully cancelled");
        verify(executionService, never()).updateTaskStatusTransactional(anyString(), anyString(), anyString());
        assertFalse(getFlag("cancelled"));
    }

    @Test
    void isFinished_reflectsCompletionAndCancellation() throws Exception {
        assertFalse(instance.isFinished());

        setFlag("completed", true);
        assertTrue(instance.isFinished());

        setFlag("completed", false);
        setFlag("cancelled", true);
        assertTrue(instance.isFinished());
    }

    // ==================== cancel ordering (P0) ====================

    /**
     * The parent task must be written CANCELED BEFORE the child is cancelled: otherwise the
     * child's failure callback (which only acts while the parent task is RUNNING) would
     * transiently set the task to ERROR first.
     */
    @Test
    void cancel_writesTaskStatusBeforeCancellingSubWorkflow() throws Exception {
        Future<?> future = mock(Future.class);
        when(future.isDone()).thenReturn(false);
        when(future.isCancelled()).thenReturn(false);
        fieldRunningTasks().put("task-x", future);
        when(paramsService.getTaskParamsByInstanceId("task-x")).thenReturn(workflowTaskParam("sub-1"));
        WfInstance sub = new WfInstance();
        sub.setStatus(Constants.STATUS_RUNNING);
        when(workflowEngine.getWfInstanceStatus("sub-1")).thenReturn(sub);

        assertTrue(instance.cancel());

        InOrder inOrder = inOrder(executionService, workflowEngine);
        inOrder.verify(executionService).updateTaskStatusTransactional(
                "task-x", Constants.STATUS_CANCELED, "Workflow cancelled");
        inOrder.verify(workflowEngine).cancel("sub-1");
    }

    /**
     * The child must still be cancelled when the parent task status write fails — that is
     * the reason the status write and the child cancellation are wrapped in try/finally.
     */
    @Test
    void cancel_cancelsSubWorkflowEvenWhenStatusWriteFails() throws Exception {
        Future<?> future = mock(Future.class);
        when(future.isDone()).thenReturn(false);
        when(future.isCancelled()).thenReturn(false);
        fieldRunningTasks().put("task-x", future);
        when(paramsService.getTaskParamsByInstanceId("task-x")).thenReturn(workflowTaskParam("sub-1"));
        WfInstance sub = new WfInstance();
        sub.setStatus(Constants.STATUS_RUNNING);
        when(workflowEngine.getWfInstanceStatus("sub-1")).thenReturn(sub);
        when(executionService.updateTaskStatusTransactional(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("db down"));

        assertTrue(instance.cancel());

        verify(executionService).updateTaskStatusTransactional(
                "task-x", Constants.STATUS_CANCELED, "Workflow cancelled");
        verify(workflowEngine).cancel("sub-1");
    }

    /**
     * Second cancel loop: a WORKFLOW task waiting for a callback (not in runningTasks) must
     * also be written CANCELED before its child is cancelled.
     */
    @Test
    void cancel_waitingCallbackTask_writesStatusThenCancelsChild() throws Exception {
        fieldSet("runningWorkflowTaskIds").add("task-x");
        when(paramsService.getTaskParamsByInstanceId("task-x")).thenReturn(workflowTaskParam("sub-1"));
        WfInstance sub = new WfInstance();
        sub.setStatus(Constants.STATUS_RUNNING);
        when(workflowEngine.getWfInstanceStatus("sub-1")).thenReturn(sub);

        assertTrue(instance.cancel());

        InOrder inOrder = inOrder(executionService, workflowEngine);
        inOrder.verify(executionService).updateTaskStatusTransactional(
                "task-x", Constants.STATUS_CANCELED, "Workflow cancelled");
        inOrder.verify(workflowEngine).cancel("sub-1");
        assertFalse(fieldSet("runningWorkflowTaskIds").contains("task-x"));
    }

    // ==================== F3 self-heal via resume (P0) ====================

    @Test
    void resume_retriesPreviouslyFailedCompletion() throws Exception {
        fieldSet("allTaskInstanceIds").add("t1");
        fieldSet("completedTaskInstanceIds").add("t1");
        when(workflowEngine.completeWorkflow(WF_INSTANCE_ID)).thenReturn(true);

        instance.resume();

        assertTrue(getFlag("completed"), "resume() must retry a completion that previously failed");
        verify(workflowEngine).completeWorkflow(WF_INSTANCE_ID);
    }

    // ==================== P1 boundary cases ====================

    @Test
    void cancel_alreadyCancelled_returnsTrueIdempotently() throws Exception {
        setFlag("cancelled", true);

        assertTrue(instance.cancel());

        verify(executionService, never()).updateTaskStatusTransactional(anyString(), anyString(), anyString());
    }

    @Test
    void skipTask_nonWorkflowTask_doesNotTouchSubWorkflow() {
        TaskParameter tp = new TaskParameter();
        tp.setTaskType("SHELL");
        tp.setPvalue("{}");
        when(paramsService.getTaskParamsByInstanceId("task-x")).thenReturn(tp);

        assertTrue(instance.skipTask("task-x"));

        verify(workflowEngine, never()).getWfInstanceStatus(anyString());
        verify(workflowEngine, never()).cancel(anyString());
    }

    @Test
    void checkCompletion_withErrorTask_doesNotComplete() throws Exception {
        fieldSet("allTaskInstanceIds").addAll(Set.of("t1", "t2"));
        fieldSet("completedTaskInstanceIds").addAll(Set.of("t1", "t2"));
        fieldSet("errorTaskInstanceIds").add("t1");

        invokeCheckCompletion();

        assertFalse(getFlag("completed"));
        verify(workflowEngine, never()).completeWorkflow(anyString());
    }

    // ==================== R1: recovery-path callback registration race ====================

    /**
     * R1 regression: the child may finish DURING workflowEngine.recover() and its completion
     * callback already finalized the parent task. recoverWorkflowTask() must not register the
     * task again — the id would stay in runningWorkflowTaskIds forever and isCompleted()
     * would never become true.
     */
    @Test
    void recoverWorkflowTask_callbackFiresDuringRecover_doesNotRegisterStaleCallback() throws Exception {
        WfInstance subRunning = new WfInstance();
        subRunning.setStatus(Constants.STATUS_RUNNING);
        when(executionService.getWfInstance("sub-1")).thenReturn(subRunning);
        when(workflowEngine.recover("sub-1")).thenAnswer(inv -> {
            // The child completes while recover() is running; the callback finalizes the task.
            instance.notifySubWorkflowCompleted("task-x", "sub-1");
            return true;
        });

        invokeRecoverWorkflowTask("task-x", "sub-1");

        assertTrue(fieldSet("completedTaskInstanceIds").contains("task-x"));
        assertFalse(fieldSet("runningWorkflowTaskIds").contains("task-x"),
                "R1: a task whose callback already fired must not be registered again");
    }

    /**
     * R1 regression (DB-only callback path): the child status may change while recover() runs
     * without touching this instance's in-memory sets. The re-read of the child status must
     * finalize the task instead of registering it.
     */
    @Test
    void recoverWorkflowTask_childCompletesDuringRecover_finalizesWithoutRegistering() throws Exception {
        WfInstance subRunning = new WfInstance();
        subRunning.setStatus(Constants.STATUS_RUNNING);
        WfInstance subCompleted = new WfInstance();
        subCompleted.setStatus(Constants.STATUS_COMPLETED);
        // Initial read inside recoverWorkflowTask() sees RUNNING; the re-read after recover()
        // (through workflowEngine.getWfInstanceStatus) sees COMPLETED.
        when(executionService.getWfInstance("sub-1")).thenReturn(subRunning);
        when(workflowEngine.getWfInstanceStatus("sub-1")).thenReturn(subCompleted);
        when(workflowEngine.recover("sub-1")).thenReturn(true);

        invokeRecoverWorkflowTask("task-x", "sub-1");

        verify(executionService).updateTaskStatusTransactional(
                "task-x", Constants.STATUS_COMPLETED, "Recovered: sub-workflow completed");
        assertTrue(fieldSet("completedTaskInstanceIds").contains("task-x"));
        assertFalse(fieldSet("runningWorkflowTaskIds").contains("task-x"));
    }
}
