package com.ts.core.engine;

import com.ts.common.constants.Constants;
import com.ts.core.dag.WorkflowDAG;
import com.ts.exception.WorkflowException;
import com.ts.msg.NotificationFactory;
import com.ts.monitor.WorkflowMonitor;
import com.ts.model.entity.TaskInstance;
import com.ts.model.entity.TaskParameter;
import com.ts.model.entity.WfInstance;
import com.ts.model.entity.WfParameter;
import com.ts.service.DefinitionService;
import com.ts.service.ExecutionService;
import com.ts.service.ParamsService;
import com.ts.task.Task;
import com.ts.task.TaskFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link WorkflowEngine} lifecycle operations:
 * start (duplicate guard), cancel, complete, recover, and task operations.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowEngineLifecycleTest {

    @Mock
    private DefinitionService definitionService;
    @Mock
    private ExecutionService executionService;
    @Mock
    private ParamsService paramsService;
    @Mock
    private TaskFactory taskFactory;
    @Mock
    private NotificationFactory notificationFactory;
    @Mock
    private WorkflowMonitor monitor;

    private WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        engine = new WorkflowEngine(definitionService, executionService, paramsService,
                taskFactory, notificationFactory);
        engine.setMonitor(monitor);
    }

    // ---------- reflection helpers ----------

    @SuppressWarnings("unchecked")
    private Map<String, WorkflowInstance> runningWorkflows() throws Exception {
        Field f = WorkflowEngine.class.getDeclaredField("runningWorkflows");
        f.setAccessible(true);
        return (Map<String, WorkflowInstance>) f.get(engine);
    }

    private void setExecutorService(ThreadPoolExecutor executor) throws Exception {
        Field f = WorkflowEngine.class.getDeclaredField("executorService");
        f.setAccessible(true);
        f.set(engine, executor);
    }

    private String invokeStart(String workflowId, String wfInstanceId, Map<String, Object> params) throws Exception {
        Method m = WorkflowEngine.class.getDeclaredMethod(
                "startWorkflowInternal", String.class, String.class, Map.class);
        m.setAccessible(true);
        return (String) m.invoke(engine, workflowId, wfInstanceId, params);
    }

    private boolean invokeCancel(String wfInstanceId) throws Exception {
        Method m = WorkflowEngine.class.getDeclaredMethod("cancelWorkflowInternal", String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(engine, wfInstanceId);
    }

    private boolean invokeComplete(String wfInstanceId) throws Exception {
        Method m = WorkflowEngine.class.getDeclaredMethod("completeWorkflowInternal", String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(engine, wfInstanceId);
    }

    private boolean invokeRecover(String wfInstanceId) throws Exception {
        Method m = WorkflowEngine.class.getDeclaredMethod("recoverWorkflowInternal", String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(engine, wfInstanceId);
    }

    // ==================== start — duplicate guard ====================

    @Test
    void start_sameWorkflowAlreadyRunning_throwsWorkflowException() throws Exception {
        WorkflowInstance existing = mock(WorkflowInstance.class);
        when(existing.getWorkflowId()).thenReturn("WF1");
        runningWorkflows().put("inst-existing", existing);

        // reflection wraps the target exception; unwrap the cause
        InvocationTargetException ite = assertThrows(InvocationTargetException.class,
                () -> invokeStart("WF1", "inst-new", new HashMap<>()));
        assertTrue(ite.getCause() instanceof WorkflowException);
    }

    // ==================== cancel ====================

    @Test
    void cancel_instanceNotFound_returnsFalse() throws Exception {
        when(executionService.beginTransaction()).thenReturn(true);
        when(executionService.getWfInstance("missing")).thenReturn(null);

        assertFalse(invokeCancel("missing"));
        verify(executionService).rollbackTransaction();
    }

    @Test
    void cancel_completedWorkflow_returnsFalse() throws Exception {
        when(executionService.beginTransaction()).thenReturn(true);
        WfInstance wf = new WfInstance();
        wf.setWfInstanceId("wf-inst");
        wf.setStatus(Constants.STATUS_COMPLETED);
        when(executionService.getWfInstance("wf-inst")).thenReturn(wf);

        assertFalse(invokeCancel("wf-inst"));
    }

    @Test
    void cancel_restartCase_noInMemoryInstance_updatesStatusAndReturnsTrue() throws Exception {
        when(executionService.beginTransaction()).thenReturn(true);
        WfInstance wf = new WfInstance();
        wf.setWfInstanceId("wf-inst");
        wf.setStatus(Constants.STATUS_RUNNING);
        when(executionService.getWfInstance("wf-inst")).thenReturn(wf);

        // no in-memory instance (e.g. after a restart)
        assertTrue(invokeCancel("wf-inst"));
        verify(executionService).updateStatusTransactional("wf-inst", Constants.STATUS_CANCELED, "");
    }

    // ==================== completeWorkflow ====================

    @Test
    void complete_runningWorkflow_marksCompleted() throws Exception {
        when(executionService.beginTransaction()).thenReturn(true);
        WfInstance wf = new WfInstance();
        wf.setWfInstanceId("wf-inst");
        wf.setStatus(Constants.STATUS_RUNNING);
        when(executionService.getWfInstance("wf-inst")).thenReturn(wf);

        assertTrue(invokeComplete("wf-inst"));
        verify(executionService).updateStatusTransactional("wf-inst", Constants.STATUS_COMPLETED, "");
    }

    @Test
    void complete_nonRunningWorkflow_returnsFalse() throws Exception {
        when(executionService.beginTransaction()).thenReturn(true);
        WfInstance wf = new WfInstance();
        wf.setWfInstanceId("wf-inst");
        wf.setStatus(Constants.STATUS_WAITING);
        when(executionService.getWfInstance("wf-inst")).thenReturn(wf);

        assertFalse(invokeComplete("wf-inst"));
    }

    // ==================== recover ====================

    @Test
    void recover_completedWorkflow_returnsFalse() throws Exception {
        when(executionService.beginTransaction()).thenReturn(true);
        WfInstance wf = new WfInstance();
        wf.setWfInstanceId("wf-inst");
        wf.setWorkflowId("WF1");
        wf.setStatus(Constants.STATUS_COMPLETED);
        when(executionService.getWfInstance("wf-inst")).thenReturn(wf);
        when(definitionService.getWorkflow(anyString())).thenReturn(null);

        assertFalse(invokeRecover("wf-inst"));
    }

    // ==================== F2: never register an instance that finished during restore ====================

    /**
     * Regression for F2: when every task is already terminal, restoreState() completes the
     * workflow. The instance must NOT be put into runningWorkflows afterwards — otherwise
     * isRunningWorkflow() stays true forever (cron triggers discarded, manual start rejected).
     */
    @Test
    void recover_workflowFinishesDuringRestore_doesNotRegisterZombieInstance() throws Exception {
        when(executionService.beginTransaction()).thenReturn(true);
        WfInstance wf = new WfInstance();
        wf.setWfInstanceId("wf-inst");
        wf.setWorkflowId("WF1");
        wf.setStatus(Constants.STATUS_RUNNING);
        wf.setStartDate(new Date());
        when(executionService.getWfInstance("wf-inst")).thenReturn(wf);
        when(definitionService.getWorkflow("WF1")).thenReturn(null);

        Map<String, Object> params = new HashMap<>();
        params.put(Constants.PARAM_WORKFLOW_NAME, "WF");
        when(paramsService.getWfParamsByIdAsMap("wf-inst")).thenReturn(params);
        when(paramsService.buildWorkflowDAG("wf-inst")).thenReturn(new WorkflowDAG());

        TaskInstance ti = new TaskInstance();
        ti.setTaskInstanceId("task-x");
        ti.setStatus(Constants.STATUS_COMPLETED);
        when(executionService.getTaskInstancesForWorkflow("wf-inst")).thenReturn(List.of(ti));

        TaskParameter tp = new TaskParameter();
        tp.setTaskType("SHELL");
        tp.setEnableFlag(Constants.FLAG_YES);
        when(paramsService.getTaskParamsByInstanceId("task-x")).thenReturn(tp);

        WfParameter wfp = new WfParameter();
        wfp.setWorkflowName("WF");
        wfp.setPvalue("{}");
        when(paramsService.getWfParamsByInstanceId("wf-inst")).thenReturn(wfp);

        assertTrue(invokeRecover("wf-inst"));

        assertTrue(runningWorkflows().isEmpty(),
                "a workflow completed during restore must not remain registered as running");
        verify(executionService).updateStatusTransactional("wf-inst", Constants.STATUS_COMPLETED, "");
        verify(monitor, never()).recordWorkflowStart(anyString(), anyString());
    }

    /**
     * Recover on an already loaded (still active) instance must re-evaluate it — this is
     * the path that retries a failed completion (F3) when the operator clicks Recover.
     */
    @Test
    void recover_alreadyLoadedInstance_reEvaluatesInsteadOfNoOp() throws Exception {
        WorkflowInstance loaded = mock(WorkflowInstance.class);
        when(loaded.isFinished()).thenReturn(false);
        runningWorkflows().put("wf-inst", loaded);

        assertTrue(engine.recover("wf-inst"));

        verify(loaded).resume();
        verify(executionService, never()).getWfInstance("wf-inst");
    }

    /**
     * A finished instance left over in memory (legacy state) must be dropped and rebuilt
     * from the database instead of being treated as "already running".
     */
    @Test
    void recover_completedInstanceInMemory_isDroppedAndRebuilt() throws Exception {
        WorkflowInstance stale = mock(WorkflowInstance.class);
        when(stale.isFinished()).thenReturn(true);
        when(stale.isCompletionPersisted()).thenReturn(true);
        runningWorkflows().put("wf-inst", stale);

        when(executionService.beginTransaction()).thenReturn(true);
        WfInstance wf = new WfInstance();
        wf.setWfInstanceId("wf-inst");
        wf.setWorkflowId("WF1");
        wf.setStatus(Constants.STATUS_COMPLETED);
        when(executionService.getWfInstance("wf-inst")).thenReturn(wf);

        assertFalse(engine.recover("wf-inst"));

        assertFalse(runningWorkflows().containsKey("wf-inst"));
        verify(stale, never()).resume();
    }

    /**
     * A cancelled instance is still being finalized by cancelWorkflowInternal(). Recover
     * must not drop it and rebuild concurrently (that would create two instances for one run).
     */
    @Test
    void recover_cancellingInstanceInMemory_refusesWithoutRebuild() throws Exception {
        WorkflowInstance cancelling = mock(WorkflowInstance.class);
        when(cancelling.isFinished()).thenReturn(true);   // cancelled
        when(cancelling.isCompletionPersisted()).thenReturn(false);
        runningWorkflows().put("wf-inst", cancelling);

        assertFalse(engine.recover("wf-inst"));

        assertTrue(runningWorkflows().containsKey("wf-inst"), "the cancelling instance must be left alone");
        verify(executionService, never()).getWfInstance("wf-inst");
        verify(cancelling, never()).resume();
    }

    /**
     * Normal recovery path (F2 regression): a workflow with a pending task must be
     * registered and resumed, and the ready task must be submitted to the executor.
     */
    @Test
    void recover_workflowWithPendingTask_registersInstanceAndSubmitsTask() throws Exception {
        // Inject a mock executor so submitTask() registers the future without running it.
        setExecutorService(mock(ThreadPoolExecutor.class));

        when(executionService.beginTransaction()).thenReturn(true);
        WfInstance wf = new WfInstance();
        wf.setWfInstanceId("wf-inst");
        wf.setWorkflowId("WF1");
        wf.setStatus(Constants.STATUS_RUNNING);
        when(executionService.getWfInstance("wf-inst")).thenReturn(wf);
        when(definitionService.getWorkflow("WF1")).thenReturn(null);

        Map<String, Object> params = new HashMap<>();
        params.put(Constants.PARAM_WORKFLOW_NAME, "WF");
        when(paramsService.getWfParamsByIdAsMap("wf-inst")).thenReturn(params);

        // The DAG must contain the task, otherwise canExecute() would reject it.
        WorkflowDAG dag = new WorkflowDAG();
        dag.addTask("task-x", "T");
        when(paramsService.buildWorkflowDAG("wf-inst")).thenReturn(dag);

        TaskInstance ti = new TaskInstance();
        ti.setTaskInstanceId("task-x");
        ti.setWfInstanceId("wf-inst");
        ti.setStatus(Constants.STATUS_WAITING);
        when(executionService.getTaskInstancesForWorkflow("wf-inst")).thenReturn(List.of(ti));
        when(executionService.getTaskInstance("task-x")).thenReturn(ti);

        TaskParameter tp = new TaskParameter();
        tp.setTaskType("SHELL");
        tp.setEnableFlag(Constants.FLAG_YES);
        when(paramsService.getTaskParamsByInstanceId("task-x")).thenReturn(tp);
        when(taskFactory.createTask(anyString(), any())).thenReturn(mock(Task.class));

        assertTrue(engine.recover("wf-inst"));

        assertTrue(runningWorkflows().containsKey("wf-inst"),
                "a still-active recovered workflow must be registered");
        verify(executionService).updateTaskStatusTransactional("task-x", Constants.STATUS_RUNNING, "");
        verify(monitor).recordWorkflowStart("WF1", "wf-inst");
    }

    /**
     * R1 end-to-end: the parent is being recovered, its sub-workflow is already finished and
     * completes synchronously during workflowEngine.recover(child).
     *
     * <p>With the instance registered before restoreState(), the child's completion callback
     * finds the parent in memory and finalizes its task. The parent then completes normally
     * and must NOT be left in runningWorkflows (zombie), nor keep the task in
     * runningWorkflowTaskIds (stale "waiting for callback").
     */
    @Test
    void recover_parentWhoseChildCompletesDuringRecovery_doesNotLeaveZombie() throws Exception {
        when(executionService.beginTransaction()).thenReturn(true);

        WfInstance parent = new WfInstance();
        parent.setWfInstanceId("wf-inst");
        parent.setWorkflowId("WF1");
        parent.setStatus(Constants.STATUS_RUNNING);
        parent.setStartDate(new Date());

        WfInstance child = new WfInstance();
        child.setWfInstanceId("sub-1");
        child.setWorkflowId("WFC");
        child.setStatus(Constants.STATUS_RUNNING);
        child.setStartDate(new Date());

        when(executionService.getWfInstance("wf-inst")).thenReturn(parent);
        when(executionService.getWfInstance("sub-1")).thenReturn(child);
        when(definitionService.getWorkflow(anyString())).thenReturn(null);

        Map<String, Object> params = new HashMap<>();
        params.put(Constants.PARAM_WORKFLOW_NAME, "WF");
        when(paramsService.getWfParamsByIdAsMap(anyString())).thenReturn(params);
        when(paramsService.buildWorkflowDAG("wf-inst")).thenReturn(dagWith("task-x"));
        when(paramsService.buildWorkflowDAG("sub-1")).thenReturn(new WorkflowDAG());

        // Parent: one WORKFLOW task, RUNNING, waiting for the child
        TaskInstance parentTask = new TaskInstance();
        parentTask.setTaskInstanceId("task-x");
        parentTask.setWfInstanceId("wf-inst");
        parentTask.setStatus(Constants.STATUS_RUNNING);
        when(executionService.getTaskInstancesForWorkflow("wf-inst")).thenReturn(List.of(parentTask));
        when(executionService.getTaskInstance("task-x")).thenReturn(parentTask);

        TaskParameter parentTp = new TaskParameter();
        parentTp.setTaskType(Constants.TYPE_GROUP_WORKFLOW);
        parentTp.setEnableFlag(Constants.FLAG_YES);
        parentTp.setPvalue("{\"subWorkflowInstanceId\":\"sub-1\"}");
        when(paramsService.getTaskParamsByInstanceId("task-x")).thenReturn(parentTp);

        // Child: all tasks already terminal → it completes during recover()
        TaskInstance childTask = new TaskInstance();
        childTask.setTaskInstanceId("sub__task");
        childTask.setWfInstanceId("sub-1");
        childTask.setStatus(Constants.STATUS_COMPLETED);
        when(executionService.getTaskInstancesForWorkflow("sub-1")).thenReturn(List.of(childTask));

        TaskParameter childTp = new TaskParameter();
        childTp.setTaskType("SHELL");
        childTp.setEnableFlag(Constants.FLAG_YES);
        childTp.setPvalue("{}");
        when(paramsService.getTaskParamsByInstanceId("sub__task")).thenReturn(childTp);

        // The child's params carry the parent linkage used by the completion callback
        WfParameter childParams = new WfParameter();
        childParams.setWorkflowName("WFC");
        childParams.setPvalue("{\"parent.taskInstanceId\":\"task-x\",\"parent.workflowInstanceId\":\"wf-inst\"}");
        when(paramsService.getWfParamsByInstanceId("sub-1")).thenReturn(childParams);

        WfParameter parentParams = new WfParameter();
        parentParams.setWorkflowName("WF");
        parentParams.setPvalue("{}");
        when(paramsService.getWfParamsByInstanceId("wf-inst")).thenReturn(parentParams);

        assertTrue(engine.recover("wf-inst"));

        assertTrue(runningWorkflows().isEmpty(),
                "R1: the parent must not stay registered after completing during recovery");
        verify(executionService).updateTaskStatusTransactional(
                "task-x", Constants.STATUS_COMPLETED, "Sub-workflow completed: sub-1");
        verify(executionService).updateStatusTransactional("wf-inst", Constants.STATUS_COMPLETED, "");
        // The workflow completed during restore → recover() must not report a fresh start
        verify(monitor, never()).recordWorkflowStart(anyString(), anyString());
    }

    private WorkflowDAG dagWith(String taskId) {
        WorkflowDAG dag = new WorkflowDAG();
        dag.addTask(taskId, "T");
        return dag;
    }

    // ==================== task operations — validation guards ====================

    @Test
    void retryTask_workflowTerminal_returnsFalse() {
        TaskInstance ti = new TaskInstance();
        ti.setTaskInstanceId("task-x");
        ti.setWfInstanceId("wf-inst");
        when(executionService.getTaskInstance("task-x")).thenReturn(ti);
        WfInstance wf = new WfInstance();
        wf.setStatus(Constants.STATUS_COMPLETED);
        when(executionService.getWfInstance("wf-inst")).thenReturn(wf);

        assertFalse(engine.retryTask("task-x"));
    }

    @Test
    void retryTask_invalidTaskStatus_returnsFalse() {
        TaskInstance ti = new TaskInstance();
        ti.setTaskInstanceId("task-x");
        ti.setWfInstanceId("wf-inst");
        ti.setStatus(Constants.STATUS_WAITING);   // WAITING is not retryable
        when(executionService.getTaskInstance("task-x")).thenReturn(ti);
        WfInstance wf = new WfInstance();
        wf.setStatus(Constants.STATUS_RUNNING);
        when(executionService.getWfInstance("wf-inst")).thenReturn(wf);

        assertFalse(engine.retryTask("task-x"));
    }

    @Test
    void skipTask_workflowTerminal_returnsFalse() {
        TaskInstance ti = new TaskInstance();
        ti.setTaskInstanceId("task-x");
        ti.setWfInstanceId("wf-inst");
        when(executionService.getTaskInstance("task-x")).thenReturn(ti);
        WfInstance wf = new WfInstance();
        wf.setStatus(Constants.STATUS_CANCELED);
        when(executionService.getWfInstance("wf-inst")).thenReturn(wf);

        assertFalse(engine.skipTask("task-x"));
    }

    @Test
    void cancelTask_nonRunningTask_returnsFalse() {
        TaskInstance ti = new TaskInstance();
        ti.setTaskInstanceId("task-x");
        ti.setWfInstanceId("wf-inst");
        ti.setStatus(Constants.STATUS_WAITING);   // cancel only allowed on RUNNING
        when(executionService.getTaskInstance("task-x")).thenReturn(ti);
        WfInstance wf = new WfInstance();
        wf.setStatus(Constants.STATUS_RUNNING);
        when(executionService.getWfInstance("wf-inst")).thenReturn(wf);

        assertFalse(engine.cancelTask("task-x"));
    }
}
