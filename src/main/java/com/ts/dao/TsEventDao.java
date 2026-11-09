/*
 * Description: DAO interface for TsEvent CRUD on TS_EVENT
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao;

import com.ts.model.entity.TsEvent;

/**
 * DAO interface for TS_EVENT.
 * Timestamp values are always supplied by the service layer (Java time);
 * this DAO never generates timestamps itself.
 *
 * Author: Rock Rao
 * Date: 2025
 * Version: 1.0
 */
public interface TsEventDao {

    /**
     * Persist a new event record (status = NEW).
     * The {@code creationDate} field of {@code event} must be set by the caller.
     *
     * @param event fully-populated TsEvent
     * @return true if the row was inserted successfully
     */
    boolean createEvent(TsEvent event);

    /**
     * Find by globally unique event key.
     *
     * @param eventKey event key
     * @return TsEvent, or null if not found
     */
    TsEvent findByEventKey(String eventKey);

    /**
     * Find by logical composite key (tpwfInstanceId, taskId).
     *
     * @param tpwfInstanceId top-parent workflow instance ID
     * @param taskId         EventTask task definition ID
     * @return TsEvent, or null if not found
     */
    TsEvent findByTpwfAndTaskId(String tpwfInstanceId, String taskId);

    /**
     * Transition NEW -> WAITING.
     *
     * @param eventKey event key
     * @param waitDate Java-side current time to store in WAIT_DATE
     * @return true if exactly one row was updated
     */
    boolean updateToWaiting(String eventKey, java.util.Date waitDate);

    /**
     * Transition (NEW | WAITING) -> TRIGGERED.
     *
     * @param eventKey      event key
     * @param payload       JSON payload from the trigger caller (may be null)
     * @param triggeredBy   username of the API caller
     * @param triggeredDate Java-side current time to store in TRIGGERED_DATE
     * @return true if exactly one row was updated
     */
    boolean updateToTriggered(String eventKey, String payload,
                              String triggeredBy, java.util.Date triggeredDate);
}
