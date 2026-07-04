/*
 * Description: Parameter service merging global, workflow, and task-level parameters with inheritance chain resolution
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service.impl;

import com.ts.common.utils.JsonUtils;
import com.ts.common.constants.Constants;
import com.ts.common.utils.UniqueIdGenerator;
import com.ts.config.EmailTemplateConfig;
import com.ts.config.HttpServerConfig;
import com.ts.config.ThreadPoolConfig;
import com.ts.config.MailConfig;
import com.ts.core.dag.WorkflowDAG;
import com.ts.core.dag.WorkflowDAGBuilder;
import com.ts.dao.*;
import com.ts.exception.CycleDetectedException;
import com.ts.exception.WorkflowNotFoundException;
import com.ts.exception.WorkflowValidationException;
import com.ts.model.entity.*;
import com.ts.service.ParamsService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * Implementation of {@link com.ts.service.ParamsService} that merges parameters
 * across global, workflow-definition, task-definition, workflow-instance, and
 * task-instance levels.
 *
 * <p>Key operations:
 * <ul>
 *   <li>{@code getTaskParams} — builds the complete parameter map for a single
 *       task execution by walking the inheritance chain bottom-up.</li>
 *   <li>{@code getWorkflowInstanceParams} — merges workflow-level instance
 *       parameters with global and definition-level defaults.</li>
 *   <li>Configuration loaders ({@code getThreadPoolConfig},
 *       {@code getEmailConfig}, etc.) deserialise JSON-encoded global parameters
 *       into strongly-typed config objects.</li>
 * </ul>
 *
 * <p>DAG parameter propagation: parameter inheritance can form a DAG across
 * parent–child workflow definitions; acyclicity is validated before any save.
 */
public class ParamsServiceImpl implements ParamsService {
    private static final Logger logger = LogManager.getLogger(ParamsServiceImpl.class);

    private final GlobalParamsDao globalParamsDao;
    private final WfDefinitionDao wfDefinitionDao;
    private final TaskDefinitionDao taskDefinitionDao;
    private final WfParameterDao wfParameterDao;
    private final TaskParameterDao taskParameterDao;
    private final WfInstanceDao wfInstanceDao;
    private final TaskInstanceDao taskInstanceDao;

    /**
     * Constructs a new ParamsServiceImpl instance.
     *
     * @param globalParamsDao  DAO dependency
     * @param wfDefinitionDao  DAO dependency
     * @param taskDefinitionDao  DAO dependency
     * @param wfParameterDao  DAO dependency
     * @param taskParameterDao  DAO dependency
     * @param wfInstanceDao  DAO dependency
     * @param taskInstanceDao  DAO dependency
     */
    public ParamsServiceImpl(GlobalParamsDao globalParamsDao, WfDefinitionDao wfDefinitionDao, TaskDefinitionDao taskDefinitionDao,
                             WfParameterDao wfParameterDao,
                             TaskParameterDao taskParameterDao, WfInstanceDao wfInstanceDao, TaskInstanceDao taskInstanceDao) {
        this.globalParamsDao = globalParamsDao;
        this.wfDefinitionDao = wfDefinitionDao;
        this.taskDefinitionDao = taskDefinitionDao;
        this.wfParameterDao = wfParameterDao;
        this.taskParameterDao = taskParameterDao;
        this.wfInstanceDao = wfInstanceDao;
        this.taskInstanceDao = taskInstanceDao;
    }

    @Override
    /**
     * Returns the all global params.
     *
     * @return a list of matching records
     */
    public List<GlobalParams> getAllGlobalParams() {
        return globalParamsDao.getAllGlobalParams();
    }

    @Override
    /**
     * Returns the wf global params.
     *
     * @return the requested wfglobalparams object, or null if not found
     */
    public GlobalParams getWfGlobalParams() {
        return globalParamsDao.getGlobalParams(Constants.WF_GLOBAL_PARAM_NAME);
    }

    @Override
    /**
     * Returns the global workflow parameters as a map.
     * Deserialises the JSON from the single {@code GlobalParams} record.
     *
     * @return a mutable map of global parameter key-value pairs, or an empty map if not configured
     */
    public Map<String, Object> getWfGlobalParamsAsMap() {
        GlobalParams globalParams = getWfGlobalParams();
        if (globalParams == null || globalParams.getPvalue() == null) {
            return new HashMap<>();
        }

        Map<String, Object> paramsMap = JsonUtils.jsonToMap(globalParams.getPvalue());
        return paramsMap != null ? paramsMap : new HashMap<>();
    }

