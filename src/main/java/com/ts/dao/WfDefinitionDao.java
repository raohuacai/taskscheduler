/*
 * Description: DAO interface for WfDefinition CRUD
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao;


import com.ts.model.entity.WfDefinition;

import java.util.List;
import java.util.Map;
/**
 * DAO interface for {@link com.ts.model.entity.WfDefinition} CRUD operations.
 */
public interface WfDefinitionDao {
    WfDefinition getWorkflow(String workflowId);

    WfDefinition getWorkflowByName(String workflowName);

    List<WfDefinition> getAllWorkflows(String workflowId, String workflowName);

    boolean saveWorkflow(WfDefinition workflow);

    boolean updateWorkflow(WfDefinition workflow);

    boolean deleteWorkflow(String workflowId);

    Map<String, Object> getWorkflowParamsAsMap(String workflowId);
}