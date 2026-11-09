/*
 * Description: Task type integrating with EventManager to trigger or wait on named workflow events
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.task.core;

import com.ts.common.constants.Constants;
import com.ts.common.utils.ParamUtils;
import com.ts.event.EventManager;
import com.ts.exception.TaskExecutionException;
import com.ts.exception.TaskTimeoutException;
import com.ts.model.entity.TsEvent;
import com.ts.service.impl.TaskLogService;
import com.ts.task.AbstractTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * EventTask – suspends the workflow until an external caller fires the event
 * via POST /api/event/trigger, then resumes execution.
 * <p>
 * ── preAction (eventKey resolution, two-priority strategy) ───────────────────
 * <p>
 * Priority 1 – Constants.PARAM_EVENT_KEY is present and non-empty in parameters:
 * Use that value as eventKey and look up the TS_EVENT record by eventKey directly.
 * Log clearly that the parameter-provided key is being used.
 * <p>
 * Priority 2 – Constants.PARAM_EVENT_KEY is absent or empty:
 * Look up the TS_EVENT record by (tpwfInstanceId, taskId) and derive eventKey
 * from the found record.
 * Log clearly that the composite-key lookup is being used.
 * <p>
 * If neither lookup finds a record, throw TaskExecutionException (upstream task
 * forgot to call EventManager.createEvent()).
 * <p>
 * After the record is located, react to current status:
 * TRIGGERED – set alreadyTriggered=true; action() will skip the latch.
 * NEW       – transition to WAITING (with race-condition re-check).
 * WAITING   – restored after JVM restart; action() re-registers the latch.
 * <p>
 * ── action ────────────────────────────────────────────────────────────────────
 * alreadyTriggered=true → consume payload and return immediately.
 * Otherwise → register latch and await(timeout, SECONDS).
 * Timeout  → throw TaskTimeoutException.
 * Cancel   → detect via canceled.get() after InterruptedException, throw TaskExecutionException.
 * <p>
 * ── cleanupResources ──────────────────────────────────────────────────────────
 * Always calls EventManager.unregisterLatch() to prevent latch and payload leaks
 * regardless of exit path (success / timeout / cancel / exception).
 * <p>
 * Consistency with other tasks:
 * - cancel / retry / restore handled entirely by WorkflowEngine / WorkflowInstance.
 * - Does NOT extend TimeoutTask; timeout controlled via latch.await().
 * - Uses saveParams() to persist payload into the task parameter table.
 * <p>
 * Author: Rock Rao
 * Date: 2025
 * Version: 1.0
 */
public class EventTask extends AbstractTask {

    private static final Logger logger = LogManager.getLogger(EventTask.class);

    // Resolved during preAction; used across all lifecycle methods.
    private String eventKey;

    // Set true in preAction when the DB record is already TRIGGERED,
    // allowing action() to skip the latch and return immediately.
    private volatile boolean alreadyTriggered = false;

    // Set true in action() after registerLatch() is called,
    // so cleanupResources() knows whether to call unregisterLatch().
    private volatile boolean latchRegistered = false;

    // ── preAction ─────────────────────────────────────────────────────────────

