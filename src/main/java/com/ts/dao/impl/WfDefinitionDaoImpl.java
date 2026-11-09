/*
 * Description: MyBatis implementation of WfDefinitionDao
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao.impl;

import com.ts.common.utils.JsonUtils;
import com.ts.common.utils.ParamUtils;
import com.ts.dao.WfDefinitionDao;
import com.ts.mapper.*;
import com.ts.model.entity.*;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * MyBatis-based implementation of {@link com.ts.dao.WfDefinitionDao} for
 * workflow definition persistence and retrieval.
 */
public class WfDefinitionDaoImpl implements WfDefinitionDao {
    private static final Logger logger = LogManager.getLogger(WfDefinitionDaoImpl.class);

    private final SqlSessionFactory sqlSessionFactory;

    /**
     * Constructs a new WfDefinitionDaoImpl instance.
     *
     * @param sqlSessionFactory  factory instance
     */
    public WfDefinitionDaoImpl(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    /**
     * Returns the workflow.
     *
     * @param workflowId  workflowId identifier
     * @return the requested workflow object, or null if not found
     */
    public WfDefinition getWorkflow(String workflowId) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfDefinitionMapper mapper = sqlSession.getMapper(WfDefinitionMapper.class);
            return mapper.selectByPrimaryKey(workflowId);
        } catch (Exception e) {
            logger.error("Error getting workflow: " + workflowId, e);
            return null;
        }
    }

    @Override
    /**
     * Returns the workflow by name.
     *
     * @param workflowName  workflowName name
     * @return the requested workflowbyname object, or null if not found
     */
    public WfDefinition getWorkflowByName(String workflowName){
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfDefinitionMapper mapper = sqlSession.getMapper(WfDefinitionMapper.class);
            return mapper.selectByName(workflowName);
        } catch (Exception e) {
            logger.error("Error getting workflow: " + workflowName, e);
            return null;
        }
    }

    @Override
    /**
     * Returns the all workflows.
     *
     * @param workflowId  workflowId identifier
     * @param workflowName  workflowName name
     * @return a list of matching records
     */
    public List<WfDefinition> getAllWorkflows(String workflowId, String workflowName) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfDefinitionMapper mapper = sqlSession.getMapper(WfDefinitionMapper.class);
            return mapper.selectAll(ParamUtils.likePattern(workflowId), ParamUtils.likePattern(workflowName));
        } catch (Exception e) {
            logger.error("Error getting all workflows", e);
            return List.of();
        }
    }

    @Override
    /**
     * Saves the workflow.
     *
     * @param workflow  workflow value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean saveWorkflow(WfDefinition workflow) {
        if (workflow == null) {
            return false;
        }

        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfDefinitionMapper mapper = sqlSession.getMapper(WfDefinitionMapper.class);
            mapper.insert(workflow);
            sqlSession.commit();
            return true;
        } catch (Exception e) {
            logger.error("Error saving ts", e);
            return false;
        }
    }

    @Override
    /**
     * Updates the workflow.
     *
     * @param workflow  workflow value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateWorkflow(WfDefinition workflow) {
        if (workflow == null) {
            return false;
        }

        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfDefinitionMapper mapper = sqlSession.getMapper(WfDefinitionMapper.class);
            mapper.updateByPrimaryKey(workflow);
            sqlSession.commit();
            return true;
        } catch (Exception e) {
            logger.error("Error updating ts", e);
            return false;
        }
    }

    @Override
    /**
     * Deletes the workflow.
     *
     * @param workflowId  workflowId identifier
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean deleteWorkflow(String workflowId) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            WfDefinitionMapper mapper = sqlSession.getMapper(WfDefinitionMapper.class);
            mapper.deleteByPrimaryKey(workflowId);
            sqlSession.commit();
            return true;
        } catch (Exception e) {
            logger.error("Error deleting workflow: " + workflowId, e);
            return false;
        }
    }

    @Override
    /**
     * Returns the workflow-definition-level parameters as a map
     * by deserialising the JSON stored in the {@code pvalue} column.
     *
     * @param workflowId the workflow definition ID
     * @return a mutable map of parameter key-value pairs, or an empty map if undefined
     */
    public Map<String, Object> getWorkflowParamsAsMap(String workflowId) {
        WfDefinition workflow = getWorkflow(workflowId);
        if (workflow == null || workflow.getPvalue() == null) {
            return new HashMap<>();
        }

        Map<String, Object> paramsMap = JsonUtils.jsonToMap(workflow.getPvalue());
        return paramsMap != null ? paramsMap : new HashMap<>();
    }
}