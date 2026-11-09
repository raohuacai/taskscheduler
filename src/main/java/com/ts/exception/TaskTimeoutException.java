/*
 * Description: Thrown when a task exceeds its configured execution timeout
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.exception;
/**
 * Thrown when a task exceeds its configured execution timeout.
 *
 * <p>Caught by {@link com.ts.core.engine.WorkflowInstance} to transition the
 * task instance to {@code TIMEOUT} status.
 */
public class TaskTimeoutException extends WorkflowException {
    private final String taskId;
    private final String taskInstanceId;

    /**
     * Constructs a new TaskTimeoutException instance.
     *
     * @param message  descriptive message
     * @param taskId  identifier
     * @param taskInstanceId  instance identifier
     */
    public TaskTimeoutException(String message, String taskId, String taskInstanceId) {
        super(message);
        this.taskId = taskId;
        this.taskInstanceId = taskInstanceId;
    }

    /**
     * Constructs a new TaskTimeoutException instance.
     *
     * @param message  descriptive message
     * @param cause  cause value
     * @param taskId  identifier
     * @param taskInstanceId  instance identifier
     */
    public TaskTimeoutException(String message, Throwable cause, String taskId, String taskInstanceId) {
        super(message, cause);
        this.taskId = taskId;
        this.taskInstanceId = taskInstanceId;
    }

    /**
     * Returns the task id.
     *
     * @return the result as a String
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * Returns the task instance id.
     *
     * @return the result as a String
     */
    public String getTaskInstanceId() {
        return taskInstanceId;
    }
}
