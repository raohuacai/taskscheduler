/*
 * Description: Static utility service for appending timestamped log entries to task execution logs
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service.impl;

import com.ts.common.utils.UniqueIdGenerator;
import com.ts.dao.TaskLogDao;
import com.ts.model.entity.TaskLog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
/**
 * Utility service for appending timestamped log entries to a task instance's
 * execution log stored in the database.
 *
 * <p>Uses static methods for convenience so callers do not need a service
 * reference injected. All writes are synchronous and fail-silent (exceptions
 * are caught and logged) to avoid disrupting task execution on log write errors.
 */
public class TaskLogService {
    private static final Logger logger = LogManager.getLogger(TaskLogService.class);

    private static TaskLogDao taskLogDao;

    /**
     * Constructor with DAO instance
     *
     * @param taskLogDao Task Log DAO instance
     */
    public TaskLogService(TaskLogDao taskLogDao) {
        this.taskLogDao = taskLogDao;
    }

    /**
     * Static utility method to add a task log entry
     *
     * @param wfInstanceId   Workflow instance ID
     * @param taskInstanceId Task instance ID
     * @param message        Log message (max 128 chars)
     * @return true if successful, false otherwise
     */
    public static boolean addTaskLog(String wfInstanceId, String taskInstanceId, String message) {
        logger.debug("Adding task log for workflow: {}, task: {}", wfInstanceId, taskInstanceId);

        if (taskInstanceId == null || message == null || message.isEmpty()) {
            logger.error("Cannot add task log with null wfInstanceId or taskInstanceId");
            return false;
        }

        try {
            // Truncate message if longer than 128 characters
            String truncatedMessage = message;
            if (message != null && message.length() > 128) {
                logger.warn("{}", truncatedMessage);
                truncatedMessage = message.substring(0, 128);
            }

            TaskLog taskLog = new TaskLog();
            taskLog.setTaskLogId(UniqueIdGenerator.generateId(UniqueIdGenerator.IdType.TASK_LOG));
            taskLog.setWfInstanceId(wfInstanceId);
            taskLog.setTaskInstanceId(taskInstanceId);
            taskLog.setCreationDate(new Date());
            taskLog.setMessage(truncatedMessage);
            taskLogDao.createTaskLog(taskLog);
            return true;
        } catch (Exception e) {
            logger.error("Error adding task log", e);
            return false;
        }
    }

    /**
     * Adds the task log.
     *
     * @param taskLog  taskLog value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public static boolean addTaskLog(TaskLog taskLog) {
        if (taskLog == null) {
            logger.error("Cannot add null task log");
            return false;
        }

        if (taskLog.getTaskInstanceId() == null || taskLog.getWfInstanceId() == null) {
            logger.error("Task instance ID and workflow instance ID cannot be null");
            return false;
        }

        // Generate ID if not provided
        if (taskLog.getTaskLogId() == null) {
            taskLog.setTaskLogId(UniqueIdGenerator.generateId(UniqueIdGenerator.IdType.TASK_LOG));
        }

        // Set creation date if not provided
        if (taskLog.getCreationDate() == null) {
            taskLog.setCreationDate(new Date());
        }

        // Truncate message if longer than 512 characters (DB constraint)
        if (taskLog.getMessage() != null && taskLog.getMessage().length() > 128) {
            String truncatedMessage = taskLog.getMessage().substring(0, 128);
            logger.warn("Task log message truncated to 128 chars");
            taskLog.setMessage(truncatedMessage);
        }

        logger.info("Adding task log for task instance: {}", taskLog.getTaskInstanceId());
        return taskLogDao.createTaskLog(taskLog);
    }

    /**
     * Returns the task logs by task instance id.
     *
     * @param taskInstanceId  instance identifier
     * @return a list of matching records
     */
    public static List<TaskLog> getTaskLogsByTaskInstanceId(String taskInstanceId) {
        if (taskInstanceId == null || taskInstanceId.isEmpty()) {
            logger.error("Task instance ID cannot be null or empty");
            return new ArrayList<>();
        }

        logger.debug("Getting task logs for task instance: {}", taskInstanceId);
        return taskLogDao.getTaskLogsByTaskInstanceId(taskInstanceId);
    }
}