    @Override
    /**
     * Updates the wf global params.
     *
     * @param params  parameter map
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateWfGlobalParams(Map<String, Object> params) {
        return globalParamsDao.updateGlobalParams(Constants.WF_GLOBAL_PARAM_NAME, params);
    }

    // Thread pool configuration methods
    @Override
    /**
     * Returns the thread pool config.
     *
     * @return the requested threadpoolconfig object, or null if not found
     */
    public ThreadPoolConfig getThreadPoolConfig() {
        logger.debug("Retrieving thread pool configuration");
        GlobalParams configParams = globalParamsDao.getGlobalParams(Constants.THREAD_POOL_CONFIG_NAME);

        if (configParams == null || configParams.getPvalue() == null) {
            logger.warn("Thread pool configuration not found, returning default configuration");
            return new ThreadPoolConfig();
        }

        try {
            ThreadPoolConfig config = JsonUtils.jsonToObject(configParams.getPvalue(), ThreadPoolConfig.class);
            if (config == null) {
                logger.warn("Could not parse thread pool configuration, returning default");
                return new ThreadPoolConfig();
            }
            logger.debug("Retrieved thread pool configuration: {}", config);
            return config;
        } catch (Exception e) {
            logger.error("Error parsing thread pool configuration", e);
            return new ThreadPoolConfig();
        }
    }

    @Override
    /**
     * Updates the thread pool config.
     *
     * @param params  parameter map
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateThreadPoolConfig(Map<String, Object> params) {
        return globalParamsDao.updateGlobalParams(Constants.THREAD_POOL_CONFIG_NAME, params);
    }

    // email configuration methods
    @Override
    /**
     * Returns the mail config.
     *
     * @return the requested mailconfig object, or null if not found
     */
    public MailConfig getMailConfig() {
        logger.debug("Retrieving email configuration");
        GlobalParams configParams = globalParamsDao.getGlobalParams(Constants.EMAIL_CONFIG_NAME);

        if (configParams == null || configParams.getPvalue() == null) {
            logger.warn("email configuration not found, returning default configuration");
            return new MailConfig();
        }

        try {
            MailConfig config = JsonUtils.jsonToObject(configParams.getPvalue(), MailConfig.class);
            if (config == null) {
                logger.warn("Could not parse email configuration, returning default");
                return new MailConfig();
            }
            logger.info("{}", config);
            return config;
        } catch (Exception e) {
            logger.error("Error parsing email configuration", e);
            return new MailConfig();
        }
    }

    @Override
    /**
     * Updates the mail config.
     *
     * @param params  parameter map
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateMailConfig(Map<String, Object> params) {
        return globalParamsDao.updateGlobalParams(Constants.EMAIL_CONFIG_NAME, params);
    }

    // email template methods
    @Override
    /**
     * Returns the email template config.
     *
     * @return the requested emailtemplateconfig object, or null if not found
     */
    public EmailTemplateConfig getEmailTemplateConfig() {
        logger.debug("Retrieving email template");
        GlobalParams configParams = globalParamsDao.getGlobalParams(Constants.EMAIL_TEMPLATE_CONFIG_NAME);

        if (configParams == null || configParams.getPvalue() == null) {
            logger.warn("email template not found, returning default template");
            return new EmailTemplateConfig();
        }

        try {
            EmailTemplateConfig config = JsonUtils.jsonToObject(configParams.getPvalue(), EmailTemplateConfig.class);
            if (config == null) {
                logger.warn("Could not parse email template, returning default");
                return new EmailTemplateConfig();
            }
            logger.debug("{}", config);
            return config;
        } catch (Exception e) {
            logger.error("Error parsing email template", e);
            return new EmailTemplateConfig();
        }
    }