    @Override
    /**
      * Prepares resources and reads task parameters before the main action.
      * Called by {@link AbstractTask#execute} before {@link #action}.
      *
      * @param parameters  merged task parameter map supplied by the workflow engine
      * @throws Exception  if a required parameter is missing or resource initialisation fails
      */
    public void preAction(Map<String, Object> parameters) throws Exception {
        super.preAction(parameters);

        // tpwfInstanceId is needed for the composite-key lookup path.
        // It is always present when the workflow is running inside a sub-workflow chain;
        // for top-level workflows it equals the current workflowInstanceId.
        String tpwfInstanceId = ParamUtils.getParameter(
                parameters, Constants.PARAM_TOP_PARENT_WORKFLOW_INSTANCE, workflowInstanceId);

        // ── Step 1: locate the TS_EVENT record using two-priority strategy ────

        TsEvent event;

        String paramEventKey = ParamUtils.getParameter(
                parameters, Constants.PARAM_EVENT_KEY, null);

        if (paramEventKey != null && !paramEventKey.trim().isEmpty()) {
            // Priority 1: eventKey is explicitly provided in the task parameters.
            // Look up TS_EVENT directly by eventKey.
            eventKey = paramEventKey.trim();
            logger.info("EventTask preAction: eventKey resolved from parameter='{}', "
                            + "looking up TS_EVENT by eventKey. Task: {}, {}, {}",
                    eventKey, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                    "EventTask: eventKey from parameter: " + eventKey);

            event = EventManager.getInstance().findByEventKey(eventKey);

            if (event == null) {
                throw new TaskExecutionException(
                        "EventTask: no TS_EVENT record found for eventKey='" + eventKey
                                + "' (resolved from parameter). "
                                + "The upstream task must call EventManager.createEvent() first.",
                        null, taskId, taskInstanceId);
            }

        } else {
            // Priority 2: eventKey is not provided; derive it from the TS_EVENT
            // record found by the logical composite key (tpwfInstanceId, taskId).
            logger.info("EventTask preAction: eventKey not found in parameters, "
                            + "looking up TS_EVENT by tpwfInstanceId='{}', taskId='{}'. "
                            + "Task: {}, {}, {}",
                    tpwfInstanceId, taskId, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                    "EventTask: no eventKey in parameters, looking up by tpwfInstanceId="
                            + tpwfInstanceId + ", taskId=" + taskId);

            event = EventManager.getInstance().findByTpwfAndTaskId(tpwfInstanceId, taskId);

            if (event == null) {
                throw new TaskExecutionException(
                        "EventTask: no TS_EVENT record found for tpwfInstanceId='"
                                + tpwfInstanceId + "', taskId='" + taskId + "'. "
                                + "The upstream task must call EventManager.createEvent() first.",
                        null, taskId, taskInstanceId);
            }

            // Derive eventKey from the found record.
            eventKey = event.getEventKey();
            logger.info("EventTask preAction: eventKey derived from TS_EVENT record: '{}'. "
                    + "Task: {}, {}, {}", eventKey, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                    "EventTask: eventKey derived from composite-key lookup: " + eventKey);
        }

        // ── Step 2: react to the current status of the located record ─────────

        String status = event.getStatus();

        if ("TRIGGERED".equals(status)) {
            // Fast path: trigger arrived before preAction (e.g. upstream was slow or
            // this is a retry after the event had already been fired).
            alreadyTriggered = true;
            logger.info("EventTask preAction: event already TRIGGERED, "
                            + "proceeding without blocking. eventKey='{}', Task: {}, {}, {}",
                    eventKey, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                    "EventTask: event already TRIGGERED in preAction: " + eventKey);
            return;
        }

        if ("NEW".equals(status)) {
            // Normal path: transition NEW -> WAITING.
            boolean updated = EventManager.getInstance().markWaiting(eventKey);

            if (!updated) {
                // markWaiting updated 0 rows: the trigger may have arrived between
                // our findBy* call and the markWaiting update (concurrent trigger race).
                // Re-read to confirm actual status.
                TsEvent reread = EventManager.getInstance().findByEventKey(eventKey);
                if (reread != null && "TRIGGERED".equals(reread.getStatus())) {
                    alreadyTriggered = true;
                    logger.info("EventTask preAction: TRIGGERED detected during markWaiting race. "
                                    + "eventKey='{}', Task: {}, {}, {}",
                            eventKey, taskId, taskName, taskInstanceId);
                    TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                            "EventTask: event TRIGGERED (race in preAction): " + eventKey);
                    return;
                }
                // markWaiting failed for an unexpected reason; log a warning and
                // continue – action() will still register the latch and await correctly.
                logger.warn("EventTask preAction: markWaiting returned false for unexpected reason. "
                                + "Continuing to action(). eventKey='{}', Task: {}, {}, {}",
                        eventKey, taskId, taskName, taskInstanceId);
            } else {
                logger.info("EventTask preAction: status set to WAITING. "
                                + "eventKey='{}', timeout={}s, Task: {}, {}, {}",
                        eventKey, timeout, taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                        "EventTask: waiting for event '" + eventKey
                                + "' (timeout=" + timeout + "s)");
            }
            return;
        }

