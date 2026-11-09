/*
 * Description: MyBatis implementation of TaskTypeDao
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao.impl;

import com.ts.common.utils.ParamUtils;
import com.ts.dao.TaskTypeDao;
import com.ts.model.entity.TaskType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * MyBatis-based implementation of {@link com.ts.dao.TaskTypeDao}.
 */
public class TaskTypeDaoImpl implements TaskTypeDao {
    private static final Logger logger = LogManager.getLogger(TaskTypeDaoImpl.class);
    private final SqlSessionFactory sqlSessionFactory;

    /**
     * Constructs a new TaskTypeDaoImpl instance.
     *
     * @param sqlSessionFactory  factory instance
     */
    public TaskTypeDaoImpl(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    /**
     * Returns the all task types.
     *
     * @return a list of matching records
     */
    public List<TaskType> getAllTaskTypes() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            return sqlSession.selectList("com.ts.dao.TaskTypeMapper.selectAll");
        } catch (Exception e) {
            logger.error("Error getting all task types", e);
            return new ArrayList<>();
        }
    }

    @Override
    /**
     * Returns the task type by name.
     *
     * @param typeName  typeName name
     * @return the requested tasktypebyname object, or null if not found
     */
    public TaskType getTaskTypeByName(String typeName) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            return sqlSession.selectOne("com.ts.dao.TaskTypeMapper.selectByPrimaryKey", typeName);
        } catch (Exception e) {
            logger.error("Error getting task type by name: " + typeName, e);
            return null;
        }
    }

    @Override
    /**
     * Searches workflow instances with optional filters and returns a paginated result.
     *
     * @param typeGroup  type discriminator
     * @param typeName  typeName name
     * @return a list of matching records
     */
    public List<TaskType> search(String typeGroup, String typeName) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Map<String, Object> params = new HashMap<>();
            params.put("typeGroup", typeGroup);
            params.put("typeName", ParamUtils.likePattern(typeName));
            return sqlSession.selectList("com.ts.dao.TaskTypeMapper.search", params);
        } catch (Exception e) {
            logger.error("Error getting task types by group or name: " + typeGroup + "," + typeGroup, e);
            return new ArrayList<>();
        }
    }

    @Override
    /**
     * Inserts the task type.
     *
     * @param taskType  type discriminator
     * @return the numeric result
     */
    public int insertTaskType(TaskType taskType) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            return sqlSession.insert("com.ts.dao.TaskTypeMapper.insert", taskType);
        } catch (Exception e) {
            logger.error("Error inserting task type: " + taskType, e);
            return 0;
        }
    }

    @Override
    /**
     * Updates the task type.
     *
     * @param taskType  type discriminator
     * @return the numeric result
     */
    public int updateTaskType(TaskType taskType) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            return sqlSession.update("com.ts.dao.TaskTypeMapper.updateByPrimaryKey", taskType);
        } catch (Exception e) {
            logger.error("Error updating task type: " + taskType, e);
            return 0;
        }
    }

    @Override
    /**
     * Deletes the task type.
     *
     * @param typeName  typeName name
     * @return the numeric result
     */
    public int deleteTaskType(String typeName) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            return sqlSession.delete("com.ts.dao.TaskTypeMapper.deleteByPrimaryKey", typeName);
        } catch (Exception e) {
            logger.error("Error deleting task type: " + typeName, e);
            return 0;
        }
    }
}
