/*
 * Description: Task-type registry service with factory-cache invalidation on every mutation
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ts.common.constants.Constants;
import com.ts.dao.TaskTypeDao;
import com.ts.model.entity.TaskType;
import com.ts.service.*;
import com.ts.task.TaskFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;
import java.util.List;
/**
 * Implementation of {@link com.ts.service.TaskTypeService} that persists
 * task-type definitions in the database and invalidates the
 * {@link com.ts.task.TaskFactory} class cache on every mutation.
 *
 * <p>The factory cache is invalidated (not just updated) on create/update/delete
 * to ensure consistency — the next task instantiation will reload from the DB.
 */
public class TaskTypeServiceImpl implements TaskTypeService {
    private static final Logger logger = LogManager.getLogger(TaskTypeServiceImpl.class);

    private final TaskTypeDao taskTypeDao;
    private TaskFactory taskFactory;

    /**
     * Constructs a new TaskTypeServiceImpl instance.
     *
     * @param taskTypeDao  type discriminator
     */
    public TaskTypeServiceImpl(TaskTypeDao taskTypeDao) {
        this.taskTypeDao = taskTypeDao;
    }

    /**
     * Sets the task factory.
     *
     * @param taskFactory  factory instance
     */
    public void setTaskFactory(TaskFactory taskFactory) {
        this.taskFactory = taskFactory;
    }

    @Override
    /**
     * Registers the task type.
     *
     * @param taskType  type discriminator
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean registerTaskType(TaskType taskType) {
        try {
            if (taskType == null || taskType.getTypeName() == null || taskType.getTypeName().isEmpty()) {
                throw new IllegalArgumentException("Invalid task type");
            }

            if (taskType.getClassName() == null || taskType.getClassName().trim().isEmpty()) {
                throw new IllegalArgumentException("Invalid class name");
            }

            // Check if task type already exists
            TaskType existingTaskType = taskTypeDao.getTaskTypeByName(taskType.getTypeName());
            if (existingTaskType != null) {
                throw new IllegalArgumentException("Task type already exists: " + taskType.getTypeName());
            }

            // Set creation date
            if (taskType.getCreationDate() == null) {
                taskType.setCreationDate(new Date());
            }

            // Set interruptible flag if not set
            if (taskType.getInterruptableFlag() == null) {
                taskType.setInterruptableFlag(Constants.FLAG_YES);
            }

            // Register task type to task factory
            taskFactory.registerTaskType(taskType.getTypeName(), taskType.getClassName());
            // Register task type to database
            taskTypeDao.insertTaskType(taskType);
        } catch (Exception e) {
            logger.error("Fail to register Task Type {}, {}", taskType != null ? taskType.getTypeName() : "null", e.getMessage());
            return false;
        }
        logger.debug("Registered task type: {}", taskType.getTypeName());
        return true;
    }

    @Override
    /**
     * Updates the task type.
     *
     * @param taskType  type discriminator
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateTaskType(TaskType taskType) {
        try {
            if (taskType == null || taskType.getTypeName() == null || taskType.getTypeName().isEmpty()) {
                throw new IllegalArgumentException("Invalid task type");
            }

            if (taskType.getClassName() == null || taskType.getClassName().trim().isEmpty()) {
                throw new IllegalArgumentException("Invalid class name");
            }

            // Check if task type exists
            TaskType existingTaskType = taskTypeDao.getTaskTypeByName(taskType.getTypeName());
            if (existingTaskType == null) {
                throw new IllegalArgumentException("Task type not found: " + taskType.getTypeName());
            }

            // Register task type to task factory
            taskFactory.unregisterTaskType(taskType.getTypeName());
            taskFactory.registerTaskType(taskType.getTypeName(), taskType.getClassName());
            // Update task type
            taskTypeDao.updateTaskType(taskType);
        } catch (Exception e) {
            logger.error("Fail to update Task Type {}, {}", taskType != null ? taskType.getTypeName() : "null", e.getMessage());
            return false;
        }
        logger.debug("Updated task type: {}", taskType.getTypeName());
        return true;
    }

    @Override
    /**
     * Unregister the task type.
     *
     * @param typeName  typeName name
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean unregisterTaskType(String typeName) {
        try {
            if (typeName == null || typeName.isEmpty()) {
                throw new IllegalArgumentException("Task type name cannot be null or empty");
            }

            // Check if task type exists
            TaskType existingTaskType = taskTypeDao.getTaskTypeByName(typeName);
            if (existingTaskType == null) {
                throw new IllegalArgumentException("Task type not found: " + typeName);
            }

            // unregister task type from task factory
            taskFactory.unregisterTaskType(typeName);
            // Unregister task type
            taskTypeDao.deleteTaskType(typeName);
        } catch (Exception e) {
            logger.error("Fail to update Task Type {}, {}", typeName, e.getMessage());
            return false;
        }
        logger.debug("Unregistered task type: {}", typeName);
        return true;
    }

    @Override
    /**
     * Returns the task type by exact name match; used by edit operations.
     *
     * @param typeName exact type name
     * @return the TaskType, or null if not found
     */
    public TaskType getTaskType(String typeName) {
        return taskTypeDao.getTaskTypeByName(typeName);
    }

    @Override
    /**
     * Searches workflow instances with optional filters and returns a paginated result.
     *
     * @param typeGroup  type discriminator
     * @param typeName  typeName name
     * @param pageNum  page number (1-based)
     * @param pageSize  page size
     * @param orderBy  ORDER BY clause
     * @return a paginated result set
     */
    public PageInfo<TaskType> search(String typeGroup, String typeName, int pageNum, int pageSize, String orderBy) {
        // Begin paginated query
        //PageHelper.startPage(pageNum, pageSize);
        PageHelper.startPage(pageNum, pageSize, (orderBy == null || orderBy.isEmpty()) ? "TYPE_GROUP,TYPE_NAME,CREATION_DATE DESC" : orderBy);
        // Invoke the existing query method with filters
        List<TaskType> taskTypes = taskTypeDao.search(typeGroup, typeName);
        // Wrap results with pagination metadata
        return new PageInfo<>(taskTypes);
    }

}