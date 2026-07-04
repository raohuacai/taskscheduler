/*
 * Description: Thread-safe DAG implementation used by WorkflowInstance to determine task execution order
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.core.dag;

import com.ts.common.constants.Constants;
import com.ts.exception.CycleDetectedException;
import com.ts.model.entity.TaskInstance;
import com.ts.model.entity.TaskParameter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.*;
/**
 * Thread-safe implementation of {@link DAG} for representing workflow task dependencies.
 *
 * <p>Stores bidirectional adjacency maps ({@code predecessors} and {@code successors})
 * backed by {@link java.util.concurrent.ConcurrentHashMap} so that the DAG can be
 * read concurrently by multiple task-completion callbacks while remaining consistent.
 *
 * <p>Cycle detection uses DFS with a recursion stack; a {@link com.ts.exception.CycleDetectedException}
 * is thrown eagerly during DAG validation, before any task execution begins.
 *
 * <p>Tasks of type {@code OR} are identified via {@link #getOrTaskIds()} and
 * treated with OR-gate dependency logic by {@link com.ts.core.engine.WorkflowInstance}.
 *
 * @see WorkflowDAGBuilder
 * @see com.ts.core.engine.WorkflowInstance
 */
public class WorkflowDAG implements DAG {
    private static final Logger logger = LogManager.getLogger(WorkflowDAG.class);

    private final Map<String, Set<String>> predecessors = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> successors = new ConcurrentHashMap<>();
    private final Map<String, String> taskNames = new ConcurrentHashMap<>();
    private final Set<String> allTasks = ConcurrentHashMap.newKeySet();

    /**
     * Add a task to the DAG
     *
     * @param taskId   Task ID
     * @param taskName Task name
     */
    public void addTask(String taskId, String taskName) {
        allTasks.add(taskId);
        taskNames.put(taskId, taskName);
        predecessors.putIfAbsent(taskId, ConcurrentHashMap.newKeySet());
        successors.putIfAbsent(taskId, ConcurrentHashMap.newKeySet());
        logger.debug("Added task {} ({}) to DAG", taskId, taskName);
    }

    /**
     * Add an edge between two tasks
     *
     * @param fromTaskId Source task ID
     * @param toTaskId   Target task ID
     */
    public void addEdge(String fromTaskId, String toTaskId) {
        if (!allTasks.contains(fromTaskId) || !allTasks.contains(toTaskId)) {
            throw new IllegalArgumentException("Both tasks must be added to the DAG before creating an edge");
        }

        predecessors.get(toTaskId).add(fromTaskId);
        successors.get(fromTaskId).add(toTaskId);
        logger.debug("Added edge from {} to {}", fromTaskId, toTaskId);
    }

    @Override
    /**
     * Returns the direct predecessors.
     *
     * @param taskId  identifier
     * @return the result as a String
     */
    public List<String> getDirectPredecessors(String taskId) {
        if (!allTasks.contains(taskId)) {
            throw new IllegalArgumentException("Task not found in DAG: " + taskId);
        }
        return new ArrayList<>(predecessors.getOrDefault(taskId, new HashSet<>()));
    }

    @Override
    /**
     * Checks whether the cycle.
     *
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean hasCycle() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        // Perform DFS from each unvisited node
        for (String taskId : allTasks) {
            if (hasCycleDFS(taskId, visited, recursionStack)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasCycleDFS(String taskId, Set<String> visited, Set<String> recursionStack) {
        // If the node is already in the recursion stack, a cycle has been detected
        if (recursionStack.contains(taskId)) {
            return true;
        }

        // If already visited and not in the recursion stack, skip to avoid redundant work
        if (visited.contains(taskId)) {
            return false;
        }

        // Mark the node as visited and add it to the recursion stack
        visited.add(taskId);
        recursionStack.add(taskId);

        // Check all successor (downstream) nodes recursively
        for (String successor : successors.getOrDefault(taskId, new HashSet<>())) {
            if (hasCycleDFS(successor, visited, recursionStack)) {
                return true;
            }
        }

        // Remove the current node from the recursion stack during backtracking
        recursionStack.remove(taskId);

        return false;
    }

    /**
     * Validate the DAG structure and throw exception if invalid
     *
     * @throws CycleDetectedException if a cycle is detected
     */
    public void validate() throws CycleDetectedException {
        if (hasCycle()) {
            throw new CycleDetectedException("Cycle detected in workflow DAG");
        }
        logger.info("Workflow DAG validation successful, no cycles detected");
    }

