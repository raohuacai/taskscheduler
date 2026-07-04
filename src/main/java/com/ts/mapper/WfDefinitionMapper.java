/*
 * Description: MyBatis mapper for TS_WF_DEFINITION
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.mapper;

import com.ts.model.entity.WfDefinition;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * MyBatis mapper interface for {@code TS_WF_DEFINITION} CRUD.
 */
public interface WfDefinitionMapper {
    WfDefinition selectByPrimaryKey(@Param("workflowId") String workflowId);

    WfDefinition selectByName(@Param("workflowName") String workflowName);

    List<WfDefinition> selectAll(@Param("workflowId") String workflowId,
                                 @Param("workflowName") String workflowName);

    int insert(WfDefinition record);

    int updateByPrimaryKey(WfDefinition record);

    int deleteByPrimaryKey(@Param("workflowId") String workflowId);
}