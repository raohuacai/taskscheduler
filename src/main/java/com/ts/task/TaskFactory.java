/*
 * Description: Contract for instantiating Task objects by task-type name
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.task;

import com.ts.core.engine.WorkflowEngine;
/**
 * Contract for factories that instantiate {@link Task} objects by task-type name.
 *
 * <p>Implementations resolve the fully-qualified class name from the task-type
 * registry and return a configured, injection-ready {@code Task} instance.
 *
 * @see com.ts.task.DefaultTaskFactory
 */
public interface TaskFactory {

    Task createTask(String taskType, WorkflowEngine engine);

    void registerTaskType(String typeName, String className);

    void unregisterTaskType(String typeName);
}
