/*
 * Description: MyBatis mapper for TS_WF_INSTANCE
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.mapper;

import com.ts.model.entity.WfDefinition;
import com.ts.model.entity.WfInstance;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
/**
 * MyBatis mapper interface for {@code TS_WF_INSTANCE} lifecycle management.
 */
public interface WfInstanceMapper {
    WfInstance selectByPrimaryKey(@Param("wfInstanceId") String wfInstanceId);

    List<WfInstance> selectByWorkflowId(@Param("workflowId") String workflowId);

    List<WfInstance> selectAll(@Param("workflowId") String workflowId,
                               @Param("workflowName") String workflowName,
                               @Param("wfInstanceId") String wfInstanceId,
                               @Param("parentWfInstanceId") String parentWfInstanceId,
                               @Param("status") String status,
                               @Param("startDateFrom") Date startDateFrom,
                               @Param("startDateTo") Date startDateTo);

    List<WfInstance> selectAll(@Param("workflowId") String workflowId,
                               @Param("workflowName") String workflowName,
                               @Param("wfInstanceId") String wfInstanceId,
                               @Param("parentWfInstanceId") String parentWfInstanceId,
                               @Param("topParentInstanceId") String topParentInstanceId,
                               @Param("status") String status,
                               @Param("startDateFrom") Date startDateFrom,
                               @Param("startDateTo") Date startDateTo);

    int insert(WfInstance record);

    int updateStatusToRunning(WfInstance record);

    int updateStatusToError(WfInstance record);

    int updateStatus(WfInstance record);
}