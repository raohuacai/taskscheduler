package com.ts.event;

import com.ts.model.entity.TsEvent;
import com.ts.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link EventManager}: latch registration (incl. the early-trigger
 * fast path), trigger semantics (NEW -> TRIGGERED, races, not-found), payload
 * consumption, and cleanup.
 *
 * <p>The singleton instance is reset before each test so each test binds a
 * fresh mock {@link EventService}.
 */
@ExtendWith(MockitoExtension.class)
class EventManagerTest {

    @Mock
    private EventService eventService;

    @BeforeEach
    void setUp() throws Exception {
        Field f = EventManager.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
        EventManager.initialize(eventService);
    }

    private TsEvent event(String status) {
        TsEvent e = new TsEvent();
        e.setEventKey("key");
        e.setStatus(status);
        return e;
    }

    // ==================== getInstance / initialize ====================

    @Test
    void getInstance_beforeInitialize_throws() throws Exception {
        Field f = EventManager.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);

        assertThrows(IllegalStateException.class, EventManager::getInstance);
    }

    // ==================== registerLatch ====================

    @Test
    void registerLatch_normal_returnsWaitingLatch() {
        CountDownLatch latch = EventManager.getInstance().registerLatch("key");
        assertNotNull(latch);
        assertEquals(1, latch.getCount());
    }

    @Test
    void registerLatch_alreadyTriggered_returnsInstantLatch() throws Exception {
        // fire the trigger first so the payload cache holds the key
        when(eventService.findByEventKey("key")).thenReturn(event("NEW"));
        when(eventService.markTriggered(anyString(), anyString(), anyString())).thenReturn(true);
        EventManager.getInstance().triggerEvent("key", "payload", "user");

        CountDownLatch latch = EventManager.getInstance().registerLatch("key");

        assertEquals(0, latch.getCount(), "early-trigger path must return a non-blocking latch");
    }

    // ==================== triggerEvent ====================

    @Test
    void triggerEvent_newEvent_successReleasesLatchAndCachesPayload() {
        when(eventService.findByEventKey("key")).thenReturn(event("NEW"));
        when(eventService.markTriggered("key", "payload", "user")).thenReturn(true);
        EventManager mgr = EventManager.getInstance();
        CountDownLatch latch = mgr.registerLatch("key");

        assertEquals(EventManager.TriggerResult.SUCCESS, mgr.triggerEvent("key", "payload", "user"));

        assertEquals(0, latch.getCount(), "waiting latch must be released");
        assertEquals("payload", mgr.consumePayload("key"));
    }

    @Test
    void triggerEvent_notFound_returnsNotFound() {
        when(eventService.findByEventKey("missing")).thenReturn(null);

        assertEquals(EventManager.TriggerResult.NOT_FOUND,
                EventManager.getInstance().triggerEvent("missing", "p", "u"));
        verify(eventService, never()).markTriggered(anyString(), anyString(), anyString());
    }

    @Test
    void triggerEvent_alreadyTriggered_returnsAlreadyTriggered() {
        when(eventService.findByEventKey("key")).thenReturn(event("TRIGGERED"));

        assertEquals(EventManager.TriggerResult.ALREADY_TRIGGERED,
                EventManager.getInstance().triggerEvent("key", "p", "u"));
    }

    @Test
    void triggerEvent_dbUpdateRace_returnsAlreadyTriggered() {
        when(eventService.findByEventKey("key")).thenReturn(event("NEW"));
        // row flipped to TRIGGERED between read and update
        when(eventService.markTriggered(anyString(), anyString(), anyString())).thenReturn(false);

        assertEquals(EventManager.TriggerResult.ALREADY_TRIGGERED,
                EventManager.getInstance().triggerEvent("key", "p", "u"));
    }

    // ==================== consumePayload ====================

    @Test
    void consumePayload_cacheHit_returnsCachedAndRemoves() {
        when(eventService.findByEventKey("key")).thenReturn(event("NEW"));
        when(eventService.markTriggered(anyString(), anyString(), anyString())).thenReturn(true);
        EventManager mgr = EventManager.getInstance();
        mgr.triggerEvent("key", "cached", "u");

        assertEquals("cached", mgr.consumePayload("key"));
        // second consume falls back to DB / empty
        when(eventService.findByEventKey("key")).thenReturn(event("TRIGGERED"));
        assertNotNull(mgr.consumePayload("key"));
    }

    @Test
    void consumePayload_cacheMiss_fallsBackToDbPayload() {
        TsEvent ev = event("TRIGGERED");
        ev.setPayload("db-payload");
        when(eventService.findByEventKey("key")).thenReturn(ev);

        assertEquals("db-payload", EventManager.getInstance().consumePayload("key"));
    }

    @Test
    void consumePayload_cacheMiss_noDbPayload_returnsEmpty() {
        when(eventService.findByEventKey("key")).thenReturn(event("TRIGGERED"));   // payload null

        assertEquals("", EventManager.getInstance().consumePayload("key"));
    }

    // ==================== cleanup / delegation ====================

    @Test
    void unregisterLatch_removesLatchAndPayload() {
        when(eventService.findByEventKey("key")).thenReturn(event("NEW"));
        when(eventService.markTriggered(anyString(), anyString(), anyString())).thenReturn(true);
        EventManager mgr = EventManager.getInstance();
        // cache the payload (simulating a completed trigger)
        mgr.triggerEvent("key", "payload", "u");
        mgr.unregisterLatch("key");

        // payload was cleaned -> a fresh register is a real waiting latch, not the instant path
        CountDownLatch fresh = mgr.registerLatch("key");
        assertEquals(1, fresh.getCount());
    }

    @Test
    void delegatesToEventService() {
        EventManager mgr = EventManager.getInstance();

        mgr.createEvent("key", "tpwf", "task");
        verify(eventService).createEvent("key", "tpwf", "task");

        mgr.markWaiting("key");
        verify(eventService).markWaiting("key");

        mgr.findByEventKey("key");
        verify(eventService).findByEventKey("key");

        mgr.findByTpwfAndTaskId("tpwf", "task");
        verify(eventService).findByTpwfAndTaskId("tpwf", "task");
    }
}
