/*
 * Description: DAO interface for workflow run-history view queries
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao;

import com.ts.model.vo.WorkflowInstanceVO;

import java.util.Date;
import java.util.List;

/**
 * DAO interface for querying the workflow run-history view
 * ({@code V_WF_INSTANCES_HISTORY}).
 *
 * <p>History records are read-only; all mutation goes through
 * {@link com.ts.dao.WfInstanceDao} and {@link com.ts.dao.TaskInstanceDao}.
 */
public interface HistoryDao {

    /**
     * Search workflow instances with filters
     */
    List<WorkflowInstanceVO> search(
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