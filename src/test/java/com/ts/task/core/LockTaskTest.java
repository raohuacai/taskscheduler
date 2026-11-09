package com.ts.task.core;

import com.ts.Main;
import com.ts.common.constants.Constants;
import com.ts.exception.TaskExecutionException;
import com.ts.exception.TaskTimeoutException;
import com.ts.service.LockService;
import com.ts.service.impl.TaskLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the B1 fix in {@link LockTask}: the retry loop compares elapsed time
 * (milliseconds) against the configured timeout (seconds). Before the fix,
 * {@code elapsedTime > timeout} compared ms against seconds, so a 3600s timeout
 * gave up after ~3.6s (i.e. 3600 ms). The fix compares {@code elapsedTime > timeout * 1000L}.
 *
 * <p>These tests exercise the full {@code execute()} path, stubbing the static
 * {@code Main.getLockService()} (the LockTask constructor pulls it statically)
 * and {@code TaskLogService.addTaskLog}.
 */
@ExtendWith(MockitoExtension.class)
class LockTaskTest {

    @Mock
    private LockService lockService;

    private Map<String, Object> baseParams(String timeoutSeconds) {
        Map<String, Object> p = new HashMap<>();
        p.put(Constants.PARAM_TASK_TIMEOUT, timeoutSeconds);
        p.put(Constants.LOCK_NAME, "mylock");
        p.put(Constants.LOCK_OWNER, "owner1");
        p.put(Constants.LOCK_ACTION, Constants.LOCK_ACQUIRE);
        p.put(Constants.PARAM_WORKFLOW_ID, "WF1");
        p.put(Constants.PARAM_WORKFLOW_NAME, "WF");
        p.put(Constants.PARAM_WORKFLOW_INSTANCE, "wf-inst-1");
        p.put(Constants.PARAM_TASK_ID, "task1");
        p.put(Constants.PARAM_TASK_NAME, "T");
        p.put(Constants.PARAM_TASK_INSTANCE, "task-inst-1");
        p.put(Constants.PARAM_TASK_TYPE, "LOCK");
        return p;
    }

    @Test
    void acquireLock_succeedsImmediately_completesNormally() {
        try (MockedStatic<Main> main = mockStatic(Main.class);
             MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            main.when(Main::getLockService).thenReturn(lockService);
            when(lockService.acquireLock("mylock", "owner1")).thenReturn(true);

            LockTask task = new LockTask();

            assertDoesNotThrow(() -> task.execute(baseParams("60")));
            verify(lockService).acquireLock("mylock", "owner1");
            // A normal acquire-then-complete must KEEP the lock (downstream work relies on it)
            verify(lockService, never()).releaseLock(anyString());
        }
    }

    /**
     * Regression for F4: a task cancelled while still WAITING for the lock never owned it.
     * releaseLock() deletes by lock_name (no owner check), so releasing here would delete
     * a lock currently held by another task/workflow.
     */
    @Test
    void cancelWhileWaiting_neverReleasesLock() {
        try (MockedStatic<Main> main = mockStatic(Main.class);
             MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            main.when(Main::getLockService).thenReturn(lockService);

            LockTask task = new LockTask();
            // First acquire attempt reports "lock held by someone else", then cancels the task.
            // cancel() sets the interrupt flag, so the retry sleep throws InterruptedException
            // immediately and execute() unwinds through cleanupResources().
            when(lockService.acquireLock("mylock", "owner1")).thenAnswer(inv -> {
                task.cancel();
                return false;
            });

            assertThrows(TaskExecutionException.class, () -> task.execute(baseParams("60")));
            verify(lockService).acquireLock("mylock", "owner1");
            verify(lockService, never()).releaseLock(anyString());
        }
    }

    /**
     * Guard against over-fixing: once the lock WAS acquired, a cancellation must still
     * release it (otherwise a cancelled workflow leaks the lock forever).
     */
    @Test
    void cancelAfterAcquire_releasesLock() {
        try (MockedStatic<Main> main = mockStatic(Main.class);
             MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            main.when(Main::getLockService).thenReturn(lockService);

            LockTask task = new LockTask();
            // Acquire succeeds, then the task is cancelled before the post-loop cancel check.
            when(lockService.acquireLock("mylock", "owner1")).thenAnswer(inv -> {
                task.cancel();
                return true;
            });

            assertThrows(TaskExecutionException.class, () -> task.execute(baseParams("60")));
            verify(lockService).acquireLock("mylock", "owner1");
            verify(lockService).releaseLock("mylock");
        }
    }

    @Test
    void acquireLock_neverAvailable_timesOutAfterSecondsNotMillis() {
        try (MockedStatic<Main> main = mockStatic(Main.class);
             MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            main.when(Main::getLockService).thenReturn(lockService);
            when(lockService.acquireLock(anyString(), anyString())).thenReturn(false);

            LockTask task = new LockTask();

            long start = System.currentTimeMillis();
            assertThrows(TaskTimeoutException.class, () -> task.execute(baseParams("1")));
            long elapsed = System.currentTimeMillis() - start;

            // F4 guard: a task that never acquired the lock must not release it on timeout
            verify(lockService, never()).releaseLock(anyString());

            // B1 fix: timeout=1 is ONE SECOND. The loop waits one DEFAULT_RETRY_INTERVAL
            // (5s) before the elapsed check trips, so elapsed is seconds-scale.
            // If the old bug were present (timeout treated as millis), it would throw
            // almost immediately (elapsed ~0-2ms).
            assertTrue(elapsed >= 2000,
                    "timeout must be treated as seconds, but elapsed=" + elapsed + "ms");
        }
    }

    @Test
    void acquireLock_lockIsFreeOnRetry_succeeds() {
        try (MockedStatic<Main> main = mockStatic(Main.class);
             MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            main.when(Main::getLockService).thenReturn(lockService);
            // first attempt fails, second succeeds
            when(lockService.acquireLock(anyString(), anyString()))
                    .thenReturn(false, true);

            LockTask task = new LockTask();

            assertDoesNotThrow(() -> task.execute(baseParams("60")));
            verify(lockService, times(2)).acquireLock("mylock", "owner1");
        }
    }

    @Test
    void releaseLock_action_releasesLockByName() {
        try (MockedStatic<Main> main = mockStatic(Main.class);
             MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            main.when(Main::getLockService).thenReturn(lockService);
            when(lockService.releaseLock("mylock")).thenReturn(true);

            LockTask task = new LockTask();
            Map<String, Object> params = baseParams("60");
            params.put(Constants.LOCK_ACTION, Constants.LOCK_RELEASE);

            assertDoesNotThrow(() -> task.execute(params));
            verify(lockService).releaseLock("mylock");
            verify(lockService, never()).acquireLock(anyString(), anyString());
        }
    }

    @Test
    void releaseLock_failure_stillCompletesTask() {
        try (MockedStatic<Main> main = mockStatic(Main.class);
             MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            main.when(Main::getLockService).thenReturn(lockService);
            when(lockService.releaseLock("mylock")).thenReturn(false);

            LockTask task = new LockTask();
            Map<String, Object> params = baseParams("60");
            params.put(Constants.LOCK_ACTION, Constants.LOCK_RELEASE);

            // release failing is logged as a warning, not a task failure
            assertDoesNotThrow(() -> task.execute(params));
            verify(lockService).releaseLock("mylock");
        }
    }
}
