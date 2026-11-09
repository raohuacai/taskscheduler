/*
 * Description: MyBatis mapper for task-type registry table
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.mapper;

import com.ts.model.entity.TaskType;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * MyBatis mapper interface for the task-type registry table.
 */
public interface TaskTypeMapper {
    List<TaskType> selectAll();

    TaskType selectByPrimaryKey(@Param("typeName") String typeName);

    List<TaskType> search(@Param("typeGroup") String typeGroup, @Param("typeName") String typeName);

    int insert(TaskType record);

    int updateByPrimaryKey(TaskType record);

    int deleteByPrimaryKey(@Param("typeName") String typeName);
}