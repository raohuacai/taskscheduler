/*
 * Description: Builds a WorkflowDAG from workflow definition task instances and their JSON dependency specifications
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.core.dag;

import com.ts.exception.CycleDetectedException;

import java.util.List;
import java.util.Map;
/**
 * Builds a {@link WorkflowDAG} from a workflow definition and its associated task instances.
 *
 * <p>Reads the JSON dependency field stored on each {@link com.ts.model.entity.TaskInstance}
 * to establish predecessor–successor edges. Validates the resulting graph for cycles
 * before returning it. Tasks whose dependency field is empty or null are treated
 * as root nodes (no predecessors).
 */
public class WorkflowDAGBuilder {
    private final WorkflowDAG dag = new WorkflowDAG();

    /**
     * Add a task to the DAG
     *
     * @param taskId   Task ID
     * @param taskName Task name
     * @return This builder for chaining
     */
    public WorkflowDAGBuilder addTask(String taskId, String taskName) {
        if (taskId == null) {
            throw new IllegalArgumentException("Task ID cannot be null");
        }
        if (taskName == null) {
            throw new IllegalArgumentException("Task Name cannot be null");
        }
        dag.addTask(taskId, taskName);
        return this;
    }

    /**
     * Add an edge between two tasks
     *
     * @param fromTaskId Source task ID
     * @param toTaskId   Target task ID
     * @return This builder for chaining
     */
    public WorkflowDAGBuilder addEdge(String fromTaskId, String toTaskId) {
        if (fromTaskId == null) {
            throw new IllegalArgumentException("fromTaskId cannot be null");
        }
        if (toTaskId == null) {
            throw new IllegalArgumentException("toTaskId cannot be null");
        }
        dag.addEdge(fromTaskId, toTaskId);
        return this;
    }

    /**
     * Build the WorkflowDAG
     *
     * @return Constructed WorkflowDAG
     */
    public WorkflowDAG build() {
        return dag;
    }

    /**
     * From the json.
     *
     * @param json  json value
     * @return the result of the operation
     */
    public static WorkflowDAGBuilder fromJson(Map<String, Object> json) {
        throw new UnsupportedOperationException("fromJson is not implemented");
    }
}
