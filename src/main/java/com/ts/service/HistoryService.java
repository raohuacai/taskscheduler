/*
 * Description: Contract for paginated workflow run-history queries
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service;

import com.github.pagehelper.PageInfo;
import com.ts.model.vo.WorkflowInstanceVO;

import java.util.Date;
import java.util.List;

/**
 * Contract for querying paginated workflow run history from the read-optimised
 * history view ({@code V_WF_INSTANCES_HISTORY}).
 *
 * <p>History records are populated by the database view and are read-only from
 * the application perspective.
 */
public interface HistoryService {

    /**
     * Search workflow instances with pagination and sorting
     */
    PageInfo<WorkflowInstanceVO> search(
            String workflowId,
            String workflowName,
            String instanceId,
            String parentWfInstanceId,
            String topParentInstanceId,
            String status,
            Date startDateFrom,
            Date startDateTo,
            int pageNum,
            int pageSize,
            String orderBy
    );

    /**
     * Search all workflow instances without pagination
     */
    List<WorkflowInstanceVO> searchAll(
            String workflowId,
            String workflowName,
            String instanceId,
            String parentWfInstanceId,
            String topParentInstanceId,
            String status,
            Date startDateFrom,
            Date startDateTo
    );

}