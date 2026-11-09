/*
 * Description: Base exception for workflow-level errors
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.exception;
/**
 * Base exception for workflow-level errors that are not covered by more
 * specific subtypes.
 */
public class WorkflowException extends RuntimeException {
    /**
     * Constructs a new WorkflowException instance.
     *
     * @param message  descriptive message
     */
    public WorkflowException(String message) {
        super(message);
    }

    /**
     * Constructs a new WorkflowException instance.
     *
     * @param message  descriptive message
     * @param cause  cause value
     */
    public WorkflowException(String message, Throwable cause) {
        super(message, cause);
    }
}