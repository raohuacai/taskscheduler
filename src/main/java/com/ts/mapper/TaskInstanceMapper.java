/*
 * Description: MyBatis mapper for TS_TASK_INSTANCE
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.mapper;

import com.ts.model.entity.TaskInstance;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * MyBatis mapper interface for {@code TS_TASK_INSTANCE} lifecycle operations.
 */
public interface TaskInstanceMapper {
    TaskInstance selectByPrimaryKey(@Param("taskInstanceId") String taskInstanceId);

    List<TaskInstance> selectBywfInstanceId(@Param("wfInstanceId") String wfInstanceId);

    List<TaskInstance> selectByTaskIdAndwfInstanceId(@Param("taskId") String taskId, @Param("wfInstanceId") String wfInstanceId);

    int insert(TaskInstance record);

    int updateStatusToError(TaskInstance record);

    int updateStatusToRunning(TaskInstance record);

    int updateStatus(TaskInstance record);
}