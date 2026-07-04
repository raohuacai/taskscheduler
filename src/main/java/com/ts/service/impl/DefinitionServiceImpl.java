/*
 * Description: Workflow/task definition service with DAG cycle validation and paginated search
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ts.common.SeqId.SeqIdGenerator;
import com.ts.common.constants.Constants;
import com.ts.common.utils.JsonUtils;
import com.ts.core.dag.WorkflowDAG;
import com.ts.core.dag.WorkflowDAGBuilder;
import com.ts.dao.TaskDefinitionDao;
import com.ts.dao.TaskTypeDao;
import com.ts.dao.WfDefinitionDao;
import com.ts.exception.*;
import com.ts.model.entity.TaskDefinition;
import com.ts.model.entity.TaskType;
import com.ts.model.entity.WfDefinition;
import com.ts.service.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * Implementation of {@link com.ts.service.DefinitionService} that persists
 * workflow and task definitions via MyBatis DAOs.
 *
 * <p>Validates DAG acyclicity on every save to prevent cycles from reaching the
 * execution layer. Generates IDs using {@link com.ts.common.SeqId.SeqIdGenerator}.
 * Uses {@link com.github.pagehelper.PageHelper} for transparent pagination of
 * list queries.
 */
public class DefinitionServiceImpl implements DefinitionService {
    private static final Logger logger = LogManager.getLogger(DefinitionServiceImpl.class);

    private final WfDefinitionDao wfDefinitionDao;
    private final TaskDefinitionDao taskDefinitionDao;
    private final TaskTypeDao taskTypeDao;

    /**
     * Constructs a new DefinitionServiceImpl instance.
     *
     * @param wfDefinitionDao  DAO dependency
     * @param taskDefinitionDao  DAO dependency
     * @param taskTypeDao  type discriminator
     */
    public DefinitionServiceImpl(WfDefinitionDao wfDefinitionDao, TaskDefinitionDao taskDefinitionDao, TaskTypeDao taskTypeDao) {
        this.wfDefinitionDao = wfDefinitionDao;
        this.taskDefinitionDao = taskDefinitionDao;
        this.taskTypeDao = taskTypeDao;
    }

    @Override
    /**
     * Creates the workflow.
     *
     * @param wfDefinition  wfDefinition value
     * @return the result as a String
     */
    public String createWorkflow(WfDefinition wfDefinition) {
        // Generate task ID if not provided
        if (wfDefinition.getWorkflowId() == null || wfDefinition.getWorkflowId().isEmpty())
            wfDefinition.setWorkflowId(SeqIdGenerator.generateWfId());

        // Check workflow id and workflow name
        checkWorkflowParams(wfDefinition);
        // Check if workflow already exists
        WfDefinition existingWorkflow = wfDefinitionDao.getWorkflow(wfDefinition.getWorkflowId());
        if (existingWorkflow != null) {
            throw new WorkflowValidationException("Workflow already exists: " + wfDefinition.getWorkflowId());
        }
        // Check if workflow name already exists
        existingWorkflow = wfDefinitionDao.getWorkflowByName(wfDefinition.getWorkflowName());
        if (existingWorkflow != null) {
            throw new WorkflowValidationException("Workflow name already exists: " + wfDefinition.getWorkflowName());
        }
        //set flag to N if flag is not Y
        if (!wfDefinition.getEnableFlag().equals(Constants.FLAG_YES)) {
            wfDefinition.setEnableFlag(Constants.FLAG_NO);
        }
        // Set audit fields
        wfDefinition.setCreationDate(new Date());
        wfDefinition.setLastUpdateDate(new Date());

        // Create workflow
        wfDefinitionDao.saveWorkflow(wfDefinition);
        logger.info("Created workflow: {}", wfDefinition.getWorkflowId());
        return wfDefinition.getWorkflowId();
    }

