package com.ts.task.core;

import com.ts.common.constants.Constants;
import com.ts.core.engine.WorkflowEngine;
import com.ts.exception.TaskExecutionException;
import com.ts.exception.WorkflowNotFoundException;
import com.ts.service.impl.TaskLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link WorkflowTask}: sub-workflow parameter collection (sub.*
 * prefix, parent context, forced notification suppression) and the start
 * action, including failure paths.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowTaskTest {

    @Mock
    private WorkflowEngine workflowEngine;

    private Map<String, Object> baseParams() {
        Map<String, Object> p = new HashMap<>();
        p.put(Constants.PARAM_TASK_SUBWORKFLOWID, "SUB-WF");
        p.put("sub.foo", "bar");
        p.put(Constants.PARAM_WORKFLOW_ID, "WF1");
        p.put(Constants.PARAM_WORKFLOW_NAME, "WF");
        p.put(Constants.PARAM_WORKFLOW_INSTANCE, "inst-1");
        p.put(Constants.PARAM_TASK_ID, "task1");
        p.put(Constants.PARAM_TASK_NAME, "T");
        p.put(Constants.PARAM_TASK_INSTANCE, "task-inst-1");
        p.put(Constants.PARAM_TASK_TYPE, Constants.TYPE_GROUP_WORKFLOW);
        p.put(Constants.PARAM_WORKFLOW_TRIGGERDATE, new Date());
        p.put(Constants.PARAM_TOP_PARENT_WORKFLOW_INSTANCE, "inst-1");
        p.put(Constants.PARAM_WORKFLOW_TRIGGERBY, "admin");
        return p;
    }

    // ==================== success: starts sub-workflow with collected params ====================

    @Test
    void execute_startsSubWorkflowAndCollectsParams() {
        try (MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            when(workflowEngine.start(eq("SUB-WF"), anyMap())).thenReturn("sub-inst-1");

            WorkflowTask task = new WorkflowTask(workflowEngine);
            assertDoesNotThrow(() -> task.execute(baseParams()));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(workflowEngine).start(eq("SUB-WF"), captor.capture());
            Map<String, Object> sub = captor.getValue();

            // sub.* params are passed through with the prefix stripped
            assertEquals("bar", sub.get("foo"));
            // parent context is propagated
            assertEquals("inst-1", sub.get(Constants.PARAM_PARENT + "." + Constants.PARAM_WORKFLOW_INSTANCE));
            assertEquals("WF1", sub.get(Constants.PARAM_PARENT + "." + Constants.PARAM_WORKFLOW_ID));
            assertEquals("task-inst-1", sub.get(Constants.PARAM_PARENT + "." + Constants.PARAM_TASK_INSTANCE));
            // notification for the sub-workflow is suppressed
            assertEquals(Constants.FLAG_NO, sub.get(Constants.PARAM_NOTICE_EMAIL));
            assertEquals(Constants.FLAG_NO, sub.get(Constants.PARAM_NOTICE_WEBHOOK));
            // trigger date / top parent propagated
            assertNotNull(sub.get(Constants.PARAM_WORKFLOW_TRIGGERDATE));
            assertEquals("inst-1", sub.get(Constants.PARAM_TOP_PARENT_WORKFLOW_INSTANCE));
        }
    }

    // ==================== failure: start returns null ====================

    @Test
    void execute_subWorkflowStartReturnsNull_throws() {
        try (MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            when(workflowEngine.start(anyString(), anyMap())).thenReturn(null);

            WorkflowTask task = new WorkflowTask(workflowEngine);
            assertThrows(TaskExecutionException.class, () -> task.execute(baseParams()));
        }
    }

    // ==================== failure: sub-workflow not found ====================

    @Test
    void execute_subWorkflowNotFound_throws() {
        try (MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            when(workflowEngine.start(anyString(), anyMap()))
                    .thenThrow(new WorkflowNotFoundException("sub not found"));

            WorkflowTask task = new WorkflowTask(workflowEngine);
            assertThrows(TaskExecutionException.class, () -> task.execute(baseParams()));
        }
    }

    // ==================== missing sub-workflow id ====================

    @Test
    void execute_missingSubWorkflowId_throws() {
        try (MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            WorkflowTask task = new WorkflowTask(workflowEngine);
            Map<String, Object> p = baseParams();
            p.remove(Constants.PARAM_TASK_SUBWORKFLOWID);
            assertThrows(TaskExecutionException.class, () -> task.execute(p));
            verify(workflowEngine, never()).start(anyString(), anyMap());
        }
    }
}
