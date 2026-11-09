/*
 * Description: MyBatis implementation of GlobalParamsDao
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao.impl;

import com.ts.common.utils.JsonUtils;
import com.ts.dao.GlobalParamsDao;
import com.ts.mapper.*;
import com.ts.model.entity.*;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
/**
 * MyBatis-based implementation of {@link com.ts.dao.GlobalParamsDao}.
 */
public class GlobalParamsDaoImpl implements GlobalParamsDao {
    private static final Logger logger = LogManager.getLogger(GlobalParamsDaoImpl.class);

    private final SqlSessionFactory sqlSessionFactory;

    /**
     * Constructs a new GlobalParamsDaoImpl instance.
     *
     * @param sqlSessionFactory  factory instance
     */
    public GlobalParamsDaoImpl(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    /**
     * Returns the all global params.
     *
     * @return a list of matching records
     */
    public List<GlobalParams> getAllGlobalParams() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            GlobalParamsMapper mapper = sqlSession.getMapper(GlobalParamsMapper.class);
            return mapper.selectAll();
        } catch (Exception e) {
            logger.error("Error getting all global params", e);
            return null;
        }
    }

    @Override
    /**
     * Returns the global params.
     *
     * @param pName  pName name
     * @return the requested globalparams object, or null if not found
     */
    public GlobalParams getGlobalParams(String pName) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            GlobalParamsMapper mapper = sqlSession.getMapper(GlobalParamsMapper.class);
            return mapper.selectByPrimaryKey(pName);
        } catch (Exception e) {
            logger.error("Error getting global params", e);
            return null;
        }
    }

    @Override
    /**
     * Updates the global params.
     *
     * @param pName  pName name
     * @param params  parameter map
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateGlobalParams(String pName, Map<String, Object> params) {
        if (pName == null || pName.isEmpty()) {
            return false;
        }
        if (params == null) {
            return false;
        }

        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            GlobalParamsMapper mapper = sqlSession.getMapper(GlobalParamsMapper.class);

            GlobalParams globalParams = mapper.selectByPrimaryKey(pName);
            if (globalParams == null) {

                return false;
            } else {
                // Update existing global params
                globalParams.setPvalue(JsonUtils.toJson(params));
                mapper.updateByPrimaryKey(globalParams);
            }

            sqlSession.commit();
            return true;
        } catch (Exception e) {
            logger.error("Error updating global params", e);
            return false;
        }
    }
}