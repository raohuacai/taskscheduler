/*
 * Description: MyBatis mapper for TS_EVENT table
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.mapper;

import com.ts.model.entity.TsEvent;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
 * MyBatis mapper interface for TS_EVENT.
 *
 * All timestamp parameters are Java {@link Date} values supplied by the caller.
 * No SYSDATE is used in the SQL – consistent with TaskLogMapper and AuditLogMapper.
 *
 * Author: Rock Rao
 * Date: 2025
 * Version: 1.0
 */
public interface TsEventMapper {

    /**
     * Insert a new event record (status = NEW).
     * The {@code creationDate} field of the entity must be set by the caller.
     */
    int insert(TsEvent record);

    /**
     * Select by globally unique event key.
     *
     * @param eventKey event key
     * @return TsEvent or null if not found
     */
    TsEvent selectByEventKey(@Param("eventKey") String eventKey);

    /**
     * Select by logical composite key (tpwfInstanceId, taskId).
     * The pair is unique in normal operation.
     *
     * @param tpwfInstanceId top-parent workflow instance ID
     * @param taskId         EventTask task definition ID
     * @return TsEvent or null if not found
     */
    TsEvent selectByTpwfAndTaskId(@Param("tpwfInstanceId") String tpwfInstanceId,
                                   @Param("taskId") String taskId);

    /**
     * Transition NEW -> WAITING.
     * Only updates the row when current STATUS = 'NEW' (forward-only, prevents regression).
     *
     * @param eventKey event key
     * @param waitDate Java-side current time; stored in WAIT_DATE column
     * @return number of rows updated (1 = success, 0 = not in NEW state)
     */
    int updateToWaiting(@Param("eventKey") String eventKey,
                        @Param("waitDate") Date waitDate);

    /**
     * Transition (NEW | WAITING) -> TRIGGERED.
     * Accepts both source statuses to tolerate the race where the trigger
     * API fires before EventTask reaches preAction().
     *
     * @param eventKey      event key
     * @param payload       JSON payload from the trigger caller (may be null)
     * @param triggeredBy   username of the API caller
     * @param triggeredDate Java-side current time; stored in TRIGGERED_DATE column
     * @return number of rows updated (1 = success, 0 = not found or already TRIGGERED)
     */
    int updateToTriggered(@Param("eventKey") String eventKey,
                          @Param("payload") String payload,
                          @Param("triggeredBy") String triggeredBy,
                          @Param("triggeredDate") Date triggeredDate);
}
