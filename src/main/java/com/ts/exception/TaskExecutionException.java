/*
 * Description: Thrown on non-timeout, non-cancellation task execution failure
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.exception;
/**
 * Thrown when a task fails during execution with a non-timeout, non-cancellation error.
 *
 * <p>Carries the originating task ID and task instance ID to support
 * targeted error handling in {@link com.ts.core.engine.WorkflowInstance}.
 */
public class TaskExecutionException extends WorkflowException {
    private final String taskId;
    private final String taskInstanceId;

    /**
     * Constructs a new TaskExecutionException instance.
     *
     * @param message  descriptive message
     * @param taskId  identifier
     * @param taskInstanceId  instance identifier
     */
    public TaskExecutionException(String message, String taskId, String taskInstanceId) {
        super(message);
        this.taskId = taskId;
        this.taskInstanceId = taskInstanceId;
    }

    /**
     * Constructs a new TaskExecutionException instance.
     *
     * @param message  descriptive message
     * @param cause  cause value
     * @param taskId  identifier
     * @param taskInstanceId  instance identifier
     */
    public TaskExecutionException(String message, Throwable cause, String taskId, String taskInstanceId) {
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