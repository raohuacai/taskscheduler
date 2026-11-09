/*
 * Description: Task type dynamically loading and executing a Java class with allowlist-based security
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.task.timeout;

import com.ts.common.constants.Constants;
import com.ts.common.utils.ParamUtils;
import com.ts.exception.TaskExecutionException;
import com.ts.service.impl.TaskLogService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
/**
 * Task type that dynamically loads and executes a Java class at runtime.
 *
 * <p>The target class name is specified via the {@code javaClassName} parameter.
 * Security: only classes whose package prefix appears in the {@code allowedPackages}
 * configuration list may be loaded, preventing arbitrary code execution. The class
 * must have a no-arg constructor and a {@code execute(Map)} method.
 *
 * <p>Inherits from {@link TimeoutTask} for interrupt-based timeout control.
 */
public class JavaTask extends TimeoutTask {
    private static final Logger logger = LogManager.getLogger(JavaTask.class);
    // Security controls and configuration parameters for allowed class loading
    private static final String DEFAULT_ALLOWED_PACKAGES = "com.ts.custom";

    private final Set<String> allowedPackages;
    private Object instance;
    private Method method;

    /**
     * Constructs a new JavaTask instance.
     */
    public JavaTask() {
        // Read the list of allowed package prefixes from configuration
        String allowedPackagesStr = DEFAULT_ALLOWED_PACKAGES;
        this.allowedPackages = Arrays.stream(allowedPackagesStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        logger.debug("ExecuteJavaTask initialized with allowed packages: {}", allowedPackages);
    }

    @Override
    /**
      * Prepares resources and reads task parameters before the main action.
      * Called by {@link AbstractTask#execute} before {@link #action}.
      *
      * @param parameters  merged task parameter map supplied by the workflow engine
      * @throws Exception  if a required parameter is missing or resource initialisation fails
      */
    public void preAction(Map<String, Object> parameters) throws Exception {
        super.preAction(parameters);

        String className = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TASK_CLASSNAME, taskId, taskInstanceId);
        String methodName = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TASK_METHODNAME, taskId, taskInstanceId);

        // Security check: verify the class belongs to an allowed package
        if (!isClassAllowed(className)) {
            throw new TaskExecutionException("Security violation: Class is not in allowed packages: " + className, null, taskId, taskInstanceId);
        }

        try {
            // Detailed error handling for class loading failures
            Class<?> clazz = loadClass(className);

            // Check that the class has a no-arg constructor
            if (!hasDefaultConstructor(clazz)) {
                throw new TaskExecutionException("Class does not have a default constructor: " + className, null, taskId, taskInstanceId);
            }

            // Instantiate the class via reflection
            instance = clazz.getDeclaredConstructor().newInstance();

            // Locate the target method via reflection
            try {
                method = clazz.getMethod(methodName, Map.class);
            } catch (NoSuchMethodException e) {
                throw new TaskExecutionException("Method not found: " + className + "." + methodName + "(Map<String, Object>)", e, taskId, taskInstanceId);
            }

            // Verify that the method has the expected modifiers (public)
            if (!Modifier.isPublic(method.getModifiers())) {
                throw new TaskExecutionException("Method is not public: " + className + "." + methodName, null, taskId, taskInstanceId);
            }
        } catch (TaskExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new TaskExecutionException("Error initializing Java task: " + e.getMessage(), e, taskId, taskInstanceId);
        }
    }

    @Override
    /**
      * Executes the core task logic.
      * Called by {@link AbstractTask#execute} after {@link #preAction}.
      *
      * @param parameters  merged task parameter map
      * @throws Exception  if the task logic fails
      */
    public void action(Map<String, Object> parameters) throws Exception {
        if (instance == null || method == null) {
            throw new TaskExecutionException("Task not properly initialized. Make sure preAction completed successfully.", null, taskId, taskInstanceId);
        }
        try {
            // Use detailed exception handling for better diagnostics
            Object result = method.invoke(instance, parameters);
            // Store the method return value back into the parameters map
            if (result != null) {
                saveParam(parameters, Constants.PARAM_TASK_OUTPUT, result);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Task output: " + result);
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            logger.error("Error in executed method: {}: {}, {}, {}", cause != null ? cause.getMessage() : "unknown", taskId, taskName, taskInstanceId);
            if (cause instanceof InterruptedException) {
                //throw new TaskExecutionException("Error in executed method: " + cause.getMessage(), (Exception) cause, taskId, taskInstanceId);
                throw (InterruptedException) cause;
            } else {
                throw new TaskExecutionException("Error in executed method", e, taskId, taskInstanceId);
            }
        } catch (Exception e) {
            throw new TaskExecutionException("Error executing Java method: " + e.getMessage(), e, taskId, taskInstanceId);
        }
    }

    // Security check: verify the class is in the allowed-packages whitelist
    private boolean isClassAllowed(String className) {
        //return allowedPackages.stream().anyMatch(className::startsWith);
        return allowedPackages.stream().anyMatch(pkg ->
                className.startsWith(pkg + ".") || className.equals(pkg));
    }

    // Load the class with detailed exception handling
    private Class<?> loadClass(String className) throws TaskExecutionException {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new TaskExecutionException("Class not found: " + className, e, taskId, taskInstanceId);
        }
    }

    // Verify the class has a no-arg constructor accessible via reflection
    private boolean hasDefaultConstructor(Class<?> clazz) {
        try {
            clazz.getDeclaredConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}