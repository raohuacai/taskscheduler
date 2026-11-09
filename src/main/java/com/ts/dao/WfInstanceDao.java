/*
 * Description: DAO interface for WfInstance lifecycle management
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao;

import com.ts.model.entity.WfInstance;

import java.util.Date;
import java.util.List;
/**
 * DAO interface for {@link com.ts.model.entity.WfInstance} lifecycle management.
 *
 * <p>Covers creation, status transitions, and retrieval by instance ID, parent ID,
 * or workflow definition ID.
 */
public interface WfInstanceDao {
    WfInstance getWfInstance(String wfInstanceId);

    List<WfInstance> getAllWfInstances(String workflowId, String workflowName, String wfInstanceId, String parentWfInstanceId, String status,
                                       Date startDateFrom, Date startDateTo);

    List<WfInstance> getAllWfInstances(String workflowId, String workflowName, String wfInstanceId, String parentWfInstanceId,String topParentInstanceId, String status,
                                       Date startDateFrom, Date startDateTo);

    String createWfInstance(WfInstance wfInstance);

    boolean updateStatusToError(WfInstance wfInstance);

    boolean updateStatusToRunning(WfInstance wfInstance);

    boolean updateStatus(WfInstance wfInstance);
}