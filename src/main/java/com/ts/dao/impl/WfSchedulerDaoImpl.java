/*
 * Description: MyBatis implementation of WfSchedulerDao
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao.impl;

import com.ts.common.utils.ParamUtils;
import com.ts.dao.WfSchedulerDao;
import com.ts.mapper.WfSchedulerMapper;
import com.ts.model.entity.WfScheduler;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
/**
 * MyBatis-based implementation of {@link com.ts.dao.WfSchedulerDao} for
 * cron-schedule definition persistence.
 */
public class WfSchedulerDaoImpl implements WfSchedulerDao {
    private static final Logger logger = LogManager.getLogger(WfSchedulerDaoImpl.class);
    private final SqlSessionFactory sqlSessionFactory;

    /**
     * Constructs a new WfSchedulerDaoImpl instance.
     *
     * @param sqlSessionFactory  factory instance
     */
    public WfSchedulerDaoImpl(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    /**
     * Returns the schedule.
     *
     * @param workflowId  workflowId identifier
     * @return the requested schedule object, or null if not found
     */
    public WfScheduler getSchedule(String workflowId) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfSchedulerMapper mapper = sqlSession.getMapper(WfSchedulerMapper.class);
            return mapper.selectByPrimaryKey(workflowId);
        } catch (Exception e) {
            logger.error("Error getting schedule for workflow: " + workflowId, e);
            return null;
        }
    }

    @Override
    /**
     * Returns the all active schedules.
     *
     * @return a list of matching records
     */
    public List<WfScheduler> getAllActiveSchedules() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfSchedulerMapper mapper = sqlSession.getMapper(WfSchedulerMapper.class);
            return mapper.selectAllActives();
        } catch (Exception e) {
            logger.error("Error getting all active schedules", e);
            return Collections.emptyList();
        }
    }

    @Override
    /**
     * Returns the all schedules.
     *
     * @param workflowId  workflowId identifier
     * @param workflowName  workflowName name
     * @return a list of matching records
     */
    public List<WfScheduler> getAllSchedules(String workflowId, String workflowName) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfSchedulerMapper mapper = sqlSession.getMapper(WfSchedulerMapper.class);
            return mapper.selectAll(ParamUtils.likePattern(workflowId), ParamUtils.likePattern(workflowName));
        } catch (Exception e) {
            logger.error("Error getting all schedules", e);
            return Collections.emptyList();
        }
    }

    @Override
    /**
     * Creates the schedule.
     *
     * @param schedule  schedule value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean createSchedule(WfScheduler schedule) {
        if (schedule == null) {
            return false;
        }
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfSchedulerMapper mapper = sqlSession.getMapper(WfSchedulerMapper.class);
            int result = mapper.insert(schedule);
            sqlSession.commit();
            return result > 0;
        } catch (Exception e) {
            logger.error("Error saving schedule", e);
            return false;
        }
    }

    @Override
    /**
     * Updates the schedule.
     *
     * @param schedule  schedule value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateSchedule(WfScheduler schedule) {
        if (schedule == null) {
            return false;
        }
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfSchedulerMapper mapper = sqlSession.getMapper(WfSchedulerMapper.class);
            int result = mapper.updateByPrimaryKey(schedule);
            sqlSession.commit();
            return result > 0;
        } catch (Exception e) {
            logger.error("Error updating schedule", e);
            return false;
        }
    }

    @Override
    /**
     * Deletes the schedule.
     *
     * @param workflowId  workflowId identifier
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean deleteSchedule(String workflowId) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfSchedulerMapper mapper = sqlSession.getMapper(WfSchedulerMapper.class);
            int result = mapper.deleteByPrimaryKey(workflowId);
            sqlSession.commit();
            return result > 0;
        } catch (Exception e) {
            logger.error("Error deleting schedule for workflow: " + workflowId, e);
            return false;
        }
    }

    @Override
    /**
     * Returns the all active workflow ids.
     *
     * @return the result as a String
     */
    public List<String> getAllActiveWorkflowIds() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfSchedulerMapper mapper = sqlSession.getMapper(WfSchedulerMapper.class);
            return mapper.selectAllActiveWorkflowIds();
        } catch (Exception e) {
            logger.error("Error getting active workflow IDs", e);
            return Collections.emptyList();
        }
    }

}