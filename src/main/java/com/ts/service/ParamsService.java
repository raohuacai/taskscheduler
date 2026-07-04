/*
 * Description: Contract for the multi-level parameter inheritance system
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service;

import com.ts.config.EmailTemplateConfig;
import com.ts.config.HttpServerConfig;
import com.ts.config.ThreadPoolConfig;
import com.ts.config.MailConfig;
import com.ts.core.dag.WorkflowDAG;
import com.ts.model.entity.*;

import java.util.List;
import java.util.Map;
/**
 * Contract for the multi-level parameter inheritance system.
 *
 * <p>The TS engine resolves parameters in the following priority order (highest
 * first): task-instance overrides → task-definition defaults → workflow-instance
 * overrides → workflow-definition defaults → global parameters. Implementations
 * build a merged map for task execution and handle DAG-structured parameter
 * propagation between workflow levels.
 */
public interface ParamsService {
    // Global parameters
    List<GlobalParams> getAllGlobalParams();

    GlobalParams getWfGlobalParams();

    Map<String, Object> getWfGlobalParamsAsMap();

    boolean updateWfGlobalParams(Map<String, Object> params);

    // Thread pool configuration methods
    ThreadPoolConfig getThreadPoolConfig();
    boolean updateThreadPoolConfig(Map<String, Object> params);

    // Mail configuration methods
    MailConfig getMailConfig();
    boolean updateMailConfig(Map<String, Object> params);
    // Mail template methods
    EmailTemplateConfig getEmailTemplateConfig();
    boolean updateEmailTemplateConfig(Map<String, Object> params);

    // Http server config
    HttpServerConfig getHttpServerConfig();
    boolean updateHttpServerConfig(Map<String, Object> params);

    // Workflow parameters
    WfParameter getWfParamsByInstanceId(String wfInstanceId);

    WfParameter getWfParamById(String wfInstanceParamId);

    Map<String, Object> getWfParamsByIdAsMap(String wfInstanceParamId);

    String createWfParams(WfParameter wfParameter);

    WfParameter checkWfInstanceId(String wfInstanceId);

    Map<String, Object> getWorkflowDAG(String workflowId);

    boolean validateWorkflowDAG(String workflowId);

    WorkflowDAG buildWorkflowDAG(String wfInstanceId);

    void saveWfParameters(String workflowId, String wfInstanceId, WfDefinition workflow, Map<String, Object> mergedParams);

    //Task parameters
    TaskParameter getTaskParamsByInstanceId(String taskInstanceId);

    List<TaskParameter> getTaskParamsByWfInstanceId(String wfInstanceId);

    List<TaskParameter> getTaskParamsByWfInstanceIdAndTaskIds(String wfInstanceId, List<String> taskIds);


    String createTaskParams(TaskParameter taskParameter);

    void saveTaskParameters(String workflowId, String wfInstanceId, TaskInstance taskInstance, TaskDefinition taskParam, Map<String, Object> mergedTaskParams);
    void saveTaskParameters(String taskInstanceId,Map<String, Object> params);
                            // copy workflow and tasks to parameter table
    Map<String, Object> mergeAndCopyParams(String workflowId, String wfInstanceId, Map<String, Object> params);
}