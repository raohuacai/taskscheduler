/*
 * Description: Thrown when a workflow definition or instance is not found
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.exception;
/**
 * Thrown when a workflow definition or instance cannot be found by the requested ID.
 */
public class WorkflowNotFoundException extends WorkflowException {
    /**
     * Constructs a new WorkflowNotFoundException instance.
     *
     * @param message  descriptive message
     */
    public WorkflowNotFoundException(String message) {
        super(message);
    }
}