    @Override
    /**
     * Updates the email template config.
     *
     * @param params  parameter map
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateEmailTemplateConfig(Map<String, Object> params) {
        return globalParamsDao.updateGlobalParams(Constants.EMAIL_TEMPLATE_CONFIG_NAME, params);
    }

    @Override
    /**
     * Returns the http server config.
     *
     * @return the requested httpserverconfig object, or null if not found
     */
    public HttpServerConfig getHttpServerConfig() {
        logger.debug("Retrieving http server configuration");
        GlobalParams configParams = globalParamsDao.getGlobalParams(Constants.HTTP_SERVER_CONFIG_NAME);
        if (configParams == null || configParams.getPvalue() == null) {
            logger.warn("http server configuration not found, returning default configuration");
            return new HttpServerConfig();
        }

        try {
            HttpServerConfig config = JsonUtils.jsonToObject(configParams.getPvalue(), HttpServerConfig.class);
            if (config == null) {
                logger.warn("Could not parse http server configuration, returning default");
                return new HttpServerConfig();
            }
            logger.info("{}", config);
            return config;
        } catch (Exception e) {
            logger.error("Error parsing http server configuration", e);
            return new HttpServerConfig();
        }
    }

    @Override
    /**
     * Updates the http server config.
     *
     * @param params  parameter map
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateHttpServerConfig(Map<String, Object> params) {
        return globalParamsDao.updateGlobalParams(Constants.HTTP_SERVER_CONFIG_NAME, params);
    }

    @Override
    /**
     * Returns workflow-instance-level parameters merged with global defaults.
     * If no workflow instance parameter record exists, returns only global params.
     *
     * @param wfInstanceParamId the workflow parameter record ID
     * @return merged parameter map (instance overrides global values)
     */
    public Map<String, Object> getWfParamsByIdAsMap(String wfInstanceParamId) {
        // Get workflow parameters
        WfParameter wfParameter = wfParameterDao.getWfParamById(wfInstanceParamId);
        if (wfParameter == null || wfParameter.getPvalue() == null) {
            // If no workflow parameters, return just the global parameters
            return getWfGlobalParamsAsMap();
        }

        // Parse workflow parameters to map
        Map<String, Object> workflowParams = JsonUtils.jsonToMap(wfParameter.getPvalue());
        if (workflowParams == null) {
            workflowParams = new HashMap<>();
        }

        return workflowParams;
    }

    /**
     * Checks the wf instance id.
     *
     * @param wfInstanceId  instance identifier
     * @return the result of the operation
     */
    public WfParameter checkWfInstanceId(String wfInstanceId) {
        if (wfInstanceId == null || wfInstanceId.isEmpty()) {
            logger.error("Workflow Instance ID cannot be null or empty");
            throw new IllegalArgumentException("Workflow Instance ID cannot be null or empty");
        }
        // Check if workflow exists
        WfParameter wfParameter = getWfParamsByInstanceId(wfInstanceId);
        if (wfParameter == null) {
            logger.error("Workflow instance not found: {}", wfInstanceId);
            throw new WorkflowNotFoundException("Workflow instance not found: " + wfInstanceId);
        }
        return wfParameter;
    }

    @Override
    /**
     * Returns the DAG structure for a workflow instance enriched with
     * runtime task-instance status and parameter values.
     *
     * @param wfInstanceId the workflow instance ID
     * @return the DAG representation containing nodes (with status) and edges
     */
    public Map<String, Object> getWorkflowDAG(String wfInstanceId) {
        // check and get workflow
        WfParameter wfParameter = checkWfInstanceId(wfInstanceId);
        WfInstance wfInstance = wfInstanceDao.getWfInstance(wfInstanceId);
        List<TaskParameter> taskParameters = getTaskParamsByWfInstanceId(wfInstanceId);
        List<TaskInstance> taskInstances = taskInstanceDao.getTaskInstancesByWfInstanceId(wfInstanceId);

        Map<String, Object> dagJson = new HashMap<>();
        try {
            WorkflowDAG dag = buildWorkflowDAG(wfInstanceId);
            dagJson = dag.toJson(taskInstances, taskParameters);
        } catch (CycleDetectedException ex) {

        }
        // Add workflow metadata
        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("workflowId", wfParameter.getWorkflowId());
        workflowJson.put("workflowName", wfParameter.getWorkflowName());
        workflowJson.put("wfInstanceId", wfParameter.getWfInstanceId());
        workflowJson.put("enableFlag", wfParameter.getEnableFlag());
        workflowJson.put("startDate", wfInstance.getStartDate());
        workflowJson.put("endDate", wfInstance.getEndDate());
        workflowJson.put("status", wfInstance.getStatus());
        workflowJson.put("errorMessage", wfInstance.getErrorMessage());
        workflowJson.put("dag", dagJson);
        return workflowJson;
    }