    @Override
    /**
     * Updates the workflow.
     *
     * @param wfDefinition  wfDefinition value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateWorkflow(WfDefinition wfDefinition) {
        // Check workflow id and workflow name
        checkWorkflowParams(wfDefinition);
        // Check if workflow exists
        WfDefinition existingWorkflow = wfDefinitionDao.getWorkflow(wfDefinition.getWorkflowId());
        if (existingWorkflow == null) {
            throw new WorkflowNotFoundException("Workflow not found: " + wfDefinition.getWorkflowId());
        }
        // Set audit fields
        wfDefinition.setLastUpdateDate(new Date());
        // Update workflow
        wfDefinitionDao.updateWorkflow(wfDefinition);
        logger.info("Updated workflow: {}", wfDefinition.getWorkflowId());
        return true;
    }

    @Override
    /**
     * Deletes the workflow.
     *
     * @param workflowId  workflowId identifier
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean deleteWorkflow(String workflowId) {
        // Check workflow Id
        checkWorkflowId(workflowId);
        // Delete tasks first
        taskDefinitionDao.deleteTasksByWorkflowId(workflowId);
        // Delete workflow
        wfDefinitionDao.deleteWorkflow(workflowId);
        logger.info("Deleted workflow: {}", workflowId);
        return true;
    }

    @Override
    /**
     * Returns the workflow.
     *
     * @param workflowId  workflowId identifier
     * @return the requested workflow object, or null if not found
     */
    public WfDefinition getWorkflow(String workflowId) {
        return checkWorkflowId(workflowId);
    }

    @Override
    /**
     * Returns the workflow-definition-level parameters as a map by
     * deserialising the JSON stored in the {@code pvalue} column.
     *
     * @param workflowId the workflow definition ID
     * @return a mutable map of parameter key-value pairs, or an empty map if none are defined
     */
    public Map<String, Object> getWorkflowParamsAsMap(String workflowId) {
        WfDefinition workflow = getWorkflow(workflowId);
        if (workflow == null || workflow.getPvalue() == null) {
            return new HashMap<>();
        }

        Map<String, Object> paramsMap = JsonUtils.jsonToMap(workflow.getPvalue());
        return paramsMap != null ? paramsMap : new HashMap<>();
    }

    @Override
    /**
     * Searches workflow instances with optional filters and returns a paginated result.
     *
     * @param workflowId  workflowId identifier
     * @param workflowName  workflowName name
     * @param pageNum  page number (1-based)
     * @param pageSize  page size
     * @param orderBy  ORDER BY clause
     * @return a paginated result set
     */
    public PageInfo<WfDefinition> search(String workflowId, String workflowName, int pageNum, int pageSize, String orderBy) {
        // Begin paginated query
        //PageHelper.startPage(pageNum, pageSize);
        PageHelper.startPage(pageNum, pageSize, (orderBy == null || orderBy.isEmpty()) ? "WORKFLOW_ID" : orderBy);
        // Invoke the existing query method with filters
        List<WfDefinition> workflows = wfDefinitionDao.getAllWorkflows(workflowId, workflowName);
        // Wrap results with pagination metadata
        return new PageInfo<>(workflows);
    }

    @Override
    /**
     * Returns the DAG structure of a workflow definition as a JSON-compatible map
     * containing {@code nodes} and {@code edges} arrays.
     *
     * @param workflowId the workflow definition ID
     * @return the DAG representation suitable for the visual editor
     * @throws com.ts.exception.WorkflowNotFoundException if the workflow does not exist
     */
    public Map<String, Object> getWorkflowDAG(String workflowId) {
        // check and get workflow
        WfDefinition workflow = checkWorkflowId(workflowId);

        Map<String, Object> dagJson = new HashMap<>();
        try {
            WorkflowDAG dag = buildWorkflowDAG(workflowId);
            dagJson = dag.toJson();
        } catch (CycleDetectedException ex) {

        }
        // Add workflow metadata
        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("workflowId", workflow.getWorkflowId());
        workflowJson.put("workflowName", workflow.getWorkflowName());
        workflowJson.put("workflowDesc", workflow.getWorkflowDesc());
        workflowJson.put("enableFlag", workflow.getEnableFlag());
        workflowJson.put("dag", dagJson);
        return workflowJson;
    }

