/*
 * Description: DAO interface for workflow-level parameter definitions
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao;

import com.ts.model.entity.WfParameter;
/**
 * DAO interface for workflow-level parameter definitions.
 */
public interface WfParameterDao {
    /**
     * Get workflow instance parameters by workflow instance ID
     *
     * @param wfInstanceId workflow instance ID
     * @return WfParams object or null if not found
     */
    WfParameter getWfParams(String wfInstanceId);

    /**
     * Get workflow instance parameters by primary key
     *
     * @param wfInstanceParamId workflow instance parameter ID
     * @return WfParams object or null if not found
     */
    WfParameter getWfParamById(String wfInstanceParamId);

    /**
     * Create new workflow instance parameters
     *
     * @param wfParameter parameters to create
     * @return created parameter ID or null if failed
     */
    String createWfParams(WfParameter wfParameter);

}