    @Override
    /**
     * Validates the workflow dag.
     *
     * @param wfInstanceId  instance identifier
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean validateWorkflowDAG(String wfInstanceId) {
        // check and get workflow
        WfParameter wfParameter = checkWfInstanceId(wfInstanceId);
        try {
            WorkflowDAG dag = buildWorkflowDAG(wfInstanceId);
            return true; // No cycle detected
        } catch (CycleDetectedException ex) {
            // Log the cycle detection
            logger.error("Cycle detected in workflow: {}, error: {}", wfInstanceId, ex.getMessage());
            return false; // Cycle detected
        }
    }

    @Override
    /**
     * Builds the workflow dag.
     *
     * @param wfInstanceId  instance identifier
     * @return the result of the operation
     */
    public WorkflowDAG buildWorkflowDAG(String wfInstanceId) {
        // check and get workflow
        WfParameter workflow = getWfParamsByInstanceId(wfInstanceId);
        // Get Task list
        List<TaskParameter> tasks = taskParameterDao.getTaskParamsByWfInstanceId(wfInstanceId);
        // Get dag Builder
        WorkflowDAGBuilder builder = new WorkflowDAGBuilder();
        // Record taskId,taskInstanceId
        Map<String, String> taskIdToInstanceIdMap = new HashMap<>();
        // Add tasks to the DAG
        for (TaskParameter task : tasks) {
            // use task instance id
            builder.addTask(task.getTaskInstanceId(), task.getTaskName());
            taskIdToInstanceIdMap.put(task.getTaskId(), task.getTaskInstanceId());
        }
        // Add edges to the DAG
        for (TaskParameter task : tasks) {
            if (task.getPredecessors() != null) {
                String[] predecessors = task.getPredecessors().split(",");
                for (String predecessor : predecessors) {
                    predecessor = predecessor.trim();
                    if (!predecessor.isEmpty()) {
                        // check use task instance id
                        if (taskIdToInstanceIdMap.get(predecessor) == null || !builder.build().containsTask(taskIdToInstanceIdMap.get(predecessor))) {
                            throw new WorkflowValidationException("Task references non-existent predecessor: "
                                    + task.getTaskId() + " -> " + predecessor);
                        }
                        // use task instance id
                        builder.addEdge(taskIdToInstanceIdMap.get(predecessor), task.getTaskInstanceId());
                    }
                }
            }
        }
        // Check for cycles
        WorkflowDAG dag = builder.build();
        if (dag.hasCycle()) {
            throw new CycleDetectedException("Cycle detected in workflow DAG: " + wfInstanceId);
        }
        return dag;
    }

    /**
     * Saves the wf parameters.
     *
     * @param workflowId  workflowId identifier
     * @param wfInstanceId  instance identifier
     * @param workflow  workflow value
     * @param mergedParams  parameter map
     */
    public void saveWfParameters(String workflowId, String wfInstanceId,
                                 WfDefinition workflow, Map<String, Object> mergedParams) {
        WfParameter wfParameter = new WfParameter();
        wfParameter.setWfParamId(UniqueIdGenerator.generateId(UniqueIdGenerator.IdType.WF_PARAM));
        wfParameter.setWfInstanceId(wfInstanceId);
        wfParameter.setWorkflowId(workflowId);
        wfParameter.setWorkflowName(workflow.getWorkflowName());
        wfParameter.setEnableFlag(workflow.getEnableFlag());
        wfParameter.setPvalue(JsonUtils.toJson(mergedParams));
        wfParameter.setCreationDate(new Date());
        wfParameterDao.createWfParams(wfParameter);
        logger.debug("Saved merged parameters for workflow: {} with instance ID: {}", workflowId, wfInstanceId);
    }

    @Override
    /**
     * Returns the wf params by instance id.
     *
     * @param wfInstanceId  instance identifier
     * @return the requested wfparamsbyinstanceid object, or null if not found
     */
    public WfParameter getWfParamsByInstanceId(String wfInstanceId) {
        return wfParameterDao.getWfParams(wfInstanceId);
    }

