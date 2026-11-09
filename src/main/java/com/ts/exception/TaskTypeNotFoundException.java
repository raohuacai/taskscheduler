/*
 * Description: Thrown when no class is registered for the requested task type name
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.exception;
/**
 * Thrown when {@link com.ts.task.DefaultTaskFactory} cannot find a class
 * registered for the requested task type name.
 */
public class TaskTypeNotFoundException extends WorkflowException {
    /**
     * Constructs a new TaskTypeNotFoundException instance.
     *
     * @param message  descriptive message
     */
    public TaskTypeNotFoundException(String message) {
        super(message);
    }
}
