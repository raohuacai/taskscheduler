/*
 * Description: Singleton coordinator for named workflow events using in-memory CountDownLatch registry
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.event;

import com.ts.model.entity.TsEvent;
import com.ts.service.EventService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * EventManager – singleton coordinator for the event subsystem.
 *
 * Responsibilities:
 *   1. Single entry point for all event state changes; DB is always written
 *      before in-memory structures are updated (DB is the source of truth).
 *   2. Maintains an in-memory latch registry so EventTask threads wake up in
 *      O(1) time without polling the database.
 *   3. Caches trigger payloads so EventTask avoids a second DB round-trip
 *      after being woken up.
 *   4. Handles the race where triggerEvent() arrives before EventTask reaches
 *      action() (early-trigger path).
 *
 * Thread-safety:
 *   - {@code latches} and {@code payloads} use ConcurrentHashMap.
 *   - triggerEvent() writes to {@code payloads} BEFORE releasing the latch,
 *     so registerLatch()'s double-check always sees a consistent state.
 *   - registerLatch() performs a double-check on {@code payloads} after
 *     putIfAbsent to close the narrow window between the initial check and
 *     the map insertion (see inline comments).
 *
 * Cleanup policy:
 *   unregisterLatch() removes entries from BOTH {@code latches} AND
 *   {@code payloads}. It is called by EventTask.cleanupResources() on every
 *   exit path (success, timeout, cancel), so neither map can leak.
 *   consumePayload() also removes the payload entry on the success path;
 *   the subsequent unregisterLatch() call is then a no-op for payloads.
 *
 * Lifecycle (called from Main.java):
 *   EventManager.initialize(eventService)  – once at startup
 *   EventManager.getInstance()             – everywhere else
 *
 * Author: Rock Rao
 * Date: 2025
 * Version: 1.0
 */
public class EventManager {

    private static final Logger logger = LogManager.getLogger(EventManager.class);

    // ── TriggerResult enum ────────────────────────────────────────────────────

    /**
     * Return value of {@link #triggerEvent} that lets EventHandler produce
     * accurate HTTP status codes and messages.
     */
    public enum TriggerResult {
        /** Event triggered successfully; DB updated and latch released. */
        SUCCESS,
        /** No TS_EVENT row found for the given eventKey. */
        NOT_FOUND,
        /** Row exists but is already in TRIGGERED status. */
        ALREADY_TRIGGERED
    }

    // ── Singleton ──────────────────────────────────────────────────────────────

    private static volatile EventManager instance;

    private final EventService eventService;

    /**
     * eventKey -> CountDownLatch(1).
     * EventTask registers and awaits; triggerEvent() counts down then removes.
     */
    private final ConcurrentHashMap<String, CountDownLatch> latches = new ConcurrentHashMap<>();

    /**
     * eventKey -> payload string (never null; empty string when no payload supplied).
     *
     * Written by triggerEvent() BEFORE the latch is released, so
     * registerLatch()'s double-check can always detect an early trigger.
     * Removed by consumePayload() on the success path, or by
     * unregisterLatch() on timeout/cancel paths.
     */
    private final ConcurrentHashMap<String, String> payloads = new ConcurrentHashMap<>();