    @Override
    /**
     * Returns the wf param by id.
     *
     * @param wfInstanceParamId  wfInstanceParamId identifier
     * @return the requested wfparambyid object, or null if not found
     */
    public WfParameter getWfParamById(String wfInstanceParamId) {
        return wfParameterDao.getWfParamById(wfInstanceParamId);
    }

    @Override
    /**
     * Creates the wf params.
     *
     * @param wfParameter  wfParameter value
     * @return the result as a String
     */
    public String createWfParams(WfParameter wfParameter) {
        return wfParameterDao.createWfParams(wfParameter);
    }

    @Override
    /**
     * Returns the task params by instance id.
     *
     * @param taskInstanceId  instance identifier
     * @return the requested taskparamsbyinstanceid object, or null if not found
     */
    public TaskParameter getTaskParamsByInstanceId(String taskInstanceId) {
        return taskParameterDao.getTaskParams(taskInstanceId);
    }

    @Override
    /**
     * Returns the task params by wf instance id.
     *
     * @param wfInstanceId  instance identifier
     * @return a list of matching records
     */
    public List<TaskParameter> getTaskParamsByWfInstanceId(String wfInstanceId) {
        return taskParameterDao.getTaskParamsByWfInstanceId(wfInstanceId);
    }

    public List<TaskParameter> getTaskParamsByWfInstanceIdAndTaskIds(String wfInstanceId, List<String> taskIds) {
        return taskParameterDao.getTaskParamsByWfInstanceIdAndTaskIds(wfInstanceId, taskIds);
    }

    @Override
    /**
     * Creates the task params.
     *
     * @param taskParameter  taskParameter value
     * @return the result as a String
     */
    public String createTaskParams(TaskParameter taskParameter) {
        return taskParameterDao.createTaskParams(taskParameter);
    }


    /**
     * Saves the task parameters.
     *
     * @param workflowId  workflowId identifier
     * @param wfInstanceId  instance identifier
     * @param taskInstance  taskInstance value
     * @param taskParam  taskParam value
     * @param mergedTaskParams  parameter map
     */
    public void saveTaskParameters(String workflowId, String wfInstanceId,
                                   TaskInstance taskInstance, TaskDefinition taskParam,
                                   Map<String, Object> mergedTaskParams) {
        TaskParameter taskParameter = new TaskParameter();
        taskParameter.setTaskParamId(UniqueIdGenerator.generateId(UniqueIdGenerator.IdType.TASK_PARAM));
        taskParameter.setTaskInstanceId(taskInstance.getTaskInstanceId());
        taskParameter.setWfInstanceId(wfInstanceId);
        taskParameter.setWorkflowId(workflowId);
        taskParameter.setTaskId(taskParam.getTaskId());
        taskParameter.setTaskName(taskParam.getTaskName());
        taskParameter.setTaskType(taskParam.getTaskType());
        taskParameter.setEnableFlag(taskParam.getEnableFlag());
        taskParameter.setPredecessors(taskParam.getPredecessors());
        taskParameter.setPvalue(JsonUtils.toJson(mergedTaskParams));
        taskParameter.setCreationDate(new Date());
        taskParameterDao.createTaskParams(taskParameter);
        logger.debug("Saved merged parameters for task: {} with instance ID: {}",
                taskParam.getTaskId(), taskInstance.getTaskInstanceId());
    }

    /**
     * Saves the task parameters.
     *
     * @param taskInstanceId  instance identifier
     * @param params  parameter map
     */
    public void saveTaskParameters(String taskInstanceId, Map<String, Object> params) {
        taskParameterDao.updatePvalueByInstanceId(taskInstanceId, JsonUtils.toJson(params));
        logger.debug("Save Task Parameters for task instance ID: {}", taskInstanceId);
    }

    /**
     * Merges workflow-definition parameters with global defaults and copies
     * the result into the workflow instance parameter record.
     * Delegates to {@link #mergeAndCopyParams(String, String, String)} with a null
     * parent instance ID.
     *
     * @param workflowId   the workflow definition ID
     * @param wfInstanceId the target workflow instance ID
     * @return the merged parameter map written to the instance record
     */
    public Map<String, Object> mergeAndCopyParams(String workflowId, String wfInstanceId) {
        return this.mergeAndCopyParams(workflowId, wfInstanceId, null);
    }

