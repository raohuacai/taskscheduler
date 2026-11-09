/*
 * Description: Sample custom task class demonstrating how to implement a new task type
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.custom;

import com.ts.common.constants.Constants;
import com.ts.common.utils.ParamUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
/**
 * Demo/sample task implementation illustrating how to create a custom task type.
 *
 * <p>Not used in production; included as a reference for developers writing
 * new task classes. The class name is registered in the task-type table to make
 * it available in the workflow editor.
 */
public class Demo {

    private static final Logger logger = LogManager.getLogger(Demo.class);

    // No-arg constructor required for instantiation via reflection
    /**
     * Constructs a new Demo instance.
     */
    public Demo() {
    }

    /**
     * Executes.
     *
     * @param parameters  parameter map
     * @return the result as a String
     * @throws InterruptedException  if an error occurs
     */
    public String execute(Map<String, Object> parameters) throws InterruptedException {
        // get parameters here
        String workflowId = ParamUtils.getParameter(parameters, Constants.PARAM_WORKFLOW_ID);
        String wfName = ParamUtils.getParameter(parameters, Constants.PARAM_WORKFLOW_NAME);
        String wfInstanceId = ParamUtils.getParameter(parameters, Constants.PARAM_WORKFLOW_INSTANCE);
        String taskId = ParamUtils.getParameter(parameters, Constants.PARAM_TASK_ID);
        String taskName = ParamUtils.getParameter(parameters, Constants.PARAM_TASK_NAME);
        String taskInstanceId = ParamUtils.getParameter(parameters, Constants.PARAM_TASK_INSTANCE);
        String className = ParamUtils.getParameter(parameters, Constants.PARAM_TASK_CLASSNAME);
        String methodName = ParamUtils.getParameter(parameters, Constants.PARAM_TASK_METHODNAME);
        String timeout = ParamUtils.getParameter(parameters, Constants.PARAM_TASK_TIMEOUT);
        logger.debug("Workflow Information: {}, {}, {}", workflowId, wfName, wfInstanceId);
        logger.info("Task Information: {}, {}, {}, {}, {} {}", taskId, taskName, taskInstanceId, className, methodName, timeout);

        try {
            // !!! core logic here, sleep is just demo !!!
            Thread.sleep(5000);
            // !!! core logic finished !!!
        } catch (InterruptedException e) {
            // it is interrupted by timeoutWatcher (TimeoutTask)
            logger.debug("Task was interrupted due to timeout or cancel: {}, {}", taskId, taskInstanceId);
            throw e;
        }

        // return object
        return "result";
    }
}