    @Override
    /**
     * Builds the workflow dag.
     *
     * @param workflowId  workflowId identifier
     * @return the result of the operation
     */
    public WorkflowDAG buildWorkflowDAG(String workflowId) {
        // check and get workflow
        checkWorkflowId(workflowId);
        // Get Task list
        List<TaskDefinition> tasks = taskDefinitionDao.getTasksByWorkflowId(workflowId);
        // Get dag Builder
        WorkflowDAGBuilder builder = new WorkflowDAGBuilder();
        // Add ALL tasks to the DAG regardless of enableFlag status
        for (TaskDefinition task : tasks) {
            builder.addTask(task.getTaskId(), task.getTaskName());
        }
        // Add edges to the DAG for ALL tasks, regardless of enableFlag
        for (TaskDefinition task : tasks) {
            if (task.getPredecessors() != null) {
                String[] predecessors = task.getPredecessors().split(",");
                for (String predecessor : predecessors) {
                    predecessor = predecessor.trim();
                    if (!predecessor.isEmpty()) {
                        if (!builder.build().containsTask(predecessor)) {
                            throw new WorkflowValidationException("Task references non-existent predecessor: "
                                    + task.getTaskId() + " -> " + predecessor);
                        }
                        builder.addEdge(predecessor, task.getTaskId());
                    }
                }
            }
        }
        // Check for cycles
        WorkflowDAG dag = builder.build();
        if (dag.hasCycle()) {
            throw new CycleDetectedException("Cycle detected in workflow DAG: " + workflowId);
        }
        return dag;
    }

    /**
     * Checks the workflow id.
     *
     * @param workflowId  workflowId identifier
     * @return the result of the operation
     */
    public WfDefinition checkWorkflowId(String workflowId) {
        if (workflowId == null || workflowId.isEmpty()) {
            logger.error("Workflow ID cannot be null or empty");
            throw new IllegalArgumentException("Workflow ID cannot be null or empty");
        }
        // Check if workflow exists
        WfDefinition workflow = wfDefinitionDao.getWorkflow(workflowId);
        if (workflow == null) {
            logger.error("Workflow not found: {}", workflowId);
            throw new WorkflowNotFoundException("Workflow not found: " + workflowId);
        }
        return workflow;
    }

    /**
     * Checks the workflow params.
     *
     * @param workflowDefinition  workflowDefinition value
     * @return the result of the operation
     */
    public WfDefinition checkWorkflowParams(WfDefinition workflowDefinition) {
        if (workflowDefinition.getWorkflowId() == null || workflowDefinition.getWorkflowId().isEmpty()) {
            logger.error("Workflow ID cannot be null or empty");
            throw new IllegalArgumentException("Workflow ID cannot be null or empty");
        }
        if (workflowDefinition.getWorkflowName() == null || workflowDefinition.getWorkflowName().isEmpty()) {
            logger.error("Workflow name cannot be null or empty");
            throw new WorkflowValidationException("Workflow name cannot be null or empty");
        }
        return workflowDefinition;
    }

    @Override
    /**
     * Creates the task.
     *
     * @param workflowId  workflowId identifier
     * @param taskDefinition  taskDefinition value
     * @return the result as a String
     */
    public String createTask(String workflowId, TaskDefinition taskDefinition) {
        // Generate task ID if not provided
        if (taskDefinition.getTaskId() == null || taskDefinition.getTaskId().isEmpty()) {
            taskDefinition.setTaskId(SeqIdGenerator.generateTaskId());
        }
        checkWorkflowId(workflowId);
        checkTaskParams(taskDefinition);

        // Check if task already exists
        TaskDefinition existingTask = taskDefinitionDao.getTask(taskDefinition.getTaskId());
        if (existingTask != null) {
            throw new TaskValidationException("Task already exists: " + taskDefinition.getTaskId());
        }
        // Check if task name already exists
        existingTask = taskDefinitionDao.getTaskByName(taskDefinition.getWorkflowId(), taskDefinition.getTaskName());
        if (existingTask != null) {
            throw new TaskValidationException("Task name already exists: " + taskDefinition.getTaskName());
        }

        // Set audit fields
        taskDefinition.setCreationDate(new Date());
        taskDefinition.setLastUpdateDate(new Date());

        // Create task
        taskDefinitionDao.saveTask(taskDefinition);

        logger.info("Created task: {} in workflow: {}", taskDefinition.getTaskId(), workflowId);
        return taskDefinition.getTaskId();
    }

