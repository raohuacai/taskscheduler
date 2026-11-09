/*
 * Description: MyBatis mapper for task-level parameters
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.mapper;

import com.ts.model.entity.TaskParameter;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * MyBatis mapper interface for task-level parameter definitions.
 */
public interface TaskParameterMapper {
    TaskParameter selectByPrimaryKey(@Param("TaskParamId") String TaskParamId);

    TaskParameter selectByTaskInstanceId(@Param("taskInstanceId") String taskInstanceId);

    List<TaskParameter> selectBywfInstanceId(@Param("wfInstanceId") String wfInstanceId);

    int insert(TaskParameter record);

    int updatePvalueByPrimaryKey(@Param("TaskParamId") String TaskParamId, @Param("pvalue") String pvalue);

    int updatePvalueByTaskInstanceId(@Param("taskInstanceId") String taskInstanceId, @Param("pvalue") String pvalue);

    List<TaskParameter> selectByWfInstanceIdAndTaskIds(@Param("wfInstanceId") String wfInstanceId,
                                                       @Param("taskIds") List<String> taskIds);
}