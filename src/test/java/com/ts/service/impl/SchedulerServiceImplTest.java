package com.ts.service.impl;

import com.ts.common.constants.Constants;
import com.ts.core.scheduler.Scheduler;
import com.ts.dao.WfDefinitionDao;
import com.ts.dao.WfSchedulerDao;
import com.ts.exception.WorkflowNotFoundException;
import com.ts.exception.WorkflowValidationException;
import com.ts.model.entity.WfDefinition;
import com.ts.model.entity.WfScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for the B2 fixes in {@link SchedulerServiceImpl}:
 * <ul>
 *   <li>cron validation now uses the project's strict {@code com.ts.core.scheduler.CronExpression}
 *       (same parser the runtime {@code CronScheduler} uses) — expressions without exactly one
 *       '?' on day-of-month/day-of-week are rejected up front.</li>
 *   <li>{@code createScheduler}/{@code updateScheduler} check the return value of
 *       {@code cronScheduler.schedule()/updateSchedule()} and roll back the DB record +
 *       throw a clear {@link WorkflowValidationException} on failure, instead of silently
 *       leaving an "enabled but never scheduled" state.</li>
 *   <li>{@code createScheduler} checks {@code createSchedule()} return value (fixes the
 *       pre-existing NPE when insert fails and the subsequent re-fetch returns null).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SchedulerServiceImplTest {

    private static final String WF_ID = "WF-001";
    private static final String CRON_VALID = "0 0 12 * * ?";
    private static final String CRON_VALID_2 = "0 30 9 ? * 1-5";

    @Mock
    private WfSchedulerDao wfSchedulerDao;
    @Mock
    private WfDefinitionDao wfDefinitionDao;
    @Mock
    private Scheduler cronScheduler;

    private SchedulerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SchedulerServiceImpl(wfSchedulerDao, wfDefinitionDao);
        service.setScheduler(cronScheduler);
    }

    private WfDefinition mockWorkflowExists() {
        WfDefinition wfDef = new WfDefinition();
        wfDef.setWorkflowId(WF_ID);
        when(wfDefinitionDao.getWorkflow(WF_ID)).thenReturn(wfDef);
        return wfDef;
    }

    // ==================== createScheduler — success path ====================

    @Test
    void createScheduler_schedulesWhenRuntimeRegistrationSucceeds() {
        mockWorkflowExists();
        when(wfSchedulerDao.getSchedule(WF_ID)).thenReturn(null);
        when(wfSchedulerDao.createSchedule(any())).thenReturn(true);
        when(cronScheduler.schedule(WF_ID, CRON_VALID)).thenReturn(true);

        boolean result = service.createScheduler(WF_ID, CRON_VALID, true, "admin");

        assertTrue(result);
        verify(cronScheduler).schedule(WF_ID, CRON_VALID);
        verify(cronScheduler, never()).unSchedule(anyString());
        verify(wfSchedulerDao, never()).deleteSchedule(anyString());
    }

    @Test
    void createScheduler_disabled_unschedulesInsteadOfScheduling() {
        mockWorkflowExists();
        when(wfSchedulerDao.getSchedule(WF_ID)).thenReturn(null);
        when(wfSchedulerDao.createSchedule(any())).thenReturn(true);

        boolean result = service.createScheduler(WF_ID, CRON_VALID, false, "admin");

        assertTrue(result);
        verify(cronScheduler).unSchedule(WF_ID);
        verify(cronScheduler, never()).schedule(anyString(), anyString());
    }

    // ==================== createScheduler — runtime registration failure (B2) ====================

    @Test
    void createScheduler_runtimeScheduleFails_rollsBackDbRecordAndThrows() {
        mockWorkflowExists();
        when(wfSchedulerDao.getSchedule(WF_ID)).thenReturn(null);
        when(wfSchedulerDao.createSchedule(any())).thenReturn(true);
        // Simulate e.g. a cron with no valid fire time, or scheduler capacity full
        when(cronScheduler.schedule(WF_ID, CRON_VALID)).thenReturn(false);

        assertThrows(WorkflowValidationException.class,
                () -> service.createScheduler(WF_ID, CRON_VALID, true, "admin"));

        // The just-created DB record must be rolled back so DB and in-memory stay consistent
        verify(wfSchedulerDao).deleteSchedule(WF_ID);
    }

    // ==================== createScheduler — DB insert failure (NPE hardening) ====================

    @Test
    void createScheduler_insertFails_throwsClearExceptionWithoutNpe() {
        mockWorkflowExists();
        when(wfSchedulerDao.getSchedule(WF_ID)).thenReturn(null);
        when(wfSchedulerDao.createSchedule(any())).thenReturn(false);

        assertThrows(WorkflowValidationException.class,
                () -> service.createScheduler(WF_ID, CRON_VALID, true, "admin"));

        // Nothing should have been registered in the runtime scheduler
        verify(cronScheduler, never()).schedule(anyString(), anyString());
        verify(wfSchedulerDao, never()).deleteSchedule(anyString());
    }

    // ==================== createScheduler — duplicate / not-found / invalid cron ====================

    @Test
    void createScheduler_alreadyExists_throws() {
        mockWorkflowExists();
        when(wfSchedulerDao.getSchedule(WF_ID)).thenReturn(new WfScheduler());

        assertThrows(WorkflowValidationException.class,
                () -> service.createScheduler(WF_ID, CRON_VALID, true, "admin"));
    }

    @Test
    void createScheduler_workflowNotFound_throws() {
        when(wfDefinitionDao.getWorkflow(WF_ID)).thenReturn(null);

        assertThrows(WorkflowNotFoundException.class,
                () -> service.createScheduler(WF_ID, CRON_VALID, true, "admin"));
    }

    @Test
    void createScheduler_rejectsCronWithoutQuestionMark() {
        // Unified strict parser: no '?' on day-of-month/day-of-week is rejected (Quartz-style
        // "0 0 12 * * *" would previously pass the log4j parser but fail at runtime).
        assertThrows(IllegalArgumentException.class,
                () -> service.createScheduler(WF_ID, "0 0 12 * * *", true, "admin"));
        verifyNoInteractions(wfSchedulerDao);
    }

    @Test
    void createScheduler_rejectsBlankCron() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createScheduler(WF_ID, "", true, "admin"));
    }

    @Test
    void createScheduler_rejectsBlankWorkflowId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createScheduler("", CRON_VALID, true, "admin"));
    }

    // ==================== updateScheduler — success path ====================

    @Test
    void updateScheduler_updatesWhenRuntimeSucceeds() {
        WfScheduler existing = existingSchedule(CRON_VALID, Constants.FLAG_YES);
        when(wfSchedulerDao.getSchedule(WF_ID)).thenReturn(existing);
        when(wfSchedulerDao.updateSchedule(any())).thenReturn(true);
        when(cronScheduler.updateSchedule(WF_ID, CRON_VALID_2)).thenReturn(true);

        boolean result = service.updateScheduler(WF_ID, CRON_VALID_2, true, "admin");

        assertTrue(result);
        verify(cronScheduler).updateSchedule(WF_ID, CRON_VALID_2);
        // only the new-value update, no rollback
        verify(wfSchedulerDao, times(1)).updateSchedule(any());
    }

    @Test
    void updateScheduler_disable_unschedules() {
        WfScheduler existing = existingSchedule(CRON_VALID, Constants.FLAG_YES);
        when(wfSchedulerDao.getSchedule(WF_ID)).thenReturn(existing);
        when(wfSchedulerDao.updateSchedule(any())).thenReturn(true);

        boolean result = service.updateScheduler(WF_ID, CRON_VALID, false, "admin");

        assertTrue(result);
        verify(cronScheduler).unSchedule(WF_ID);
        verify(cronScheduler, never()).updateSchedule(anyString(), anyString());
    }

    // ==================== updateScheduler — runtime update failure (B2) ====================

    @Test
    void updateScheduler_runtimeUpdateFails_rollsBackDbRecordAndThrows() {
        WfScheduler existing = existingSchedule(CRON_VALID, Constants.FLAG_YES);
        when(wfSchedulerDao.getSchedule(WF_ID)).thenReturn(existing);
        when(wfSchedulerDao.updateSchedule(any())).thenReturn(true);
        when(cronScheduler.updateSchedule(WF_ID, CRON_VALID_2)).thenReturn(false);

        assertThrows(WorkflowValidationException.class,
                () -> service.updateScheduler(WF_ID, CRON_VALID_2, true, "admin"));

        // Second update must roll the record back to the OLD cron
        ArgumentCaptor<WfScheduler> captor = ArgumentCaptor.forClass(WfScheduler.class);
        verify(wfSchedulerDao, times(2)).updateSchedule(captor.capture());
        assertEquals(CRON_VALID, captor.getAllValues().get(1).getCron());
        assertEquals(Constants.FLAG_YES, captor.getAllValues().get(1).getEnableFlag());
    }

    // ==================== updateScheduler — not found ====================

    @Test
    void updateScheduler_notScheduled_throws() {
        when(wfSchedulerDao.getSchedule(WF_ID)).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> service.updateScheduler(WF_ID, CRON_VALID_2, true, "admin"));
    }

    // ==================== deleteScheduler ====================

    @Test
    void deleteScheduler_existing_unschedulesAndDeletes() {
        when(wfSchedulerDao.getSchedule(WF_ID)).thenReturn(new WfScheduler());
        when(wfSchedulerDao.deleteSchedule(WF_ID)).thenReturn(true);

        assertTrue(service.deleteScheduler(WF_ID));
        verify(cronScheduler).unSchedule(WF_ID);
        verify(wfSchedulerDao).deleteSchedule(WF_ID);
    }

    @Test
    void deleteScheduler_notScheduled_throws() {
        when(wfSchedulerDao.getSchedule(WF_ID)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.deleteScheduler(WF_ID));
        verify(wfSchedulerDao, never()).deleteSchedule(anyString());
    }

    private WfScheduler existingSchedule(String cron, String enableFlag) {
        WfScheduler s = new WfScheduler();
        s.setWorkflowId(WF_ID);
        s.setCron(cron);
        s.setEnableFlag(enableFlag);
        return s;
    }
}
