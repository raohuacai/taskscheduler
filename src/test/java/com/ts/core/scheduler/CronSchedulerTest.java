package com.ts.core.scheduler;

import com.ts.core.engine.WorkflowEngine;
import com.ts.core.timewheel.TimeWheel;
import com.ts.dao.WfSchedulerDao;
import com.ts.model.entity.WfScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link CronScheduler}: schedule/unSchedule/updateSchedule/refresh,
 * plus the {@code ScheduledWorkflowTask} run loop (execute, skip-if-running, and
 * the single retry policy).
 */
@ExtendWith(MockitoExtension.class)
class CronSchedulerTest {

    private static final String WF_ID = "WF-1";
    private static final String CRON = "0 0 12 * * ?";

    @Mock
    private WfSchedulerDao wfSchedulerDao;
    @Mock
    private WorkflowEngine workflowEngine;
    @Mock
    private TimeWheel timeWheel;

    private CronScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new CronScheduler(wfSchedulerDao, workflowEngine, timeWheel);
    }

    // ==================== schedule ====================

    @Test
    void schedule_validCron_registersInTimeWheel() {
        when(timeWheel.addTask(any(), anyLong(), any())).thenReturn("tw-1");

        boolean ok = scheduler.schedule(WF_ID, CRON);

        assertTrue(ok);
        verify(timeWheel).addTask(any(), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void schedule_duplicateWorkflow_returnsFalse() {
        when(timeWheel.addTask(any(), anyLong(), any())).thenReturn("tw-1");
        assertTrue(scheduler.schedule(WF_ID, CRON));
        // second registration of the same workflow is rejected
        assertFalse(scheduler.schedule(WF_ID, "0 0 13 * * ?"));
    }

    @Test
    void schedule_nullArguments_returnsFalse() {
        assertFalse(scheduler.schedule(null, CRON));
        assertFalse(scheduler.schedule(WF_ID, null));
    }

    @Test
    void schedule_invalidCron_returnsFalse() {
        assertFalse(scheduler.schedule(WF_ID, "not-a-cron"));
        verify(timeWheel, never()).addTask(any(), anyLong(), any());
    }

    // ==================== unSchedule ====================

    @Test
    void unSchedule_existingWorkflow_removesTask() {
        when(timeWheel.addTask(any(), anyLong(), any())).thenReturn("tw-1");
        scheduler.schedule(WF_ID, CRON);

        boolean ok = scheduler.unSchedule(WF_ID);

        assertTrue(ok);
        verify(timeWheel).removeTask("tw-1");
    }

    @Test
    void unSchedule_unknownWorkflow_returnsFalse() {
        assertFalse(scheduler.unSchedule("unknown"));
    }

    // ==================== updateSchedule ====================

    @Test
    void updateSchedule_replacesTask() {
        when(timeWheel.addTask(any(), anyLong(), any())).thenReturn("tw-1");
        scheduler.schedule(WF_ID, CRON);
        when(timeWheel.addTask(any(), anyLong(), any())).thenReturn("tw-2");

        boolean ok = scheduler.updateSchedule(WF_ID, "0 0 13 * * ?");

        assertTrue(ok);
        verify(timeWheel).removeTask("tw-1");
        // one registration for the original schedule + one for the updated schedule
        verify(timeWheel, times(2)).addTask(any(), anyLong(), any());
    }

    @Test
    void updateSchedule_invalidCron_returnsFalse() {
        assertFalse(scheduler.updateSchedule(WF_ID, "bad-cron"));
        verify(timeWheel, never()).addTask(any(), anyLong(), any());
    }

    // ==================== refreshAllSchedules ====================

    @Test
    void refreshAllSchedules_loadsActiveSchedulesFromDb() {
        WfScheduler s1 = new WfScheduler();
        s1.setWorkflowId("WF-A");
        s1.setCron(CRON);
        WfScheduler s2 = new WfScheduler();
        s2.setWorkflowId("WF-B");
        s2.setCron(CRON);
        when(wfSchedulerDao.getAllActiveSchedules()).thenReturn(Arrays.asList(s1, s2));
        when(timeWheel.addTask(any(), anyLong(), any())).thenReturn("tw-a", "tw-b");

        scheduler.refreshAllSchedules();

        verify(timeWheel, times(2)).addTask(any(), anyLong(), any());
    }

    @Test
    void refreshAllSchedules_invalidCronEntry_isSkipped() {
        WfScheduler bad = new WfScheduler();
        bad.setWorkflowId("WF-BAD");
        bad.setCron("not-a-cron");
        WfScheduler good = new WfScheduler();
        good.setWorkflowId("WF-GOOD");
        good.setCron(CRON);
        when(wfSchedulerDao.getAllActiveSchedules()).thenReturn(Arrays.asList(bad, good));
        when(timeWheel.addTask(any(), anyLong(), any())).thenReturn("tw-good");

        scheduler.refreshAllSchedules();

        verify(timeWheel, times(1)).addTask(any(), anyLong(), any());
    }

    // ==================== ScheduledWorkflowTask.run ====================

    private Object newScheduledTask(String workflowId, String cron) throws Exception {
        Class<?> clazz = Class.forName("com.ts.core.scheduler.CronScheduler$ScheduledWorkflowTask");
        Constructor<?> ctor = clazz.getDeclaredConstructor(
                CronScheduler.class, String.class, CronExpression.class);
        ctor.setAccessible(true);
        return ctor.newInstance(scheduler, workflowId, new CronExpression(cron));
    }

    private void runScheduledTask(Object task) throws Exception {
        task.getClass().getMethod("run").invoke(task);
    }

    @Test
    void scheduledTaskRun_executesAndReschedulesNextCron() throws Exception {
        when(timeWheel.addTask(any(), anyLong(), any())).thenReturn("tw-1");
        scheduler.schedule(WF_ID, CRON);
        when(workflowEngine.isRunningWorkflow(WF_ID)).thenReturn(false);

        runScheduledTask(newScheduledTask(WF_ID, CRON));

        verify(workflowEngine).start(eq(WF_ID), isNull());
        // original registration + next-cron reschedule
        verify(timeWheel, atLeast(2)).addTask(any(), anyLong(), any());
    }

    @Test
    void scheduledTaskRun_skipsWhenWorkflowAlreadyRunning() throws Exception {
        when(timeWheel.addTask(any(), anyLong(), any())).thenReturn("tw-1");
        scheduler.schedule(WF_ID, CRON);
        when(workflowEngine.isRunningWorkflow(WF_ID)).thenReturn(true);

        runScheduledTask(newScheduledTask(WF_ID, CRON));

        verify(workflowEngine, never()).start(anyString(), isNull());
        // still reschedules for the next cron tick
        verify(timeWheel, atLeast(2)).addTask(any(), anyLong(), any());
    }

    @Test
    void scheduledTaskRun_onFailureRetriesOnceInsteadOfRescheduling() throws Exception {
        when(timeWheel.addTask(any(), anyLong(), any())).thenReturn("tw-1");
        scheduler.schedule(WF_ID, CRON);
        when(workflowEngine.isRunningWorkflow(WF_ID)).thenReturn(false);
        doThrow(new RuntimeException("start failed")).when(workflowEngine).start(eq(WF_ID), isNull());
        when(timeWheel.addTask(any(), anyLong(), any())).thenReturn("tw-retry");

        runScheduledTask(newScheduledTask(WF_ID, CRON));

        // exactly the retry registration (original + retry), NOT a next-cron reschedule
        verify(timeWheel, times(2)).addTask(any(), anyLong(), any());
    }
}
