package com.ts.core.engine;

import com.ts.common.constants.Constants;
import com.ts.msg.NotificationFactory;
import com.ts.model.entity.WfInstance;
import com.ts.model.entity.WfParameter;
import com.ts.service.DefinitionService;
import com.ts.service.ExecutionService;
import com.ts.service.ParamsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

/**
 * Tests for the B3 fix in {@link NotificationManager#sendWfNotification}:
 * when {@code ExecutionService.getWfInstance} returns {@code null} (now possible
 * after the B3 change), the notification preparation must bail out gracefully
 * instead of NPE-ing on the null instance.
 */
@ExtendWith(MockitoExtension.class)
class NotificationManagerTest {

    @Mock
    private DefinitionService definitionService;
    @Mock
    private ExecutionService executionService;
    @Mock
    private ParamsService paramsService;
    @Mock
    private NotificationFactory notificationFactory;

    private NotificationManager manager;

    @BeforeEach
    void setUp() {
        manager = new NotificationManager(definitionService, executionService, paramsService, notificationFactory);
    }

    @Test
    void sendWfNotification_nullInstance_noNpeAndNoDispatch() {
        WfParameter wfParam = new WfParameter();
        wfParam.setWfInstanceId("wf-inst-1");
        wfParam.setPvalue("{\"workflowName\":\"WF\"}");
        when(paramsService.getWfParamsByInstanceId("wf-inst-1")).thenReturn(wfParam);
        // B3: getWfInstance now returns null for a missing instance
        when(executionService.getWfInstance("wf-inst-1")).thenReturn(null);

        assertDoesNotThrow(() -> manager.sendWfNotification("wf-inst-1", Constants.TYPE_WORKFLOW_START));

        // No notification channel was even consulted
        verify(notificationFactory, never()).getNotificationService(anyString());
    }

    @Test
    void sendWfNotification_nullWfParam_doesNotPropagateException() {
        when(paramsService.getWfParamsByInstanceId("wf-inst-2")).thenReturn(null);

        // Existing defensive behavior: any internal failure is swallowed by the outer
        // try/catch so the notification path never breaks the caller's business flow.
        assertDoesNotThrow(() -> manager.sendWfNotification("wf-inst-2", Constants.TYPE_WORKFLOW_END));
        verify(notificationFactory, never()).getNotificationService(anyString());
    }

    @Test
    void sendWfNotification_presentInstance_withoutFlags_noDispatch() {
        WfParameter wfParam = new WfParameter();
        wfParam.setWfInstanceId("wf-inst-3");
        wfParam.setPvalue("{\"workflowName\":\"WF\"}");
        when(paramsService.getWfParamsByInstanceId("wf-inst-3")).thenReturn(wfParam);

        WfInstance instance = new WfInstance();
        instance.setWfInstanceId("wf-inst-3");
        instance.setStartDate(new Date());
        when(executionService.getWfInstance("wf-inst-3")).thenReturn(instance);

        assertDoesNotThrow(() -> manager.sendWfNotification("wf-inst-3", Constants.TYPE_WORKFLOW_START));

        // No notice_email / notice_webhook flags -> no channel consulted
        verify(notificationFactory, never()).getNotificationService(anyString());
    }
}