    @Override
    /**
     * Serialises this DAG into a JSON-compatible map containing
     * {@code nodes} and {@code edges} arrays suitable for the visual editor.
     *
     * @return a {@code Map} with keys {@code "nodes"} and {@code "edges"}
     */
    public Map<String, Object> toJson() {
        Map<String, Object> result = new HashMap<>();

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        // Add nodes
        for (String taskId : allTasks) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", taskId);
            node.put("name", taskNames.getOrDefault(taskId, taskId));
            nodes.add(node);
        }

        // Add edges
        for (Map.Entry<String, Set<String>> entry : successors.entrySet()) {
            String fromTaskId = entry.getKey();
            for (String toTaskId : entry.getValue()) {
                Map<String, Object> edge = new HashMap<>();
                edge.put("source", fromTaskId);
                edge.put("target", toTaskId);
                edges.add(edge);
            }
        }

        result.put("nodes", nodes);
        result.put("edges", edges);

        return result;
    }

    @Override
    /**
     * Serialises this DAG into a JSON-compatible map enriched with task-instance
     * runtime data (status, start/end time) and parameter values.
     *
     * @param taskInstances  runtime task instance records for this workflow run
     * @param taskParameters task-level parameter definitions
     * @return a {@code Map} with {@code "nodes"} and {@code "edges"} arrays
     */
    public Map<String, Object> toJson(List<TaskInstance> taskInstances, List<TaskParameter> taskParameters) {
        Map<String, Object> result = new HashMap<>();

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        // Create a lookup map for faster access to task instances by taskId
        Map<String, TaskInstance> taskInstanceMap = new HashMap<>();
        for (TaskInstance instance : taskInstances) {
            taskInstanceMap.put(instance.getTaskInstanceId(), instance);
        }
        Map<String, TaskParameter> taskParameterMap = new HashMap<>();
        for (TaskParameter taskParameter : taskParameters) {
            taskParameterMap.put(taskParameter.getTaskInstanceId(), taskParameter);
        }

        // Add nodes
        for (String taskId : allTasks) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", taskId);
            node.put("name", taskNames.getOrDefault(taskId, taskId));

            // Get the corresponding task instance by taskId
            TaskInstance taskInstance = taskInstanceMap.get(taskId);
            TaskParameter taskParameter = taskParameterMap.get(taskId);
            // Add task instance data if available
            if (taskInstance != null) {
                node.put("taskType", taskParameter != null ? taskParameter.getTaskType() : null);
                node.put("startDate", taskInstance.getStartDate());
                node.put("endDate", taskInstance.getEndDate());
                node.put("status", taskInstance.getStatus());
                node.put("errorMessage", taskInstance.getErrorMessage());
            } else {
                // Default values if no task instance is found
                node.put("status", Constants.STATUS_WAITING);
                if (taskParameter != null) {
                    node.put("taskType", taskParameter.getTaskType());
                }
            }
            nodes.add(node);
        }

        // Add edges
        for (Map.Entry<String, Set<String>> entry : successors.entrySet()) {
            String fromTaskId = entry.getKey();
            for (String toTaskId : entry.getValue()) {
                Map<String, Object> edge = new HashMap<>();
                edge.put("source", fromTaskId);
                edge.put("target", toTaskId);
                edges.add(edge);
            }
        }

        result.put("nodes", nodes);
        result.put("edges", edges);

        return result;
    }

    /**
     * Get all tasks in the DAG
     *
     * @return Set of all task IDs
     */
    public Set<String> getAllTasks() {
        return new HashSet<>(allTasks);
    }

    /**
     * Get the total number of edges in the DAG
     *
     * @return Total edge count
     */
    public int getEdgeCount() {
        return successors.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Get the total number of edges in the DAG
     *
     * @return Total edge count
     */
    public int getTaskCount() {
        return allTasks.size();
    }

    /**
     * Check if a task is in the DAG
     *
     * @param taskId Task ID
     * @return true if the task is in the DAG, false otherwise
     */
    public boolean containsTask(String taskId) {
        return allTasks.contains(taskId);
    }
}
