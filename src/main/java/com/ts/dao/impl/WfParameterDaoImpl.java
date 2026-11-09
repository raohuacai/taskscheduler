/*
 * Description: MyBatis implementation of WfParameterDao
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao.impl;

import com.ts.dao.WfParameterDao;
import com.ts.mapper.WfParameterMapper;
import com.ts.model.entity.WfParameter;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
/**
 * MyBatis-based implementation of {@link com.ts.dao.WfParameterDao}.
 */
public class WfParameterDaoImpl implements WfParameterDao {
    private static final Logger logger = LogManager.getLogger(WfParameterDaoImpl.class);
    private final SqlSessionFactory sqlSessionFactory;

    /**
     * Constructs a new WfParameterDaoImpl instance.
     *
     * @param sqlSessionFactory  factory instance
     */
    public WfParameterDaoImpl(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    /**
     * Returns the wf params.
     *
     * @param wfInstanceId  instance identifier
     * @return the requested wfparams object, or null if not found
     */
    public WfParameter getWfParams(String wfInstanceId) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfParameterMapper mapper = sqlSession.getMapper(WfParameterMapper.class);
            return mapper.selectBywfInstanceId(wfInstanceId);
        } catch (Exception e) {
            logger.error("Error getting workflow params by instance ID: " + wfInstanceId, e);
            return null;
        }
    }

    @Override
    /**
     * Returns the wf param by id.
     *
     * @param wfParamId  wfParamId identifier
     * @return the requested wfparambyid object, or null if not found
     */
    public WfParameter getWfParamById(String wfParamId) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfParameterMapper mapper = sqlSession.getMapper(WfParameterMapper.class);
            return mapper.selectByPrimaryKey(wfParamId);
        } catch (Exception e) {
            logger.error("Error getting workflow params by ID: " + wfParamId, e);
            return null;
        }
    }

    @Override
    /**
     * Creates the wf params.
     *
     * @param wfParameter  wfParameter value
     * @return the result as a String
     */
    public String createWfParams(WfParameter wfParameter) {
        if (wfParameter == null) {
            return null;
        }
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfParameterMapper mapper = sqlSession.getMapper(WfParameterMapper.class);
            mapper.insert(wfParameter);
            sqlSession.commit();
            return wfParameter.getWfParamId();
        } catch (Exception e) {
            logger.error("Error creating workflow params", e);
            return null;
        }
    }
}
