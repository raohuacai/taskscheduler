/*
 * Description: MyBatis mapper for TS_TASK_DEFINITION
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.mapper;

import com.ts.model.entity.TaskDefinition;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * MyBatis mapper interface for {@code TS_TASK_DEFINITION} CRUD.
 */
public interface TaskDefinitionMapper {
    TaskDefinition selectByPrimaryKey(@Param("taskId") String taskId);

    TaskDefinition selectByName(@Param("workflowId") String workflowId, @Param("taskName") String taskName);

    List<TaskDefinition> selectByWorkflowId(@Param("workflowId") String workflowId);

    int insert(TaskDefinition record);

    int updateByPrimaryKey(TaskDefinition record);

    int updatePredecessors(TaskDefinition record);

    int deleteByPrimaryKey(@Param("taskId") String taskId);

    int deleteByWorkflowId(@Param("workflowId") String workflowId);
}