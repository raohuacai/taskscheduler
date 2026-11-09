package com.ts.core.engine;

import com.ts.common.constants.Constants;
import com.ts.core.dag.WorkflowDAG;
import com.ts.monitor.WorkflowMonitor;
import com.ts.model.entity.TaskParameter;
import com.ts.model.entity.WfInstance;
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
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Real-concurrency regression for F1: the sub-workflow completion callback and
 * {@code executeTask}'s WORKFLOW finalization must be serialized by the task's
 * per-task lock.
 *
 * <p>Interleaving under test (made deterministic with latches, not sleeps):
 * <ol>
 *   <li>Thread P runs {@code executeTask}; inside {@code finalizeWorkflowTask} it holds the
 *       per-task lock and blocks while reading the sub-workflow status.</li>
 *   <li>Thread C runs {@code notifySubWorkflowCompleted} (the real callback) and must block
 *       on the same per-task lock before writing COMPLETED.</li>
 *   <li>P is released, sees the child as RUNNING and registers the task for the callback.</li>
 *   <li>C finally proceeds, writes COMPLETED and removes the registration.</li>
 * </ol>
 *
 * <p>Without the per-task lock in {@code finalizeWorkflowTask}, C would write COMPLETED and
 * remove the (not yet added) id while P is still blocked; P would then add it afterwards,
 * leaving the task registered forever and the workflow unable to complete (old A2/F1 bug).
 */
@ExtendWith(MockitoExtension.class)
class WorkflowInstanceConcurrencyTest {

    private static final String WF_INSTANCE_ID = "wf-inst-1";
    private static final String TASK_INSTANCE_ID = "task-inst-1";
    private static final String SUB_WF_INSTANCE_ID = "sub-1";
    private static final long AWAIT_SECONDS = 5;

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

    private WorkflowInstance instance;

    @BeforeEach
    void setUp() {
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

    // ---------- helpers ----------

    @SuppressWarnings("unchecked")
    private Set<String> fieldSet(String name) throws Exception {
        Field f = WorkflowInstance.class.getDeclaredField(name);
        f.setAccessible(true);
        return (Set<String>) f.get(instance);
    }

    private void invokeExecuteTask(Task task, TaskParameter taskParam) throws Exception {
        Method m = WorkflowInstance.class.getDeclaredMethod(
                "executeTask", Task.class, String.class, TaskParameter.class);
        m.setAccessible(true);
        m.invoke(instance, task, TASK_INSTANCE_ID, taskParam);
    }

    private TaskParameter workflowTaskParam() {
        TaskParameter tp = mock(TaskParameter.class);
        lenient().when(tp.getPvalue())
                .thenReturn("{\"subWorkflowInstanceId\":\"" + SUB_WF_INSTANCE_ID + "\"}");
        lenient().when(tp.getEnableFlag()).thenReturn(Constants.FLAG_YES);
        lenient().when(tp.getTaskType()).thenReturn(Constants.TYPE_GROUP_WORKFLOW);
        lenient().when(tp.getTaskId()).thenReturn("task1");
        lenient().when(tp.getTaskInstanceId()).thenReturn(TASK_INSTANCE_ID);
        return tp;
    }

    @Test
    void finalizeAndCallback_areSerializedByThePerTaskLock() throws Exception {
        // The moment P (finalize) is inside the lock and has read the sub-workflow status
        CountDownLatch statusRead = new CountDownLatch(1);
        // Test thread lets P continue after C has had a chance to reach the lock
        CountDownLatch releaseStatus = new CountDownLatch(1);
        // C's DB write (i.e. it got past the per-task lock)
        CountDownLatch callbackDbWrite = new CountDownLatch(1);
        CountDownLatch pDone = new CountDownLatch(1);
        CountDownLatch cDone = new CountDownLatch(1);
        AtomicReference<Throwable> pError = new AtomicReference<>();
        AtomicReference<Throwable> cError = new AtomicReference<>();

        WfInstance sub = new WfInstance();
        sub.setStatus(Constants.STATUS_RUNNING);
        when(workflowEngine.getWfInstanceStatus(SUB_WF_INSTANCE_ID)).thenAnswer(inv -> {
            statusRead.countDown();
            if (!releaseStatus.await(AWAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test did not release the status read in time");
            }
            return sub;
        });
        when(executionService.updateTaskStatusTransactional(
                TASK_INSTANCE_ID, Constants.STATUS_COMPLETED, "Sub-workflow completed: " + SUB_WF_INSTANCE_ID))
                .thenAnswer(inv -> {
                    callbackDbWrite.countDown();
                    return null;
                });

        Task task = mock(Task.class);
        when(task.isCanceled()).thenReturn(false);
        TaskParameter tp = workflowTaskParam();

        // ---- thread P: executeTask -> finalizeWorkflowTask (holds the per-task lock) ----
        Thread p = new Thread(() -> {
            try {
                invokeExecuteTask(task, tp);
            } catch (Throwable t) {
                pError.set(t);
            } finally {
                pDone.countDown();
            }
        }, "finalize-thread");
        p.start();

        assertTrue(statusRead.await(AWAIT_SECONDS, TimeUnit.SECONDS),
                "finalize thread must reach the sub-workflow status read");

        // ---- thread C: the real completion callback ----
        Thread c = new Thread(() -> {
            try {
                instance.notifySubWorkflowCompleted(TASK_INSTANCE_ID, SUB_WF_INSTANCE_ID);
            } catch (Throwable t) {
                cError.set(t);
            } finally {
                cDone.countDown();
            }
        }, "callback-thread");
        c.start();

        // The callback must be blocked on the per-task lock: no DB write while P holds it.
        assertFalse(callbackDbWrite.await(300, TimeUnit.MILLISECONDS),
                "the completion callback must wait for the finalize's per-task lock");
        assertFalse(cDone.await(100, TimeUnit.MILLISECONDS),
                "the completion callback must not finish while the finalize holds the lock");

        // ---- release P; it registers the task, then the callback removes it ----
        releaseStatus.countDown();
        assertTrue(pDone.await(AWAIT_SECONDS, TimeUnit.SECONDS), "finalize thread must finish");
        assertTrue(cDone.await(AWAIT_SECONDS, TimeUnit.SECONDS), "callback thread must finish");

        assertNull(pError.get(), "finalize thread failed: " + pError.get());
        assertNull(cError.get(), "callback thread failed: " + cError.get());
        assertTrue(callbackDbWrite.await(AWAIT_SECONDS, TimeUnit.SECONDS),
                "the callback must eventually persist COMPLETED");

        assertFalse(fieldSet("runningWorkflowTaskIds").contains(TASK_INSTANCE_ID),
                "the callback's removal must win over the finalize's registration");
        assertTrue(fieldSet("completedTaskInstanceIds").contains(TASK_INSTANCE_ID));
    }
}
