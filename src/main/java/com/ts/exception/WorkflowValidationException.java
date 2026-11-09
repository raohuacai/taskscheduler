/*
 * Description: Thrown when workflow definition validation fails
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.exception;
/**
 * Thrown when workflow definition validation fails (e.g., invalid cron expression,
 * missing required fields, DAG cycle detected).
 */
public class WorkflowValidationException extends WorkflowException {
    /**
     * Constructs a new WorkflowValidationException instance.
     *
     * @param message  descriptive message
     */
    public WorkflowValidationException(String message) {
        super(message);
    }
}