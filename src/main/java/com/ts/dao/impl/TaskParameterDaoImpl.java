/*
 * Description: MyBatis implementation of TaskParameterDao
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao.impl;

import com.ts.dao.TaskParameterDao;
import com.ts.mapper.TaskParameterMapper;
import com.ts.model.entity.TaskParameter;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Collections;
/**
 * MyBatis-based implementation of {@link com.ts.dao.TaskParameterDao}.
 */
public class TaskParameterDaoImpl implements TaskParameterDao {
    private static final Logger logger = LogManager.getLogger(TaskParameterDaoImpl.class);
    private final SqlSessionFactory sqlSessionFactory;

    /**
     * Constructs a new TaskParameterDaoImpl instance.
     *
     * @param sqlSessionFactory  factory instance
     */
    public TaskParameterDaoImpl(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    /**
     * Returns the task params.
     *
     * @param taskInstanceId  instance identifier
     * @return the requested taskparams object, or null if not found
     */
    public TaskParameter getTaskParams(String taskInstanceId) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskParameterMapper mapper = sqlSession.getMapper(TaskParameterMapper.class);
            return mapper.selectByTaskInstanceId(taskInstanceId);
        } catch (Exception e) {
            logger.error("Error getting task params by instance ID: " + taskInstanceId, e);
            return null;
        }
    }

    @Override
    /**
     * Returns the task param by id.
     *
     * @param TaskParamId  TaskParamId identifier
     * @return the requested taskparambyid object, or null if not found
     */
    public TaskParameter getTaskParamById(String TaskParamId) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskParameterMapper mapper = sqlSession.getMapper(TaskParameterMapper.class);
            return mapper.selectByPrimaryKey(TaskParamId);
        } catch (Exception e) {
            logger.error("Error getting task params by ID: " + TaskParamId, e);
            return null;
        }
    }

    @Override
    /**
     * Returns the task params by wf instance id.
     *
     * @param wfInstanceId  instance identifier
     * @return a list of matching records
     */
    public List<TaskParameter> getTaskParamsByWfInstanceId(String wfInstanceId) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskParameterMapper mapper = sqlSession.getMapper(TaskParameterMapper.class);
            return mapper.selectBywfInstanceId(wfInstanceId);
        } catch (Exception e) {
            logger.error("Error getting task params for workflow instance: " + wfInstanceId, e);
            return List.of();
        }
    }

    @Override
    public List<TaskParameter> getTaskParamsByWfInstanceIdAndTaskIds(String wfInstanceId, List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Collections.emptyList();
        }
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskParameterMapper mapper = sqlSession.getMapper(TaskParameterMapper.class);
            return mapper.selectByWfInstanceIdAndTaskIds(wfInstanceId, taskIds);
        } catch (Exception e) {
            logger.error("Error getting task params for workflow instance: {} taskIds: {}", wfInstanceId, taskIds, e);
            return Collections.emptyList();
        }
    }

    @Override
    /**
     * Creates the task params.
     *
     * @param taskParameter  taskParameter value
     * @return the result as a String
     */
    public String createTaskParams(TaskParameter taskParameter) {
        if (taskParameter == null) {
            return null;
        }
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskParameterMapper mapper = sqlSession.getMapper(TaskParameterMapper.class);
            mapper.insert(taskParameter);
            sqlSession.commit();
            return taskParameter.getTaskParamId();
        } catch (Exception e) {
            logger.error("Error creating task params", e);
            return null;
        }
    }

    @Override
    /**
     * Updates the pvalue by instance id.
     *
     * @param taskInstanceId  instance identifier
     * @param pvalue  pvalue value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updatePvalueByInstanceId(String taskInstanceId, String pvalue) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskParameterMapper mapper = sqlSession.getMapper(TaskParameterMapper.class);
            int result = mapper.updatePvalueByTaskInstanceId(taskInstanceId, pvalue);
            sqlSession.commit();
            return result > 0;
        } catch (Exception e) {
            logger.error("Error updating task params pvalue by instance ID: " + taskInstanceId, e);
            return false;
        }
    }
}

