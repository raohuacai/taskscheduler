/*
 * Description: Reflective TaskFactory that loads task classes by name and injects the WorkflowEngine
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.task;

import com.ts.common.constants.Constants;
import com.ts.core.engine.WorkflowEngine;
import com.ts.task.core.WorkflowTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentMap;
/**
 * Default {@link TaskFactory} that instantiates tasks by reflectively loading
 * the class registered for the given task-type name.
 *
 * <p>After construction, injects the {@link com.ts.core.engine.WorkflowEngine}
 * into the new instance (if it is an {@link AbstractTask}) so the task can
 * persist parameter updates via {@link com.ts.core.engine.WorkflowEngine#updateTaskParams}.
 *
 * <p>Task-type → class mappings are maintained in the {@link com.ts.service.TaskTypeService}
 * and cached in memory. Unknown type names result in a
 * {@link com.ts.exception.TaskTypeNotFoundException}.
 */
public class DefaultTaskFactory implements TaskFactory {
    private static final Logger logger = LogManager.getLogger(DefaultTaskFactory.class);
    private final ConcurrentMap<String, Class<? extends Task>> taskTypes;

    /**
     * Constructs a new DefaultTaskFactory instance.
     *
     * @param taskTypes  type discriminator
     */
    public DefaultTaskFactory(ConcurrentMap<String, Class<? extends Task>> taskTypes) {
        this.taskTypes = taskTypes;
    }

    @Override
    /**
     * Creates the task.
     *
     * @param taskType  type discriminator
     * @param engine  engine instance
     * @return the result of the operation
     */
    public Task createTask(String taskType, WorkflowEngine engine) {
        Task task;
        if (Constants.TYPE_GROUP_WORKFLOW.equals(taskType)) {
            task = new WorkflowTask(engine);
        } else {
            // other type Tasks
            Class<? extends Task> taskClass = taskTypes.get(taskType);
            if (taskClass == null) {
                logger.error("Unknown task type: {}", taskType);
                return null;
            }
            try {
                task = taskClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                logger.error("Error creating task instance for type: " + taskType, e);
                return null;
            }
        }
        // Inject workflowEngine into all tasks so saveParam/saveParams can persist to DB.
        // WorkflowTask already has it set via constructor; re-assigning here is harmless.
        if (task instanceof AbstractTask) {
            ((AbstractTask) task).workflowEngine = engine;
        }
        return task;
    }

    /**
     * Registers the task type.
     *
     * @param typeName  typeName name
     * @param className  className name
     */
    public synchronized void registerTaskType(String typeName, String className) {
        try {
            // Load the class dynamically
            @SuppressWarnings("unchecked") Class<? extends Task> taskClass = (Class<? extends Task>) Class.forName(className);

            // Register the task type
            taskTypes.put(typeName, taskClass);
            logger.info("Registered task type: {} -> {}", typeName, className);
        } catch (ClassNotFoundException e) {
            logger.error("Class not found for task type {}: {}", typeName, className, e);
        } catch (ClassCastException e) {
            logger.error("Class {} does not implement Task interface", className, e);
        } catch (Exception e) {
            logger.error("Error registering task type {}", typeName, e);
        }
    }

    /**
     * Unregister the task type.
     *
     * @param typeName  typeName name
     */
    public synchronized void unregisterTaskType(String typeName) {
        taskTypes.remove(typeName);
        logger.info("Unregistered task type: {}", typeName);
    }
}
