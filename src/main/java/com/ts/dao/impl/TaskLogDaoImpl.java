/*
 * Description: MyBatis implementation of TaskLogDao
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao.impl;

import com.ts.dao.TaskLogDao;
import com.ts.mapper.TaskLogMapper;
import com.ts.model.entity.TaskLog;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
/**
 * MyBatis-based implementation of {@link com.ts.dao.TaskLogDao}.
 */
public class TaskLogDaoImpl implements TaskLogDao {
    private static final Logger logger = LogManager.getLogger(TaskLogDaoImpl.class);

    private final SqlSessionFactory sqlSessionFactory;

    /**
     * Constructs a new TaskLogDaoImpl instance.
     *
     * @param sqlSessionFactory  factory instance
     */
    public TaskLogDaoImpl(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    /**
     * Creates the task log.
     *
     * @param taskLog  taskLog value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean createTaskLog(TaskLog taskLog) {
        if (taskLog == null || taskLog.getTaskLogId() == null || taskLog.getTaskInstanceId() == null || taskLog.getMessage() == null) {
            logger.error("Cannot create task log with null values for required fields");
            return false;
        }

        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskLogMapper mapper = sqlSession.getMapper(TaskLogMapper.class);
            int result = mapper.insert(taskLog);
            sqlSession.commit();
            return result > 0;
        } catch (Exception e) {
            logger.error("Error creating task log", e);
            return false;
        }
    }

    @Override
    /**
     * Returns the task logs by task instance id.
     *
     * @param taskInstanceId  instance identifier
     * @return a list of matching records
     */
    public List<TaskLog> getTaskLogsByTaskInstanceId(String taskInstanceId) {
        if (taskInstanceId == null || taskInstanceId.isEmpty()) {
            logger.error("Task instance ID cannot be null or empty");
            return new ArrayList<>();
        }

        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TaskLogMapper mapper = sqlSession.getMapper(TaskLogMapper.class);
            return mapper.selectByTaskInstanceId(taskInstanceId);
        } catch (Exception e) {
            logger.error("Error getting task logs for task instance: {}", taskInstanceId, e);
            return new ArrayList<>();
        }
    }
}