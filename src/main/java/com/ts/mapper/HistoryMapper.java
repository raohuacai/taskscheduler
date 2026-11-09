/*
 * Description: MyBatis mapper for workflow run-history view
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.mapper;

import com.ts.model.vo.WorkflowInstanceVO;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * MyBatis mapper interface for the workflow run-history view
 * ({@code V_WF_INSTANCES_HISTORY}).
 */
public interface HistoryMapper {

    /**
     * Search workflow instances with filters
     */
    List<WorkflowInstanceVO> search(
            @Param("workflowId") String workflowId,
            @Param("workflowName") String workflowName,
            @Param("instanceId") String instanceId,
            @Param("parentWfInstanceId") String parentWfInstanceId,
            @Param("topParentInstanceId") String topParentInstanceId,
            @Param("status") String status,
            @Param("startDateFrom") Date startDateFrom,
            @Param("startDateTo") Date startDateTo
    );

    /**
     * Get workflow instance by ID
     */
    WorkflowInstanceVO getById(@Param("instanceId") String instanceId);
}