        // WAITING status: task was restored after a JVM restart.
        // WorkflowEngine re-submitted it; action() will re-register the latch and wait.
        logger.info("EventTask preAction: restored with status='{}', will re-await. "
                        + "eventKey='{}', Task: {}, {}, {}",
                status, eventKey, taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                "EventTask: restored (status=" + status + "), re-awaiting: " + eventKey);
    }

    // ── action ────────────────────────────────────────────────────────────────

    @Override
    /**
      * Executes the core task logic.
      * Called by {@link AbstractTask#execute} after {@link #preAction}.
      *
      * @param parameters  merged task parameter map
      * @throws Exception  if the task logic fails
      */
    public void action(Map<String, Object> parameters) throws Exception {

        // Fast path: preAction confirmed the event is already TRIGGERED.
        if (alreadyTriggered) {
            logger.info("EventTask action: skipping wait (already triggered). "
                            + "eventKey='{}', Task: {}, {}, {}",
                    eventKey, taskId, taskName, taskInstanceId);
            String payload = EventManager.getInstance().consumePayload(eventKey);
            storePayload(parameters, payload);
            return;
        }

        // Register latch. If trigger already arrived, an instant (count=0) latch
        // is returned and await() returns true immediately.
        CountDownLatch latch = EventManager.getInstance().registerLatch(eventKey);
        latchRegistered = true;

        logger.info("EventTask action: awaiting event. "
                        + "eventKey='{}', timeout={}s, Task: {}, {}, {}",
                eventKey, timeout, taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                "EventTask: awaiting event '" + eventKey + "' (timeout=" + timeout + "s)");

        boolean triggered;
        try {
            triggered = latch.await(timeout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            if (canceled.get()) {
                logger.warn("EventTask action: canceled while awaiting. "
                                + "eventKey='{}', Task: {}, {}, {}",
                        eventKey, taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                        "EventTask: wait canceled: " + eventKey);
                // Throw instead of return so WorkflowInstance.executeTask() detects
                // task.isCanceled()==true and keeps status as CANCELED (not COMPLETED).
                throw new TaskExecutionException(
                        "EventTask canceled while waiting for event: " + eventKey,
                        e, taskId, taskInstanceId);
            }
            // Unexpected interrupt
            throw new TaskExecutionException(
                    "EventTask interrupted unexpectedly while waiting for event: " + eventKey,
                    e, taskId, taskInstanceId);
        }

        // Post-await cancel check
        if (canceled.get()) {
            logger.warn("EventTask action: canceled after await returned. "
                            + "eventKey='{}', Task: {}, {}, {}",
                    eventKey, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                    "EventTask: canceled after await: " + eventKey);
            throw new TaskExecutionException(
                    "EventTask canceled after await: " + eventKey,
                    null, taskId, taskInstanceId);
        }

        if (!triggered) {
            String msg = "EventTask timed out after " + timeout
                    + "s waiting for event: " + eventKey;
            logger.warn("{}, Task: {}, {}, {}", msg, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                    "EventTask: timeout waiting for event: " + eventKey);
            throw new TaskTimeoutException(msg, null, taskId, taskInstanceId);
        }

        // Event received successfully
        String payload = EventManager.getInstance().consumePayload(eventKey);
        logger.info("EventTask action: event received successfully. "
                        + "eventKey='{}', payloadLength={}, Task: {}, {}, {}",
                eventKey, payload != null ? payload.length() : 0, taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                "EventTask: event triggered successfully: " + eventKey);

        storePayload(parameters, payload);
    }

    // ── cleanupResources ──────────────────────────────────────────────────────

    /**
     * Called by AbstractTask.execute() in its finally block on every exit path.
     * Cleans up both the in-memory latch and the payload cache entry to prevent
     * memory leaks regardless of how the task exited (success / timeout / cancel).
     */
    @Override
    public void cleanupResources() {
        if (latchRegistered && eventKey != null && !eventKey.isEmpty()) {
            EventManager.getInstance().unregisterLatch(eventKey);
            logger.debug("EventTask cleanupResources: unregistered latch and payload cache "
                    + "for key='{}'", eventKey);
        }
        super.cleanupResources();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Store the event payload into both the in-memory parameter map and the
     * task parameter table (via saveParams), so downstream tasks can reference
     * it through variable substitution using ${eventPayload}.
     *
     * @param parameters the current task parameter map
     * @param payload    payload string received from the trigger caller
     */
    private void storePayload(Map<String, Object> parameters, String payload) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(Constants.PARAM_EVENT_PAYLOAD, payload != null ? payload : "");
        saveParams(parameters, updates);
        logger.debug("EventTask: payload stored under key='{}'. Task: {}, {}, {}",
                Constants.PARAM_EVENT_PAYLOAD, taskId, taskName, taskInstanceId);
    }
}
