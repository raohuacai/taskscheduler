/*
 * Description: Contract for workflow event record persistence
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service;

import com.ts.model.entity.TsEvent;

/**
 * Business service interface for the event subsystem.
 * Provides pure DB operations; all in-memory coordination lives in EventManager.
 * Timestamp values (waitDate, triggeredDate) are generated inside the
 * implementation using {@code new Date()}, consistent with other service
 * implementations in this codebase.
 *
 * Author: Rock Rao
 * Date: 2025
 * Version: 1.0
 */
public interface EventService {

    /**
     * Create a NEW status event record in TS_EVENT.
     * Called by an upstream task (e.g. HttpTask.postAction) before the workflow
     * reaches the EventTask node.
     *
     * @param eventKey       globally unique business event key
     * @param tpwfInstanceId top-parent workflow instance ID
     * @param taskId         task definition ID of the EventTask node
     * @return the persisted TsEvent, or null on failure
     */
    TsEvent createEvent(String eventKey, String tpwfInstanceId, String taskId);

    /**
     * Find an event record by its globally unique event key.
     *
     * @param eventKey event key
     * @return TsEvent, or null if not found
     */
    TsEvent findByEventKey(String eventKey);

    /**
     * Find an event record by the logical composite key (tpwfInstanceId, taskId).
     *
     * @param tpwfInstanceId top-parent workflow instance ID
     * @param taskId         EventTask task definition ID
     * @return TsEvent, or null if not found
     */
    TsEvent findByTpwfAndTaskId(String tpwfInstanceId, String taskId);

    /**
     * Transition NEW -> WAITING in the DB.
     * {@code waitDate} is generated internally as {@code new Date()}.
     * Called by EventTask.preAction() once the task is ready to block.
     *
     * @param eventKey event key to update
     * @return true if the row was updated; false means the row is likely
     *         already TRIGGERED (concurrent trigger race)
     */
    boolean markWaiting(String eventKey);

    /**
     * Transition (NEW | WAITING) -> TRIGGERED in the DB.
     * {@code triggeredDate} is generated internally as {@code new Date()}.
     * Called by EventManager.triggerEvent() after DB-state validation.
     *
     * @param eventKey    event key to update
     * @param payload     JSON payload from the trigger caller (may be null)
     * @param triggeredBy username of the API caller
     * @return true if exactly one row was updated
     */
    boolean markTriggered(String eventKey, String payload, String triggeredBy);
}
