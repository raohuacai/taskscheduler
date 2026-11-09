/*
 * Description: MyBatis implementation of TaskInstanceDao
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao.impl;

import com.ts.core.transaction.TransactionManager;
import com.ts.core.transaction.TransactionManagerFactory;
import com.ts.dao.TaskInstanceDao;
import com.ts.mapper.*;
import com.ts.model.entity.*;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
/**
 * MyBatis-based implementation of {@link com.ts.dao.TaskInstanceDao}.
 */
public class TaskInstanceDaoImpl implements TaskInstanceDao {
    private static final Logger logger = LogManager.getLogger(TaskInstanceDaoImpl.class);

    private final SqlSessionFactory sqlSessionFactory;
    private final TransactionManager transactionManager;

    /**
     * Constructs a new TaskInstanceDaoImpl instance.
     *
     * @param sqlSessionFactory  factory instance
     */
    public TaskInstanceDaoImpl(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.transactionManager = TransactionManagerFactory.getInstance();
    }

    @Override
    /**
     * Returns the task instance.
     *
     * @param taskInstanceId  instance identifier
     * @return the requested taskinstance object, or null if not found
     */
    public TaskInstance getTaskInstance(String taskInstanceId) {
        // First check if there's an active transaction
        if (transactionManager.isTransactionActive()) {
            return getTaskInstance(taskInstanceId, transactionManager.getCurrentSession());
        }

        // If no active transaction, use a new session
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskInstanceMapper mapper = sqlSession.getMapper(TaskInstanceMapper.class);
            return mapper.selectByPrimaryKey(taskInstanceId);
        } catch (Exception e) {
            logger.error("Error getting task instance: " + taskInstanceId, e);
            return null;
        }
    }

    @Override
    /**
     * Returns the task instances by wf instance id.
     *
     * @param wfInstanceId  instance identifier
     * @return a list of matching records
     */
    public List<TaskInstance> getTaskInstancesByWfInstanceId(String wfInstanceId) {
        // Check for active transaction
        if (transactionManager.isTransactionActive()) {
            return getTaskInstancesByWfInstanceId(wfInstanceId, transactionManager.getCurrentSession());
        }

        // If no active transaction, use a new session
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskInstanceMapper mapper = sqlSession.getMapper(TaskInstanceMapper.class);
            return mapper.selectBywfInstanceId(wfInstanceId);
        } catch (Exception e) {
            logger.error("Error getting task instances for workflow instance: " + wfInstanceId, e);
            return List.of();
        }
    }

    @Override
    /**
     * Creates the task instance.
     *
     * @param taskInstance  taskInstance value
     * @return the result as a String
     */
    public String createTaskInstance(TaskInstance taskInstance) {
        if (taskInstance == null) {
            return null;
        }

        // Check for active transaction
        if (transactionManager.isTransactionActive()) {
            return createTaskInstance(taskInstance, transactionManager.getCurrentSession());
        }

        // If no active transaction, use a new session with auto-commit
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskInstanceMapper mapper = sqlSession.getMapper(TaskInstanceMapper.class);
            mapper.insert(taskInstance);
            sqlSession.commit();
            return taskInstance.getTaskInstanceId();
        } catch (Exception e) {
            logger.error("Error creating task instance", e);
            return null;
        }
    }

    @Override
    /**
     * Updates the status to error.
     *
     * @param taskInstance  taskInstance value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateStatusToError(TaskInstance taskInstance) {
        if (taskInstance == null) {
            return false;
        }

        // Check for active transaction
        if (transactionManager.isTransactionActive()) {
            return updateStatusToError(taskInstance, transactionManager.getCurrentSession());
        }

        // If no active transaction, use a new session with auto-commit
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskInstanceMapper mapper = sqlSession.getMapper(TaskInstanceMapper.class);
            mapper.updateStatusToError(taskInstance);
            sqlSession.commit();
            return true;
        } catch (Exception e) {
            logger.error("Error updating task instance: " + taskInstance.getTaskInstanceId(), e);
            return false;
        }
    }

    @Override
    /**
     * Updates the status to running.
     *
     * @param taskInstance  taskInstance value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateStatusToRunning(TaskInstance taskInstance) {
        if (taskInstance == null) {
            return false;
        }

        // Check for active transaction
        if (transactionManager.isTransactionActive()) {
            return updateStatusToRunning(taskInstance, transactionManager.getCurrentSession());
        }

        // If no active transaction, use a new session with auto-commit
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskInstanceMapper mapper = sqlSession.getMapper(TaskInstanceMapper.class);
            mapper.updateStatusToRunning(taskInstance);
            sqlSession.commit();
            return true;
        } catch (Exception e) {
            logger.error("Error updating task instance: " + taskInstance.getTaskInstanceId(), e);
            return false;
        }
    }

    @Override
    /**
     * Updates the status.
     *
     * @param taskInstance  taskInstance value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateStatus(TaskInstance taskInstance) {
        if (taskInstance == null) {
            return false;
        }

        // Check for active transaction
        if (transactionManager.isTransactionActive()) {
            return updateStatus(taskInstance, transactionManager.getCurrentSession());
        }

        // If no active transaction, use a new session with auto-commit
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskInstanceMapper mapper = sqlSession.getMapper(TaskInstanceMapper.class);
            mapper.updateStatus(taskInstance);
            sqlSession.commit();
            return true;
        } catch (Exception e) {
            logger.error("Error updating task instance status: " + taskInstance.getTaskInstanceId(), e);
            return false;
        }
    }

    // Transaction-aware implementations
    @Override
    /**
     * Returns the task instance.
     *
     * @param taskInstanceId  instance identifier
     * @param sqlSession  SQL session
     * @return the requested taskinstance object, or null if not found
     */
    public TaskInstance getTaskInstance(String taskInstanceId, SqlSession sqlSession) {
        try {
            TaskInstanceMapper mapper = sqlSession.getMapper(TaskInstanceMapper.class);
            return mapper.selectByPrimaryKey(taskInstanceId);
        } catch (Exception e) {
            logger.error("Error getting task instance with session: " + taskInstanceId, e);
            throw new RuntimeException("Error getting task instance", e);
        }
    }

    @Override
    /**
     * Returns the task instances by wf instance id.
     *
     * @param wfInstanceId  instance identifier
     * @param sqlSession  SQL session
     * @return a list of matching records
     */
    public List<TaskInstance> getTaskInstancesByWfInstanceId(String wfInstanceId, SqlSession sqlSession) {
        try {
            TaskInstanceMapper mapper = sqlSession.getMapper(TaskInstanceMapper.class);
            return mapper.selectBywfInstanceId(wfInstanceId);
        } catch (Exception e) {
            logger.error("Error getting task instances for workflow instance with session: " + wfInstanceId, e);
            throw new RuntimeException("Error getting task instances for ts", e);
        }
    }

    @Override
    /**
     * Creates the task instance.
     *
     * @param taskInstance  taskInstance value
     * @param sqlSession  SQL session
     * @return the result as a String
     */
    public String createTaskInstance(TaskInstance taskInstance, SqlSession sqlSession) {
        if (taskInstance == null) {
            return null;
        }

        try {
            TaskInstanceMapper mapper = sqlSession.getMapper(TaskInstanceMapper.class);
            mapper.insert(taskInstance);
            return taskInstance.getTaskInstanceId();
        } catch (Exception e) {
            logger.error("Error creating task instance with session", e);
            throw new RuntimeException("Error creating task instance", e);
        }
    }

    @Override
    /**
     * Updates the status to error.
     *
     * @param taskInstance  taskInstance value
     * @param sqlSession  SQL session
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateStatusToError(TaskInstance taskInstance, SqlSession sqlSession) {
        if (taskInstance == null) {
            return false;
        }

        try {
            TaskInstanceMapper mapper = sqlSession.getMapper(TaskInstanceMapper.class);
            mapper.updateStatusToError(taskInstance);
            return true;
        } catch (Exception e) {
            logger.error("Error updating task instance to error with session: " + taskInstance.getTaskInstanceId(), e);
            throw new RuntimeException("Error updating task instance to error", e);
        }
    }

    @Override
    /**
     * Updates the status to running.
     *
     * @param taskInstance  taskInstance value
     * @param sqlSession  SQL session
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateStatusToRunning(TaskInstance taskInstance, SqlSession sqlSession) {
        if (taskInstance == null) {
            return false;
        }

        try {
            TaskInstanceMapper mapper = sqlSession.getMapper(TaskInstanceMapper.class);
            mapper.updateStatusToRunning(taskInstance);
            return true;
        } catch (Exception e) {
            logger.error("Error updating task instance to error with session: " + taskInstance.getTaskInstanceId(), e);
            throw new RuntimeException("Error updating task instance to error", e);
        }
    }

    @Override
    /**
     * Updates the status.
     *
     * @param taskInstance  taskInstance value
     * @param sqlSession  SQL session
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateStatus(TaskInstance taskInstance, SqlSession sqlSession) {
        if (taskInstance == null) {
            return false;
        }

        try {
            TaskInstanceMapper mapper = sqlSession.getMapper(TaskInstanceMapper.class);
            mapper.updateStatus(taskInstance);
            return true;
        } catch (Exception e) {
            logger.error("Error updating task instance status with session: " + taskInstance.getTaskInstanceId(), e);
            throw new RuntimeException("Error updating task instance status", e);
        }
    }
}