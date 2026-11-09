/*
 * Description: MyBatis implementation of TsEventDao
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao.impl;

import com.ts.dao.TsEventDao;
import com.ts.mapper.TsEventMapper;
import com.ts.model.entity.TsEvent;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;

/**
 * MyBatis-backed implementation of {@link TsEventDao}.
 *
 * Follows the same SqlSession open/commit/close pattern as TaskLogDaoImpl
 * and other DAO implementations in this codebase.
 * Timestamps are received from the caller; this class never generates them.
 *
 * Author: Rock Rao
 * Date: 2025
 * Version: 1.0
 */
public class TsEventDaoImpl implements TsEventDao {

    private static final Logger logger = LogManager.getLogger(TsEventDaoImpl.class);

    private final SqlSessionFactory sqlSessionFactory;

    /**
     * Constructs a TsEventDaoImpl with the provided MyBatis session factory.
     *
     * @param sqlSessionFactory  the MyBatis {@link org.apache.ibatis.session.SqlSessionFactory}
     *                           used to open sessions for each database operation
     */
    public TsEventDaoImpl(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    /**
     * Creates the event.
     *
     * @param event  event value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean createEvent(TsEvent event) {
        if (event == null || event.getId() == null || event.getEventKey() == null) {
            logger.error("createEvent: id and eventKey are required");
            return false;
        }
        try (SqlSession session = sqlSessionFactory.openSession()) {
            int rows = session.getMapper(TsEventMapper.class).insert(event);
            session.commit();
            return rows > 0;
        } catch (Exception e) {
            logger.error("Error creating TS_EVENT record for key={}", event.getEventKey(), e);
            return false;
        }
    }

    @Override
    /**
     * Finds the by event key.
     *
     * @param eventKey  lookup key
     * @return the requested byeventkey object, or null if not found
     */
    public TsEvent findByEventKey(String eventKey) {
        if (eventKey == null || eventKey.isEmpty()) return null;
        try (SqlSession session = sqlSessionFactory.openSession()) {
            return session.getMapper(TsEventMapper.class).selectByEventKey(eventKey);
        } catch (Exception e) {
            logger.error("Error finding TS_EVENT by eventKey={}", eventKey, e);
            return null;
        }
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
        if (tpwfInstanceId == null || taskId == null) return null;
        try (SqlSession session = sqlSessionFactory.openSession()) {
            return session.getMapper(TsEventMapper.class)
                          .selectByTpwfAndTaskId(tpwfInstanceId, taskId);
        } catch (Exception e) {
            logger.error("Error finding TS_EVENT by tpwf={}, taskId={}", tpwfInstanceId, taskId, e);
            return null;
        }
    }

    @Override
    /**
     * Updates the to waiting.
     *
     * @param eventKey  lookup key
     * @param waitDate  date/time value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateToWaiting(String eventKey, Date waitDate) {
        if (eventKey == null || eventKey.isEmpty()) return false;
        try (SqlSession session = sqlSessionFactory.openSession()) {
            int rows = session.getMapper(TsEventMapper.class).updateToWaiting(eventKey, waitDate);
            session.commit();
            return rows > 0;
        } catch (Exception e) {
            logger.error("Error updating TS_EVENT to WAITING for key={}", eventKey, e);
            return false;
        }
    }

    @Override
    /**
     * Updates the to triggered.
     *
     * @param eventKey  lookup key
     * @param payload  payload value
     * @param triggeredBy  triggeredBy value
     * @param triggeredDate  date/time value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateToTriggered(String eventKey, String payload,
                                     String triggeredBy, Date triggeredDate) {
        if (eventKey == null || eventKey.isEmpty()) return false;
        try (SqlSession session = sqlSessionFactory.openSession()) {
            int rows = session.getMapper(TsEventMapper.class)
                              .updateToTriggered(eventKey, payload, triggeredBy, triggeredDate);
            session.commit();
            return rows > 0;
        } catch (Exception e) {
            logger.error("Error updating TS_EVENT to TRIGGERED for key={}", eventKey, e);
            return false;
        }
    }
}
