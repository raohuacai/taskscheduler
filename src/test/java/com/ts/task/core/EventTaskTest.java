package com.ts.task.core;

import com.ts.common.constants.Constants;
import com.ts.event.EventManager;
import com.ts.task.AbstractTask;
import com.ts.exception.TaskExecutionException;
import com.ts.exception.TaskTimeoutException;
import com.ts.model.entity.TsEvent;
import com.ts.service.EventService;
import com.ts.service.impl.TaskLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link EventTask}: event-key resolution (parameter vs composite
 * lookup), already-triggered fast path, and the waiting/timeout behaviour.
 */
@ExtendWith(MockitoExtension.class)
class EventTaskTest {

    @Mock
    private EventService eventService;

    @BeforeEach
    void resetEventManager() throws Exception {
        Field f = EventManager.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
        EventManager.initialize(eventService);
    }

    private Map<String, Object> params() {
        Map<String, Object> p = new HashMap<>();
        p.put(Constants.PARAM_WORKFLOW_INSTANCE, "inst-1");
        p.put(Constants.PARAM_TOP_PARENT_WORKFLOW_INSTANCE, "inst-1");
        p.put(Constants.PARAM_TASK_ID, "task1");
        p.put(Constants.PARAM_TASK_NAME, "T");
        p.put(Constants.PARAM_TASK_INSTANCE, "task-inst-1");
        p.put(Constants.PARAM_TASK_TYPE, "EVENT");
        p.put(Constants.PARAM_TASK_TIMEOUT, "60");
        return p;
    }

    private TsEvent event(String status, String key) {
        TsEvent e = new TsEvent();
        e.setEventKey(key);
        e.setStatus(status);
        return e;
    }

    private boolean alreadyTriggered(EventTask t) throws Exception {
        Field f = EventTask.class.getDeclaredField("alreadyTriggered");
        f.setAccessible(true);
        return f.getBoolean(t);
    }

    // timeout/taskId are populated by AbstractTask.initialize() inside execute();
    // since these tests call preAction/action directly, set them explicitly.
    private void setField(Object target, String field, Object value) throws Exception {
        Field f = AbstractTask.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    // ==================== preAction: event-key resolution ====================

    @Test
    void preAction_eventKeyFromParameter_looksUpByKeyAndMarksWaiting() throws Exception {
        try (MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            when(eventService.findByEventKey("evt-1")).thenReturn(event("NEW", "evt-1"));
            when(eventService.markWaiting("evt-1")).thenReturn(true);
            Map<String, Object> p = params();
            p.put(Constants.PARAM_EVENT_KEY, "evt-1");

            EventTask task = new EventTask();
            task.preAction(p);

            verify(eventService).findByEventKey("evt-1");
            verify(eventService).markWaiting("evt-1");
        }
    }

    @Test
    void preAction_noEventKey_fallsBackToCompositeLookup() throws Exception {
        try (MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            when(eventService.findByTpwfAndTaskId("inst-1", "task1"))
                    .thenReturn(event("NEW", "derived-key"));
            when(eventService.markWaiting("derived-key")).thenReturn(true);

            EventTask task = new EventTask();
            setField(task, "taskId", "task1");   // initialize() normally sets this
            task.preAction(params());

            verify(eventService).findByTpwfAndTaskId("inst-1", "task1");
            verify(eventService).markWaiting("derived-key");
        }
    }

    @Test
    void preAction_alreadyTriggered_setsFastPathFlag() throws Exception {
        try (MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            when(eventService.findByEventKey("evt-1")).thenReturn(event("TRIGGERED", "evt-1"));
            Map<String, Object> p = params();
            p.put(Constants.PARAM_EVENT_KEY, "evt-1");

            EventTask task = new EventTask();
            task.preAction(p);

            assertTrue(alreadyTriggered(task));
            verify(eventService, never()).markWaiting(anyString());
        }
    }

    @Test
    void preAction_noEventRecord_throws() {
        try (MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            when(eventService.findByEventKey("evt-1")).thenReturn(null);
            Map<String, Object> p = params();
            p.put(Constants.PARAM_EVENT_KEY, "evt-1");

            EventTask task = new EventTask();
            assertThrows(TaskExecutionException.class, () -> task.preAction(p));
        }
    }

    // ==================== action ====================

    @Test
    void action_alreadyTriggered_consumesPayloadWithoutBlocking() throws Exception {
        try (MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            // trigger the event first so payload is cached
            when(eventService.findByEventKey("evt-1")).thenReturn(event("NEW", "evt-1"));
            when(eventService.markTriggered("evt-1", "payload", "u")).thenReturn(true);
            EventManager.getInstance().triggerEvent("evt-1", "payload", "u");

            Map<String, Object> p = params();
            p.put(Constants.PARAM_EVENT_KEY, "evt-1");
            EventTask task = new EventTask();
            task.preAction(p);   // sees TRIGGERED? No — DB still NEW; but payload cache makes register instant
            task.action(p);
            // completes without throwing (latch count 0 fast path)
        }
    }

    @Test
    void action_timesOutWhenEventNeverFired() throws Exception {
        try (MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            when(eventService.findByEventKey("evt-1")).thenReturn(event("NEW", "evt-1"));
            when(eventService.markWaiting("evt-1")).thenReturn(true);
            Map<String, Object> p = params();
            p.put(Constants.PARAM_EVENT_KEY, "evt-1");
            p.put(Constants.PARAM_TASK_TIMEOUT, "1");   // 1s timeout

            EventTask task = new EventTask();
            setField(task, "timeout", 1);   // initialize() normally sets this
            task.preAction(p);

            assertThrows(TaskTimeoutException.class, () -> task.action(p));
        }
    }

    @Test
    void action_receivesEventAndStoresPayload() throws Exception {
        try (MockedStatic<TaskLogService> log = mockStatic(TaskLogService.class)) {
            when(eventService.findByEventKey("evt-1")).thenReturn(event("NEW", "evt-1"));
            when(eventService.markWaiting("evt-1")).thenReturn(true);
            when(eventService.markTriggered(anyString(), anyString(), anyString())).thenReturn(true);
            Map<String, Object> p = params();
            p.put(Constants.PARAM_EVENT_KEY, "evt-1");
            p.put(Constants.PARAM_TASK_TIMEOUT, "5");

            EventTask task = new EventTask();
            setField(task, "timeout", 5);   // initialize() normally sets this
            task.preAction(p);

            // fire the event on another thread so the latch releases
            Thread trigger = new Thread(() ->
                    EventManager.getInstance().triggerEvent("evt-1", "payload-1", "u"));
            trigger.start();

            assertDoesNotThrow(() -> task.action(p));
            trigger.join(3000);
        }
    }
}
