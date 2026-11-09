/*
 * Description: Contract for a directed acyclic graph representing task dependencies in a workflow
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.core.dag;

import com.ts.model.entity.TaskInstance;
import com.ts.model.entity.TaskParameter;

import java.util.*;
/**
 * Contract for a directed acyclic graph (DAG) that models workflow task dependencies.
 *
 * <p>Implementations must support adding tasks and directed edges, retrieving
 * predecessors and successors, and detecting cycles. The DAG is the central
 * data structure used by {@link com.ts.core.engine.WorkflowInstance} to determine
 * the execution order of tasks.
 */
public interface DAG {
    /**
     * Get direct predecessors of a task
     *
     * @param taskId Task ID
     * @return List of direct predecessor task IDs
     */
    List<String> getDirectPredecessors(String taskId);

    /**
     * Check if the DAG has a cycle
     *
     * @return true if a cycle is detected, false otherwise
     */
    boolean hasCycle();

    /**
     * Get JSON representation of the DAG
     *
     * @return Map representing the DAG structure
     */
    Map<String, Object> toJson();

    Map<String, Object> toJson(List<TaskInstance> taskInstances, List<TaskParameter> taskParameters);
}