/*
 * Description: Event service persisting TsEvent records and coordinating with EventManager
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service.impl;

import com.ts.common.utils.UniqueIdGenerator;
import com.ts.dao.TsEventDao;
import com.ts.model.entity.TsEvent;
import com.ts.service.EventService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;

/**
 * Pure DB-layer implementation of {@link EventService}.
 *
 * All timestamp values (creationDate, waitDate, triggeredDate) are generated
 * here using {@code new Date()} (Java system time), consistent with how
 * TaskLogService and AuditServiceImpl handle timestamps.
 * No SYSDATE or any DB-generated time is used.
 * In-memory coordination (latch registry, payload cache) is the responsibility
 * of EventManager, not this service.
 *
 * Author: Rock Rao
 * Date: 2025
 * Version: 1.0
 */
public class EventServiceImpl implements EventService {

    private static final Logger logger = LogManager.getLogger(EventServiceImpl.class);

    private final TsEventDao eventDao;

    /**
     * Constructs a new EventServiceImpl instance.
     *
     * @param eventDao  DAO dependency
     */
    public EventServiceImpl(TsEventDao eventDao) {
        this.eventDao = eventDao;
    }

    @Override
    /**
     * Creates the event.
     *
     * @param eventKey  lookup key
     * @param tpwfInstanceId  instance identifier
     * @param taskId  identifier
     * @return the result of the operation
     */
    public TsEvent createEvent(String eventKey, String tpwfInstanceId, String taskId) {
        if (eventKey == null || eventKey.isEmpty()
                || tpwfInstanceId == null || tpwfInstanceId.isEmpty()
                || taskId == null || taskId.isEmpty()) {
            logger.error("createEvent: eventKey, tpwfInstanceId, and taskId are all required");
            return null;
        }
        TsEvent event = new TsEvent();
        event.setId(UniqueIdGenerator.generateId(UniqueIdGenerator.IdType.EVENT));
        event.setEventKey(eventKey);
        event.setTpwfInstanceId(tpwfInstanceId);
        event.setTaskId(taskId);
        event.setStatus("NEW");
        event.setCreationDate(new Date());   // Java time, consistent with TaskLog
        // waitDate and triggeredDate are null at creation time
        boolean ok = eventDao.createEvent(event);
        if (!ok) {
            logger.error("createEvent: DB insert failed for key={}", eventKey);
            return null;
        }
        logger.info("Event created: key={}, tpwf={}, taskId={}", eventKey, tpwfInstanceId, taskId);
        return event;
    }

    @Override
    /**
     * Finds the by event key.
     *
     * @param eventKey  lookup key
     * @return the requested byeventkey object, or null if not found
     */
    public TsEvent findByEventKey(String eventKey) {
        return eventDao.findByEventKey(eventKey);
    }

    @Override
    /**
     * Finds the by tpwf and task id.
     *
     * @param tpwfInstanceId  instance identifier
     * @param taskId  identifier
     * @return the requested bytpwfandtaskid object, or null if not found
     */
    public TsEvent findByTpwfAndTaskId(String tpwfInstanceId, String taskId) {
        return eventDao.findByTpwfAndTaskId(tpwfInstanceId, taskId);
    }

    @Override
    /**
     * Mark the waiting.
     *
     * @param eventKey  lookup key
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean markWaiting(String eventKey) {
        Date waitDate = new Date();    // Java time, not SYSDATE
        boolean ok = eventDao.updateToWaiting(eventKey, waitDate);
        if (ok) {
            logger.info("Event marked WAITING: key={}, waitDate={}", eventKey, waitDate);
        } else {
            // 0 rows updated: the row is likely already TRIGGERED (concurrent trigger race).
            // The caller (EventManager.markWaiting) will re-read the DB to confirm.
            logger.warn("markWaiting: 0 rows updated for key={} (possibly already TRIGGERED)", eventKey);
        }
        return ok;
    }

    @Override
    /**
     * Mark the triggered.
     *
     * @param eventKey  lookup key
     * @param payload  payload value
     * @param triggeredBy  triggeredBy value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean markTriggered(String eventKey, String payload, String triggeredBy) {
        Date triggeredDate = new Date();    // Java time, not SYSDATE
        boolean ok = eventDao.updateToTriggered(eventKey, payload, triggeredBy, triggeredDate);
        if (ok) {
            logger.info("Event marked TRIGGERED in DB: key={}, by={}, triggeredDate={}",
                    eventKey, triggeredBy, triggeredDate);
        } else {
            logger.warn("markTriggered: 0 rows updated for key={} (not found or already TRIGGERED)",
                    eventKey);
        }
        return ok;
    }
}
