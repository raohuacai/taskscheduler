/*
 * Description: Immutable result object returned by startWorkflow(), carrying success flag and instance ID or error message
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.core.engine;
/**
 * Immutable result object returned by {@link com.ts.service.ExecutionService#startWorkflow}.
 *
 * <p>Carries a success/failure flag and either the newly created workflow instance ID
 * (on success) or an error message (on failure). Use the static factory methods
 * {@link #success(String)} and {@link #failure(String)} to construct instances.
 */
public class WorkflowResult {
    private final String wfInstanceId;
    private final String errorMessage;
    private final boolean success;

    // Constructor for a successful result
    /**
     * Constructs a new WorkflowResult instance.
     *
     * @param wfInstanceId  instance identifier
     */
    private WorkflowResult(String wfInstanceId) {
        this.wfInstanceId = wfInstanceId;
        this.errorMessage = "";
        this.success = true;
    }

    // Constructor for a failed result
    /**
     * Constructs a new WorkflowResult instance.
     *
     * @param errorMessage  descriptive message
     * @param success  success value
     */
    private WorkflowResult(String errorMessage, boolean success) {
        this.wfInstanceId = "";
        this.errorMessage = errorMessage;
        this.success = success;
    }

    /**
     * Success.
     *
     * @param wfInstanceId  instance identifier
     * @return the result of the operation
     */
    public static WorkflowResult success(String wfInstanceId) {
        return new WorkflowResult(wfInstanceId);
    }

    /**
     * Failure.
     *
     * @param errorMessage  descriptive message
     * @return the result of the operation
     */
    public static WorkflowResult failure(String errorMessage) {
        return new WorkflowResult(errorMessage, false);
    }

    /**
     * Checks whether the success.
     *
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the wf instance id.
     *
     * @return the result as a String
     */
    public String getWfInstanceId() {
        return wfInstanceId;
    }

    /**
     * Returns the error message.
     *
     * @return the result as a String
     */
    public String getErrorMessage() {
        return errorMessage;
    }
}