    private EventManager(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Initialise the singleton. Must be called once by Main during startup,
     * after EventService is ready and before WorkflowEngine starts.
     */
    public static void initialize(EventService eventService) {
        if (instance == null) {
            synchronized (EventManager.class) {
                if (instance == null) {
                    instance = new EventManager(eventService);
                    logger.info("EventManager initialised");
                }
            }
        }
    }

    /**
     * Return the singleton instance.
     *
     * @throws IllegalStateException if initialize() has not been called
     */
    public static EventManager getInstance() {
        EventManager mgr = instance;
        if (mgr == null) {
            throw new IllegalStateException(
                    "EventManager has not been initialised. "
                    + "Call EventManager.initialize(eventService) before use.");
        }
        return mgr;
    }

    // ── Core operations ────────────────────────────────────────────────────────

    /**
     * Create a NEW status event record in TS_EVENT.
     * Called by upstream tasks (e.g. HttpTask.postAction).
     */
    public TsEvent createEvent(String eventKey, String tpwfInstanceId, String taskId) {
        return eventService.createEvent(eventKey, tpwfInstanceId, taskId);
    }

    /**
     * Find an event record by its globally unique event key.
     * Returns null if not found.
     */
    public TsEvent findByEventKey(String eventKey) {
        return eventService.findByEventKey(eventKey);
    }

    /**
     * Find an event record by logical composite key (tpwfInstanceId, taskId).
     * Returns null if not found.
     */
    public TsEvent findByTpwfAndTaskId(String tpwfInstanceId, String taskId) {
        return eventService.findByTpwfAndTaskId(tpwfInstanceId, taskId);
    }

    /**
     * Transition NEW -> WAITING in the DB.
     * Called by EventTask.preAction() once the task is ready to block.
     *
     * @return true if updated; false means the row is likely already TRIGGERED
     */
    public boolean markWaiting(String eventKey) {
        return eventService.markWaiting(eventKey);
    }

    /**
     * Register an in-memory latch for the given eventKey.
     * Called by EventTask.action() just before it calls {@code latch.await()}.
     *
     * Early-trigger fast path:
     *   If triggerEvent() already ran, {@code payloads} will contain the key.
     *   A CountDownLatch(0) is returned so EventTask's await() returns immediately.
     *
     * Double-check pattern (closes the narrow race window):
     *   1. Pre-check  {@code payloads} before putIfAbsent – catches the common case.
     *   2. putIfAbsent registers the latch atomically.
     *   3. Post-check {@code payloads} after putIfAbsent – catches the rare window
     *      where triggerEvent() ran between steps 1 and 2.
     *      If the post-check fires, remove the just-inserted latch and return an
     *      instant latch; triggerEvent() either already removed it or will find
     *      nothing to remove (both are safe).
     *
     * @param eventKey event key
     * @return CountDownLatch that EventTask will await on
     */
    public CountDownLatch registerLatch(String eventKey) {
        // Pre-check: trigger already arrived before registerLatch was called
        if (payloads.containsKey(eventKey)) {
            logger.info("registerLatch: key='{}' already triggered (pre-check), "
                    + "returning instant latch", eventKey);
            return new CountDownLatch(0);
        }

        CountDownLatch newLatch  = new CountDownLatch(1);
        CountDownLatch existing  = latches.putIfAbsent(eventKey, newLatch);
        CountDownLatch registered = (existing != null) ? existing : newLatch;

        // Post-check: trigger may have raced in between the pre-check and putIfAbsent
        if (payloads.containsKey(eventKey)) {
            latches.remove(eventKey, registered);   // defensive remove; idempotent
            logger.info("registerLatch: key='{}' triggered during registration window "
                    + "(post-check), returning instant latch", eventKey);
            return new CountDownLatch(0);
        }

        logger.debug("registerLatch: latch registered for key='{}'", eventKey);
        return registered;
    }

    /**
     * Fire the trigger for the given eventKey.
     *
     * Execution order (must not be reordered – see thread-safety comments in class javadoc):
     *   1. Validate current DB status  → produce an accurate TriggerResult.
     *   2. Write TRIGGERED status to DB (source of truth).
     *   3. Cache payload in {@code payloads} BEFORE releasing the latch,
     *      so registerLatch()'s double-check always observes the payload.
     *   4. Release the in-memory latch to wake up any waiting EventTask thread.
     *
     * Called by EventHandler on POST /api/event/trigger.
     *
     * @param eventKey    event key to trigger
     * @param payload     JSON payload from the API caller (null treated as empty)
     * @param triggeredBy username of the API caller (from JWT)
     * @return TriggerResult indicating the outcome
     */
    public TriggerResult triggerEvent(String eventKey, String payload, String triggeredBy) {
        // Step 1: validate current DB state for accurate result reporting
        TsEvent current = eventService.findByEventKey(eventKey);
        if (current == null) {
            logger.warn("triggerEvent: event not found for key='{}'", eventKey);
            return TriggerResult.NOT_FOUND;
        }
        if ("TRIGGERED".equals(current.getStatus())) {
            logger.warn("triggerEvent: event already TRIGGERED for key='{}'", eventKey);
            return TriggerResult.ALREADY_TRIGGERED;
        }

        // Step 2: write DB (authoritative state update)
        String safePayload = (payload != null) ? payload : "";
        boolean dbUpdated = eventService.markTriggered(eventKey, safePayload, triggeredBy);
        if (!dbUpdated) {
            // Row slipped to TRIGGERED between our read and the update (concurrent trigger race).
            logger.warn("triggerEvent: DB update returned 0 rows for key='{}' "
                    + "(concurrent trigger race)", eventKey);
            return TriggerResult.ALREADY_TRIGGERED;
        }

        // Step 3: cache payload BEFORE releasing latch
        payloads.put(eventKey, safePayload);

        // Step 4: release the latch to wake up EventTask
        CountDownLatch latch = latches.remove(eventKey);
        if (latch != null) {
            latch.countDown();
            logger.info("triggerEvent: latch released for key='{}', by='{}'", eventKey, triggeredBy);
        } else {
            logger.info("triggerEvent: no in-memory waiter for key='{}' "
                    + "(EventTask not yet at action(), or already timed out). "
                    + "Payload cached for early-trigger path.", eventKey);
        }

        return TriggerResult.SUCCESS;
    }

    /**
     * Retrieve and remove the cached payload after the latch is released.
     * Called once by EventTask.action() on the success path.
     *
     * Falls back to a DB read if the payload is not in cache (e.g. after a
     * JVM restart where the in-memory map was lost but the DB row survived).
     *
     * @param eventKey event key
     * @return payload string; empty string if the trigger caller sent no payload
     */
    public String consumePayload(String eventKey) {
        String cached = payloads.remove(eventKey);
        if (cached != null) {
            logger.debug("consumePayload: payload consumed from cache for key='{}'", eventKey);
            return cached;
        }
        // Fallback: DB read (JVM restart / recovery edge case)
        logger.info("consumePayload: cache miss for key='{}', falling back to DB read", eventKey);
        TsEvent event = eventService.findByEventKey(eventKey);
        if (event != null && event.getPayload() != null) {
            return event.getPayload();
        }
        return "";
    }

    /**
     * Remove both the latch entry and the payload cache entry for the given eventKey.
     *
     * Called by EventTask.cleanupResources() on every exit path:
     *   - Normal success  (consumePayload already removed payload; idempotent here)
     *   - Timeout         (payload must be cleaned up to prevent memory leak)
     *   - Cancel          (payload must be cleaned up to prevent memory leak)
     *
     * This is the single reliable cleanup point for non-success paths.
     */
    public void unregisterLatch(String eventKey) {
        latches.remove(eventKey);
        payloads.remove(eventKey);   // idempotent if consumePayload already removed it
        logger.info("unregisterLatch: cleaned up latch and payload cache for key='{}'", eventKey);
    }
}
