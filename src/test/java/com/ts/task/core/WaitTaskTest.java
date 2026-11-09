package com.ts.task.core;

import com.ts.common.constants.Constants;
import com.ts.exception.TaskExecutionException;
import com.ts.service.impl.TaskLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/**
 * Tests for {@link WaitTask}: flexible target-time parsing (all supported
 * formats), offset application, invalid input rejection, and the wait/skip
 * behaviour of {@code action}.
 */
@ExtendWith(MockitoExtension.class)
class WaitTaskTest {

    private Map<String, Object> params(String targetTime) {
        Map<String, Object> p = new HashMap<>();
        p.put(Constants.PARAM_WAIT_TARGET_TIME, targetTime);
        p.put(Constants.PARAM_TASK_INSTANCE, "t1");
        p.put(Constants.PARAM_TASK_NAME, "T");
        return p;
    }

    private WaitTask taskAfterPreAction(String targetTime) throws Exception {
        WaitTask t = new WaitTask();
        t.preAction(params(targetTime));
        return t;
    }

    private LocalDateTime deadline(WaitTask t) throws Exception {
        Field f = WaitTask.class.getDeclaredField("deadline");
        f.setAccessible(true);
        return (LocalDateTime) f.get(t);
    }

    // ==================== target-time parsing ====================

    @Test
    void preAction_fullDateTime_parses() throws Exception {
        assertEquals(LocalDateTime.of(2026, 8, 11, 10, 30, 0),
                deadline(taskAfterPreAction("2026-08-11 10:30:00")));
    }

    @Test
    void preAction_datetimeWithoutSeconds_parses() throws Exception {
        assertEquals(LocalDateTime.of(2026, 8, 11, 10, 30, 0),
                deadline(taskAfterPreAction("2026-08-11 10:30")));
    }

    @Test
    void preAction_dateOnly_parsesAtMidnight() throws Exception {
        assertEquals(LocalDateTime.of(2026, 8, 11, 0, 0, 0),
                deadline(taskAfterPreAction("2026-08-11")));
    }

    @Test
    void preAction_timeOnly_usesToday() throws Exception {
        String time = "10:30:00";
        LocalDateTime expected = LocalDateTime.of(LocalDate_now(), LocalTime.of(10, 30, 0));
        assertEquals(expected, deadline(taskAfterPreAction(time)));
    }

    @Test
    void preAction_hourOnly_parses() throws Exception {
        // "10" -> today 10:00:00
        LocalDateTime expected = LocalDateTime.of(LocalDate_now(), LocalTime.of(10, 0, 0));
        assertEquals(expected, deadline(taskAfterPreAction("10")));
    }

    @Test
    void preAction_invalidFormat_throws() {
        assertThrows(TaskExecutionException.class, () -> taskAfterPreAction("not-a-time"));
    }

    @Test
    void preAction_offsetSeconds_appliedToDeadline() throws Exception {
        Map<String, Object> p = params("2026-08-11 10:00:00");
        p.put(Constants.PARAM_WAIT_OFFSET_SECONDS, "900");
        WaitTask t = new WaitTask();
        t.preAction(p);
        assertEquals(LocalDateTime.of(2026, 8, 11, 10, 15, 0), deadline(t));
    }

    // ==================== action ====================

    @Test
    void action_deadlineAlreadyPassed_returnsImmediately() throws Exception {
        try (MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            // preAction parses a past time -> deadline in the past -> action skips
            WaitTask t = taskAfterPreAction("2020-01-01 00:00:00");
            assertDoesNotThrow(() -> t.action(params("2020-01-01 00:00:00")));
        }
    }

    @Test
    void action_waitsUntilDeadlineAndCompletes() throws Exception {
        try (MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            String future = LocalDateTime.now().plusSeconds(1)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            WaitTask t = taskAfterPreAction(future);
            assertDoesNotThrow(() -> t.action(params(future)));
        }
    }

    // small helper (java.time.LocalDate.now() is fine in tests; kept explicit)
    private static java.time.LocalDate LocalDate_now() {
        return java.time.LocalDate.now();
    }
}
