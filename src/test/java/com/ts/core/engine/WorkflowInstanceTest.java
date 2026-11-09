package com.ts.core.engine;

import com.ts.common.constants.Constants;
import com.ts.core.dag.WorkflowDAG;
import com.ts.monitor.WorkflowMonitor;
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
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for the A3 fix in {@link WorkflowInstance#executeTask}: when a task was
 * cancelled/skipped while executing, {@code AbstractTask.execute()} returns
 * cleanly at a lifecycle boundary, and the fix ensures {@code executeTask} does
 * NOT overwrite the CANCELED/SKIPPED status with COMPLETED.
 *
 * <p>Scenarios covered:
 * <ul>
 *   <li>normal task (not canceled) → status COMPLETED is written</li>
 *   <li>cancelled task → no status is written (keeps CANCELED from the cancel entry point)</li>
 *   <li>disabled task → status SKIPPED is written</li>
 *   <li>WORKFLOW-type task cancelled → also not overwritten (the guard covers the WORKFLOW branch)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class WorkflowInstanceTest {

    private static final String WF_INSTANCE_ID = "wf-inst-1";
    private static final String TASK_INSTANCE_ID = "task-inst-1";

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
                workflowEngine, wfInstance, parameters, mock(WorkflowDAG.class),
                executionService, paramsService, taskFactory,
                notificationManager, executorService, monitor);
    }

    @AfterEach
    void tearDown() {
        taskLogMock.close();
    }

    private void invokeExecuteTask(Task task, TaskParameter taskParam) throws Exception {
        Method m = WorkflowInstance.class.getDeclaredMethod(
                "executeTask", Task.class, String.class, TaskParameter.class);
        m.setAccessible(true);
        m.invoke(instance, task, TASK_INSTANCE_ID, taskParam);
    }

    @SuppressWarnings("unchecked")
    private Set<String> fieldSet(String name) throws Exception {
        Field f = WorkflowInstance.class.getDeclaredField(name);
        f.setAccessible(true);
        return (Set<String>) f.get(instance);
    }

    private void setFlag(String name, boolean value) throws Exception {
        Field f = WorkflowInstance.class.getDeclaredField(name);
        f.setAccessible(true);
        f.setBoolean(instance, value);
    }

    private TaskParameter enabledTaskParam(String taskType) {
        TaskParameter tp = mock(TaskParameter.class);
        // Shared helper across scenarios — some stubs are only exercised by certain
        // branches (e.g. canceled tasks return before reading taskId), so mark lenient
        // to keep Mockito's strict-stubbing detector happy.
        lenient().when(tp.getPvalue()).thenReturn("{}");
        lenient().when(tp.getEnableFlag()).thenReturn(Constants.FLAG_YES);
        lenient().when(tp.getTaskType()).thenReturn(taskType);
        lenient().when(tp.getTaskId()).thenReturn("task1");
        lenient().when(tp.getTaskInstanceId()).thenReturn(TASK_INSTANCE_ID);
        return tp;
    }

    // ==================== A3: cancelled task is not overwritten with COMPLETED ====================

    @Test
    void executeTask_canceledTask_doesNotOverwriteStatus() throws Exception {
        Task task = mock(Task.class);
        when(task.isCanceled()).thenReturn(true);   // task cancelled during execution
        TaskParameter tp = enabledTaskParam("SHELL");

        invokeExecuteTask(task, tp);

        // The cancel/skip entry point already wrote CANCELED/SKIPPED; executeTask must not touch it.
        verify(executionService, never()).updateTaskStatusTransactional(anyString(), anyString(), anyString());
    }

    @Test
    void executeTask_canceledWorkflowTask_doesNotOverwriteStatus() throws Exception {
        Task task = mock(Task.class);
        when(task.isCanceled()).thenReturn(true);
        TaskParameter tp = enabledTaskParam(Constants.TYPE_GROUP_WORKFLOW);

        invokeExecuteTask(task, tp);

        // A3 guard covers the WORKFLOW branch too — no COMPLETED/ERROR is written.
        verify(executionService, never()).updateTaskStatusTransactional(anyString(), anyString(), anyString());
    }

    // ==================== normal path still writes COMPLETED ====================

    @Test
    void executeTask_notCanceled_marksCompleted() throws Exception {
        Task task = mock(Task.class);
        when(task.isCanceled()).thenReturn(false);
        TaskParameter tp = enabledTaskParam("SHELL");

        invokeExecuteTask(task, tp);

        verify(executionService)
                .updateTaskStatusTransactional(TASK_INSTANCE_ID, Constants.STATUS_COMPLETED, "Task completed");
    }

    // ==================== disabled task is skipped ====================

    @Test
    void executeTask_disabledTask_marksSkipped() throws Exception {
        Task task = mock(Task.class);
        TaskParameter tp = enabledTaskParam("SHELL");
        when(tp.getEnableFlag()).thenReturn(Constants.FLAG_NO);

        invokeExecuteTask(task, tp);

        verify(executionService)
                .updateTaskStatusTransactional(TASK_INSTANCE_ID, Constants.STATUS_SKIPPED, "Task skipped");
    }

    // ==================== F1: WORKFLOW finalization is serialized with the callback ====================

    /**
     * Regression for F1: the completion callback may finalize the task while execute() is
     * returning. The finalization must re-check the in-memory terminal sets under the
     * per-task lock and must NOT register the task for a callback that already arrived
     * (otherwise the id stays in runningWorkflowTaskIds forever and the workflow never completes).
     */
    @Test
    void executeTask_workflowTaskAlreadyFinalizedByCallback_doesNotRegisterCallback() throws Exception {
        Task task = mock(Task.class);
        when(task.isCanceled()).thenReturn(false);
        TaskParameter tp = enabledTaskParam(Constants.TYPE_GROUP_WORKFLOW);
        when(tp.getPvalue()).thenReturn("{\"subWorkflowInstanceId\":\"sub-1\"}");

        // Simulate the callback having moved the task to COMPLETED while execute() was returning.
        fieldSet("completedTaskInstanceIds").add(TASK_INSTANCE_ID);

        invokeExecuteTask(task, tp);

        assertFalse(fieldSet("runningWorkflowTaskIds").contains(TASK_INSTANCE_ID),
                "a task finalized by the callback must not be registered again");
        // It must not even look at the sub-workflow status, nor write a status again.
        verify(workflowEngine, never()).getWfInstanceStatus(anyString());
        verify(executionService, never()).updateTaskStatusTransactional(anyString(), anyString(), anyString());
    }

    /**
     * Normal path: the sub-workflow is still running, so the task stays RUNNING and is
     * registered for the (future) callback.
     */
    @Test
    void executeTask_workflowTaskStillRunning_registersForCallback() throws Exception {
        Task task = mock(Task.class);
        when(task.isCanceled()).thenReturn(false);
        TaskParameter tp = enabledTaskParam(Constants.TYPE_GROUP_WORKFLOW);
        when(tp.getPvalue()).thenReturn("{\"subWorkflowInstanceId\":\"sub-1\"}");
        WfInstance sub = new WfInstance();
        sub.setStatus(Constants.STATUS_RUNNING);
        when(workflowEngine.getWfInstanceStatus("sub-1")).thenReturn(sub);

        invokeExecuteTask(task, tp);

        assertTrue(fieldSet("runningWorkflowTaskIds").contains(TASK_INSTANCE_ID));
        verify(executionService, never()).updateTaskStatusTransactional(anyString(), anyString(), anyString());
    }

    /**
     * F1: when the owning workflow has already been cancelled, the finalization must not
     * register a callback (the callback will never be processed) nor write any status.
     */
    @Test
    void executeTask_workflowTaskCancelledWorkflow_doesNotRegister() throws Exception {
        Task task = mock(Task.class);
        when(task.isCanceled()).thenReturn(false);
        TaskParameter tp = enabledTaskParam(Constants.TYPE_GROUP_WORKFLOW);
        when(tp.getPvalue()).thenReturn("{\"subWorkflowInstanceId\":\"sub-1\"}");
        setFlag("cancelled", true);

        invokeExecuteTask(task, tp);

        assertFalse(fieldSet("runningWorkflowTaskIds").contains(TASK_INSTANCE_ID));
        verify(workflowEngine, never()).getWfInstanceStatus(anyString());
        verify(executionService, never()).updateTaskStatusTransactional(anyString(), anyString(), anyString());
    }

    /**
     * F1 / state diagram: a sub-workflow already COMPLETED maps to task COMPLETED.
     */
    @Test
    void executeTask_workflowTaskChildCompleted_marksTaskCompleted() throws Exception {
        Task task = mock(Task.class);
        when(task.isCanceled()).thenReturn(false);
        TaskParameter tp = enabledTaskParam(Constants.TYPE_GROUP_WORKFLOW);
        when(tp.getPvalue()).thenReturn("{\"subWorkflowInstanceId\":\"sub-1\"}");
        WfInstance sub = new WfInstance();
        sub.setStatus(Constants.STATUS_COMPLETED);
        when(workflowEngine.getWfInstanceStatus("sub-1")).thenReturn(sub);

        invokeExecuteTask(task, tp);

        verify(executionService).updateTaskStatusTransactional(
                TASK_INSTANCE_ID, Constants.STATUS_COMPLETED, "Sub-workflow COMPLETED: sub-1");
        assertTrue(fieldSet("completedTaskInstanceIds").contains(TASK_INSTANCE_ID));
        assertFalse(fieldSet("runningWorkflowTaskIds").contains(TASK_INSTANCE_ID));
    }

    /**
     * F1 / state diagram: a sub-workflow that ended in ERROR (or was cancelled) maps to task ERROR.
     */
    @Test
    void executeTask_workflowTaskChildError_marksTaskError() throws Exception {
        Task task = mock(Task.class);
        when(task.isCanceled()).thenReturn(false);
        TaskParameter tp = enabledTaskParam(Constants.TYPE_GROUP_WORKFLOW);
        when(tp.getPvalue()).thenReturn("{\"subWorkflowInstanceId\":\"sub-1\"}");
        WfInstance sub = new WfInstance();
        sub.setStatus(Constants.STATUS_ERROR);
        when(workflowEngine.getWfInstanceStatus("sub-1")).thenReturn(sub);

        invokeExecuteTask(task, tp);

        verify(executionService).updateTaskStatusTransactional(
                TASK_INSTANCE_ID, Constants.STATUS_ERROR, "Sub-workflow ERROR: sub-1");
        assertTrue(fieldSet("errorTaskInstanceIds").contains(TASK_INSTANCE_ID));
        assertFalse(fieldSet("runningWorkflowTaskIds").contains(TASK_INSTANCE_ID));
    }

    @Test
    void executeTask_workflowTaskChildNotFound_marksTaskError() throws Exception {
        Task task = mock(Task.class);
        when(task.isCanceled()).thenReturn(false);
        TaskParameter tp = enabledTaskParam(Constants.TYPE_GROUP_WORKFLOW);
        when(tp.getPvalue()).thenReturn("{\"subWorkflowInstanceId\":\"sub-1\"}");
        // getWfInstanceStatus("sub-1") returns null by default

        invokeExecuteTask(task, tp);

        verify(executionService).updateTaskStatusTransactional(
                TASK_INSTANCE_ID, Constants.STATUS_ERROR, "Sub-workflow instance not found: sub-1");
        assertFalse(fieldSet("runningWorkflowTaskIds").contains(TASK_INSTANCE_ID));
    }

    @Test
    void executeTask_workflowTaskNoSubInstanceId_marksTaskError() throws Exception {
        Task task = mock(Task.class);
        when(task.isCanceled()).thenReturn(false);
        TaskParameter tp = enabledTaskParam(Constants.TYPE_GROUP_WORKFLOW);   // pvalue "{}"

        invokeExecuteTask(task, tp);

        verify(executionService).updateTaskStatusTransactional(
                TASK_INSTANCE_ID, Constants.STATUS_ERROR, "Sub-workflow start failed: no instance id returned");
        verify(workflowEngine, never()).getWfInstanceStatus(anyString());
        assertFalse(fieldSet("runningWorkflowTaskIds").contains(TASK_INSTANCE_ID));
    }
}