    /**
     * Merges workflow-definition parameters with global defaults, overlays the
     * provided {@code params} map, and persists the result to the workflow instance
     * parameter record. This is the canonical entry point for the multi-level
     * parameter merge logic.
     *
     * @param workflowId   the workflow definition ID providing defaults
     * @param wfInstanceId the target workflow instance whose parameter record is updated
     * @param params       caller-supplied overrides applied on top of the merged defaults;
     *                     may be {@code null}, in which case no overrides are applied
     * @return the final merged parameter map written to the instance record
     */
    public Map<String, Object> mergeAndCopyParams(String workflowId, String wfInstanceId, Map<String, Object> params) {
        logger.info("Copying and merging parameters for workflow: {} to instance: {}", workflowId, wfInstanceId);
        try {
            WfDefinition workflow = wfDefinitionDao.getWorkflow(workflowId);
            // Step 1: Get global parameters
            Map<String, Object> globalParams = getWfGlobalParamsAsMap();
            logger.debug("Global parameters: {}", globalParams);

            // Step 2: Get workflow parameters and merge with global parameters
            Map<String, Object> workflowParams = wfDefinitionDao.getWorkflowParamsAsMap(workflowId);

            // Merge dynamic parameters to global parameters
            Map<String, Object> mergedWorkflowParams;
            if (params != null) {
                mergedWorkflowParams = new HashMap<>(params);
                mergedWorkflowParams.putAll(globalParams);
            } else {
                mergedWorkflowParams = new HashMap<>(globalParams);
            }
            // Merge global parameters into workflow parameters (workflow params take precedence)
            mergedWorkflowParams.putAll(workflowParams);
            logger.debug("Merged workflow parameters: {}", mergedWorkflowParams);

            // Step 3: Save merged workflow parameters to workflow instance parameters
            WfParameter wfParameter = new WfParameter();
            wfParameter.setWfParamId(UniqueIdGenerator.generateId(UniqueIdGenerator.IdType.WF_PARAM));
            wfParameter.setWfInstanceId(wfInstanceId);
            wfParameter.setWorkflowId(workflowId);
            wfParameter.setEnableFlag(workflow.getEnableFlag());
            wfParameter.setPvalue(JsonUtils.toJson(mergedWorkflowParams));
            wfParameter.setCreationDate(new Date());
            createWfParams(wfParameter);
            logger.debug("Saved merged parameters for workflow: {} with instance ID: {}", wfParameter.getWorkflowId(), wfParameter.getWfParamId());

            // Step 4: Get all tasks for the workflow
            List<TaskDefinition> taskParams = taskDefinitionDao.getTasksByWorkflowId(workflowId);

            // Step 5: For each task, get task parameters, merge with workflow parameters, and save
            for (TaskDefinition taskParam : taskParams) {
                Map<String, Object> pValue = JsonUtils.jsonToMap(taskParam.getPvalue());
                Map<String, Object> mergedTaskParams = new HashMap<>(mergedWorkflowParams);
                if (pValue != null) {
                    mergedTaskParams.putAll(pValue);
                }
                // Save merged task parameters to task params
                TaskParameter taskParameter = new TaskParameter();
                taskParameter.setTaskParamId(UniqueIdGenerator.generateId(UniqueIdGenerator.IdType.TASK_PARAM));
                taskParameter.setWfInstanceId(wfInstanceId);
                taskParameter.setWorkflowId(workflowId);
                taskParameter.setEnableFlag(taskParam.getEnableFlag());
                taskParameter.setPvalue(JsonUtils.toJson(mergedTaskParams));
                taskParameter.setCreationDate(new Date());
                createTaskParams(taskParameter);
                logger.debug("Saved merged parameters for task: {} with instance ID: {}", taskParameter.getTaskId(), taskParameter.getTaskParamId());
            }
            return mergedWorkflowParams;

        } catch (Exception e) {
            logger.error("Error copying and merging parameters for workflow: {}", workflowId, e);
            throw new RuntimeException("Failed to copy and merge parameters: " + e.getMessage(), e);
        }
    }
}