    @Override
    /**
     * Updates the task.
     *
     * @param taskDefinition  taskDefinition value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateTask(TaskDefinition taskDefinition) {
        // Check Task id and workflow name
        checkTaskParams(taskDefinition);
        // Check if task exists
        TaskDefinition existingTask = taskDefinitionDao.getTask(taskDefinition.getTaskId());
        if (existingTask == null) {
            throw new TaskNotFoundException("Task not found: " + taskDefinition.getTaskId());
        }
        // Set audit fields
        taskDefinition.setLastUpdateDate(new Date());
        // Update workflow
        taskDefinitionDao.updateTask(taskDefinition);
        logger.debug("Updated Task: {}", taskDefinition.getTaskId());
        return true;
    }

    @Override
    /**
     * Updates the predecessors.
     *
     * @param taskDefinition  taskDefinition value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updatePredecessors(TaskDefinition taskDefinition) {
        // Set audit fields
        taskDefinition.setLastUpdateDate(new Date());
        // Update workflow
        boolean result = taskDefinitionDao.updatePredecessors(taskDefinition);
        logger.debug("Updated Task: {} with result {}", taskDefinition.getTaskId(), result);
        return result;
    }


    @Override
    /**
     * Deletes the task.
     *
     * @param taskId  identifier
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean deleteTask(String taskId) {
        // Check if task exists
        checkTaskId(taskId);
        // Delete task
        taskDefinitionDao.deleteTask(taskId);
        logger.info("Deleted task: {}", taskId);
        return true;
    }

    @Override
    /**
     * Returns the task.
     *
     * @param taskId  identifier
     * @return the requested task object, or null if not found
     */
    public TaskDefinition getTask(String taskId) {
        return checkTaskId(taskId);
    }

    @Override
    /**
     * Returns the tasks by workflow id.
     *
     * @param workflowId  workflowId identifier
     * @return a list of matching records
     */
    public List<TaskDefinition> getTasksByWorkflowId(String workflowId) {
        checkWorkflowId(workflowId);
        // Get tasks
        List<TaskDefinition> tasks = taskDefinitionDao.getTasksByWorkflowId(workflowId);
        return tasks;
    }

    /**
     * Checks the task id.
     *
     * @param taskId  identifier
     * @return the result of the operation
     */
    public TaskDefinition checkTaskId(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            logger.error("Task ID cannot be null or empty");
            throw new IllegalArgumentException("Task ID cannot be null or empty");
        }
        // Check if Task exists
        TaskDefinition task = taskDefinitionDao.getTask(taskId);
        if (task == null) {
            logger.error("Task not found: {}", taskId);
            throw new TaskNotFoundException("Task not found: " + taskId);
        }
        return task;
    }

    /**
     * Checks the task params.
     *
     * @param taskDefinition  taskDefinition value
     * @return the result of the operation
     */
    public TaskDefinition checkTaskParams(TaskDefinition taskDefinition) {
        if (taskDefinition.getWorkflowId() == null || taskDefinition.getWorkflowId().isEmpty()) {
            logger.error("Workflow ID cannot be null or empty");
            throw new IllegalArgumentException("Workflow ID cannot be null or empty");
        }
        if (taskDefinition.getTaskId() == null || taskDefinition.getTaskId().isEmpty()) {
            logger.error("Task ID cannot be null or empty");
            throw new IllegalArgumentException("Task ID cannot be null or empty");
        }
        if (taskDefinition.getTaskName() == null || taskDefinition.getTaskName().isEmpty()) {
            logger.error("Task name cannot be null or empty");
            throw new TaskValidationException("Task name cannot be null or empty");
        }
        if (taskDefinition.getTaskType() == null || taskDefinition.getTaskType().isEmpty()) {
            logger.error("Task type cannot be null or empty");
            throw new TaskValidationException("Task type cannot be null or empty");
        }
        // Check task type
        TaskType taskType = taskTypeDao.getTaskTypeByName(taskDefinition.getTaskType());
        if (taskType == null) {
            throw new TaskTypeNotFoundException("Task Type not found: " + taskDefinition.getTaskType());
        }
        return taskDefinition;
    }
}
