/*
 * Description: Thrown when a task definition or instance is not found
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.exception;
/**
 * Thrown when a task definition or instance cannot be found by the requested ID.
 */
public class TaskNotFoundException extends WorkflowException {
    /**
     * Constructs a new TaskNotFoundException instance.
     *
     * @param message  descriptive message
     */
    public TaskNotFoundException(String message) {
        super(message);
    }
}