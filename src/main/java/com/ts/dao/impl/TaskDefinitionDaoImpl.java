/*
 * Description: MyBatis implementation of TaskDefinitionDao
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao.impl;

import com.ts.dao.TaskDefinitionDao;
import com.ts.mapper.*;
import com.ts.model.entity.*;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
/**
 * MyBatis-based implementation of {@link com.ts.dao.TaskDefinitionDao}.
 */
public class TaskDefinitionDaoImpl implements TaskDefinitionDao {
    private static final Logger logger = LogManager.getLogger(TaskDefinitionDaoImpl.class);

    private final SqlSessionFactory sqlSessionFactory;

    /**
     * Constructs a new TaskDefinitionDaoImpl instance.
     *
     * @param sqlSessionFactory  factory instance
     */
    public TaskDefinitionDaoImpl(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    /**
     * Returns the task.
     *
     * @param taskId  identifier
     * @return the requested task object, or null if not found
     */
    public TaskDefinition getTask(String taskId) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskDefinitionMapper mapper = sqlSession.getMapper(TaskDefinitionMapper.class);
            return mapper.selectByPrimaryKey(taskId);
        } catch (Exception e) {
            logger.error("Error getting task: " + taskId, e);
            return null;
        }
    }

    @Override
    /**
     * Returns the task by name.
     *
     * @param workflowId  workflowId identifier
     * @param taskName  taskName name
     * @return the requested taskbyname object, or null if not found
     */
    public TaskDefinition getTaskByName(String workflowId, String taskName) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskDefinitionMapper mapper = sqlSession.getMapper(TaskDefinitionMapper.class);
            return mapper.selectByName(workflowId, taskName);
        } catch (Exception e) {
            logger.error("Error getting task: " + taskName, e);
            return null;
        }
    }

    @Override
    /**
     * Returns the tasks by workflow id.
     *
     * @param workflowId  workflowId identifier
     * @return a list of matching records
     */
    public List<TaskDefinition> getTasksByWorkflowId(String workflowId) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskDefinitionMapper mapper = sqlSession.getMapper(TaskDefinitionMapper.class);
            return mapper.selectByWorkflowId(workflowId);
        } catch (Exception e) {
            logger.error("Error getting tasks for workflow: " + workflowId, e);
            return null;
        }
    }

    @Override
    /**
     * Saves the task.
     *
     * @param task  task value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean saveTask(TaskDefinition task) {
        if (task == null) {
            return false;
        }

        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskDefinitionMapper mapper = sqlSession.getMapper(TaskDefinitionMapper.class);
            mapper.insert(task);
            sqlSession.commit();
            return true;
        } catch (Exception e) {
            logger.error("Error saving task", e);
            return false;
        }
    }

    @Override
    /**
     * Updates the task.
     *
     * @param task  task value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateTask(TaskDefinition task) {
        if (task == null) {
            return false;
        }

        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskDefinitionMapper mapper = sqlSession.getMapper(TaskDefinitionMapper.class);
            mapper.updateByPrimaryKey(task);
            sqlSession.commit();
            return true;
        } catch (Exception e) {
            logger.error("Error updating task", e);
            return false;
        }
    }

    @Override
    /**
     * Updates the predecessors.
     *
     * @param task  task value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updatePredecessors(TaskDefinition task) {
        if (task == null) {
            return false;
        }

        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskDefinitionMapper mapper = sqlSession.getMapper(TaskDefinitionMapper.class);
            int result = mapper.updatePredecessors(task);
            sqlSession.commit();
            return result > 0;
        } catch (Exception e) {
            logger.error("Error updating task", e);
            return false;
        }
    }

    @Override
    /**
     * Deletes the task.
     *
     * @param taskId  identifier
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean deleteTask(String taskId) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskDefinitionMapper mapper = sqlSession.getMapper(TaskDefinitionMapper.class);
            mapper.deleteByPrimaryKey(taskId);
            sqlSession.commit();
            return true;
        } catch (Exception e) {
            logger.error("Error deleting task: " + taskId, e);
            return false;
        }
    }

    @Override
    /**
     * Deletes the tasks by workflow id.
     *
     * @param workflowId  workflowId identifier
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean deleteTasksByWorkflowId(String workflowId) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskDefinitionMapper mapper = sqlSession.getMapper(TaskDefinitionMapper.class);
            mapper.deleteByWorkflowId(workflowId);
            sqlSession.commit();
            return true;
        } catch (Exception e) {
            logger.error("Error deleting tasks for workflow: " + workflowId, e);
            return false;
        }
    }

}