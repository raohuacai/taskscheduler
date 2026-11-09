/*
 * Description: MyBatis implementation of WfInstanceDao
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao.impl;

import com.ts.common.utils.ParamUtils;
import com.ts.dao.WfInstanceDao;
import com.ts.mapper.*;
import com.ts.model.entity.*;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;
import java.util.List;
/**
 * MyBatis-based implementation of {@link com.ts.dao.WfInstanceDao} for
 * workflow instance lifecycle management.
 */
public class WfInstanceDaoImpl implements WfInstanceDao {
    private static final Logger logger = LogManager.getLogger(WfInstanceDaoImpl.class);

    private final SqlSessionFactory sqlSessionFactory;

    /**
     * Constructs a new WfInstanceDaoImpl instance.
     *
     * @param sqlSessionFactory  factory instance
     */
    public WfInstanceDaoImpl(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    /**
     * Returns the workflow instance identified by {@code wfInstanceId}.
     *
     * <p>Note: throws {@link IllegalArgumentException} (not {@code null}) if the instance is not found.
     *
     * @param wfInstanceId  instance identifier
     * @return the requested wfinstance object, or null if not found
     */
    public WfInstance getWfInstance(String wfInstanceId) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfInstanceMapper mapper = sqlSession.getMapper(WfInstanceMapper.class);
            return mapper.selectByPrimaryKey(wfInstanceId);
        } catch (Exception e) {
            logger.error("Error getting workflow instance: " + wfInstanceId, e);
            return null;
        }
    }

    @Override
    /**
     * Returns the all wf instances.
     *
     * @param workflowId  workflowId identifier
     * @param workflowName  workflowName name
     * @param wfInstanceId  instance identifier
     * @param parentWfInstanceId  instance identifier
     * @param status  status value
     * @param startDateFrom  date/time value
     * @param startDateTo  date/time value
     * @return a list of matching records
     */
    public List<WfInstance> getAllWfInstances(String workflowId, String workflowName, String wfInstanceId, String parentWfInstanceId, String status,
                                              Date startDateFrom, Date startDateTo) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfInstanceMapper mapper = sqlSession.getMapper(WfInstanceMapper.class);
            return mapper.selectAll(ParamUtils.likePattern(workflowId), ParamUtils.likePattern(workflowName), ParamUtils.likePattern(wfInstanceId), ParamUtils.likePattern(parentWfInstanceId), status,
                    startDateFrom, startDateTo);
        } catch (Exception e) {
            logger.error("Error getting all workflow instance: ", e);
            return null;
        }
    }

    @Override
    /**
     * Returns the all wf instances.
     *
     * @param workflowId  workflowId identifier
     * @param workflowName  workflowName name
     * @param wfInstanceId  instance identifier
     * @param parentWfInstanceId  instance identifier
     * @param topParentInstanceId  instance identifier
     * @param status  status value
     * @param startDateFrom  date/time value
     * @param startDateTo  date/time value
     * @return a list of matching records
     */
    public List<WfInstance> getAllWfInstances(String workflowId, String workflowName, String wfInstanceId, String parentWfInstanceId, String topParentInstanceId, String status,
                                              Date startDateFrom, Date startDateTo) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfInstanceMapper mapper = sqlSession.getMapper(WfInstanceMapper.class);
            return mapper.selectAll(ParamUtils.likePattern(workflowId), ParamUtils.likePattern(workflowName), ParamUtils.likePattern(wfInstanceId), ParamUtils.likePattern(parentWfInstanceId), ParamUtils.likePattern(topParentInstanceId), status,
                    startDateFrom, startDateTo);
        } catch (Exception e) {
            logger.error("Error getting all workflow instance: ", e);
            return null;
        }
    }

    @Override
    /**
     * Creates the wf instance.
     *
     * @param wfInstance  wfInstance value
     * @return the result as a String
     */
    public String createWfInstance(WfInstance wfInstance) {
        if (wfInstance == null) {
            return null;
        }

        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfInstanceMapper mapper = sqlSession.getMapper(WfInstanceMapper.class);
            mapper.insert(wfInstance);
            sqlSession.commit();
            return wfInstance.getWfInstanceId();
        } catch (Exception e) {
            logger.error("Error creating workflow instance", e);
            return null;
        }
    }

    @Override
    /**
     * Updates the status to error.
     *
     * @param wfInstance  wfInstance value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateStatusToError(WfInstance wfInstance) {
        if (wfInstance == null) {
            return false;
        }

        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfInstanceMapper mapper = sqlSession.getMapper(WfInstanceMapper.class);
            mapper.updateStatusToError(wfInstance);
            sqlSession.commit();
            return true;
        } catch (Exception e) {
            logger.error("Error updating workflow instance: " + wfInstance.getWfInstanceId(), e);
            return false;
        }
    }

    @Override
    /**
     * Updates the status to running.
     *
     * @param wfInstance  wfInstance value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateStatusToRunning(WfInstance wfInstance) {
        if (wfInstance == null) {
            return false;
        }

        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfInstanceMapper mapper = sqlSession.getMapper(WfInstanceMapper.class);
            mapper.updateStatusToRunning(wfInstance);
            sqlSession.commit();
            return true;
        } catch (Exception e) {
            logger.error("Error updating workflow instance: " + wfInstance.getWfInstanceId(), e);
            return false;
        }
    }

    @Override
    /**
     * Updates the status.
     *
     * @param wfInstance  wfInstance value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateStatus(WfInstance wfInstance) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfInstanceMapper mapper = sqlSession.getMapper(WfInstanceMapper.class);
            mapper.updateStatus(wfInstance);
            sqlSession.commit();
            return true;
        } catch (Exception e) {
            logger.error("Error updating workflow instance status: " + wfInstance.getWfInstanceId(), e);
            return false;
        }
    }

}