/*
 * Description: Thrown when task pre-execution validation fails (missing or invalid parameters)
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.exception;
/**
 * Thrown during pre-execution validation when a task's required parameters
 * are missing or malformed.
 */
public class TaskValidationException extends WorkflowException {
    /**
     * Constructs a new TaskValidationException instance.
     *
     * @param message  descriptive message
     */
    public TaskValidationException(String message) {
        super(message);
    }
}