package com.ts.core.engine;

import com.ts.common.constants.Constants;
import com.ts.msg.NotificationFactory;
import com.ts.model.entity.WfInstance;
import com.ts.service.DefinitionService;
import com.ts.service.ExecutionService;
import com.ts.service.ParamsService;
import com.ts.task.TaskFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the B4 fix in {@link WorkflowEngine}:
 * <ul>
 *   <li>{@code isTerminalStatus} now treats STATUS_SKIPPED as terminal (was missing).</li>
 *   <li>{@code getOrCreateWorkflowInstance} therefore refuses to rebuild a SKIPPED
 *       (disabled) workflow, which previously leaked a spurious instance into
 *       {@code runningWorkflows} forever.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class WorkflowEngineTest {

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

    private WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        engine = new WorkflowEngine(definitionService, executionService, paramsService,
                taskFactory, notificationFactory);
    }

    // ==================== isTerminalStatus (B4) ====================

    @Test
    void isTerminalStatus_treatsAllTerminalStatesAsTerminal() throws Exception {
        assertTrue(invokeIsTerminalStatus(Constants.STATUS_COMPLETED));
        assertTrue(invokeIsTerminalStatus(Constants.STATUS_ERROR));
        assertTrue(invokeIsTerminalStatus(Constants.STATUS_CANCELED));
        assertTrue(invokeIsTerminalStatus(Constants.STATUS_SKIPPED));   // B4: was missing before
    }

    @Test
    void isTerminalStatus_treatsNonTerminalStatesAsNonTerminal() throws Exception {
        assertFalse(invokeIsTerminalStatus(Constants.STATUS_RUNNING));
        assertFalse(invokeIsTerminalStatus(Constants.STATUS_WAITING));
        assertFalse(invokeIsTerminalStatus(Constants.STATUS_TIMEOUT));
    }

    private boolean invokeIsTerminalStatus(String status) throws Exception {
        Method m = WorkflowEngine.class.getDeclaredMethod("isTerminalStatus", String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(engine, status);
    }

    // ==================== getOrCreateWorkflowInstance — SKIPPED workflow (B4) ====================

    @Test
    void getOrCreateWorkflowInstance_skippedWorkflow_returnsNullInsteadOfRebuilding() throws Exception {
        WfInstance skipped = new WfInstance();
        skipped.setWfInstanceId("wf-inst-skipped");
        skipped.setWorkflowId("WF-DISABLED");
        skipped.setStatus(Constants.STATUS_SKIPPED);

        when(executionService.beginTransaction()).thenReturn(true);
        when(executionService.getWfInstance("wf-inst-skipped")).thenReturn(skipped);
        // definitionService.getWorkflow is called inside a try/catch for logging only
        when(definitionService.getWorkflow(anyString())).thenReturn(null);

        Object result = invokeGetOrCreateWorkflowInstance("wf-inst-skipped");

        assertNull(result, "A SKIPPED (disabled) workflow must not be rebuilt into runningWorkflows");
        verify(executionService).rollbackTransaction();
    }

    @Test
    void getOrCreateWorkflowInstance_completedWorkflow_returnsNull() throws Exception {
        WfInstance completed = new WfInstance();
        completed.setWfInstanceId("wf-inst-done");
        completed.setWorkflowId("WF-1");
        completed.setStatus(Constants.STATUS_COMPLETED);

        when(executionService.beginTransaction()).thenReturn(true);
        when(executionService.getWfInstance("wf-inst-done")).thenReturn(completed);
        when(definitionService.getWorkflow(anyString())).thenReturn(null);

        assertNull(invokeGetOrCreateWorkflowInstance("wf-inst-done"));
    }

    private Object invokeGetOrCreateWorkflowInstance(String wfInstanceId) throws Exception {
        Method m = WorkflowEngine.class.getDeclaredMethod("getOrCreateWorkflowInstance", String.class);
        m.setAccessible(true);
        return m.invoke(engine, wfInstanceId);
    }
}
