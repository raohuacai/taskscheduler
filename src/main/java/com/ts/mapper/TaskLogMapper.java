/*
 * Description: MyBatis mapper for task execution log table
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.mapper;

import com.ts.model.entity.TaskLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * MyBatis mapper interface for task execution log entries.
 */
public interface TaskLogMapper {
    /**
     * Insert a new task log record
     *
     * @param record TaskLog record to insert
     * @return Number of rows affected
     */
    int insert(TaskLog record);

    /**
     * Select task logs by task instance ID
     *
     * @param taskInstanceId Task instance ID
     * @return List of task logs
     */
    List<TaskLog> selectByTaskInstanceId(@Param("taskInstanceId") String taskInstanceId);
}