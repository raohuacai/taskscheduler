/*
 * Description: Thrown when a resource cap (e.g. max workflows) is exceeded
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.exception;
/**
 * Thrown when a resource cap (e.g., {@code MAX_RUNNING_WORKFLOWS}) is exceeded.
 */
public class ResourceLimitException extends WorkflowException {
    /**
     * Constructs a new ResourceLimitException instance.
     *
     * @param message  descriptive message
     */
    public ResourceLimitException(String message) {
        super(message);
    }
}