/*
 * Description: Entity tracking runtime state of one task execution
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.model.entity;

import java.util.Date;
/**
 * Entity representing the runtime state of one task execution within a
 * workflow instance.
 *
 * <p>Created when a workflow instance starts; updated as the task transitions
 * through {@code WAITING → RUNNING → COMPLETED/ERROR/TIMEOUT/CANCELED/SKIPPED}.
 */
public class TaskInstance {
    private String taskInstanceId;
    private String wfInstanceId;
    private String workflowId;
    private String taskName;
    private String taskId;
    private Date startDate;
    private Date endDate;
    private String errorFlag;
    private String status;
    private String errorMessage;

    // Getters and setters
    /**
     * Returns the task instance id.
     *
     * @return the result as a String
     */
    public String getTaskInstanceId() {
        return taskInstanceId;
    }

    /**
     * Sets the task instance id.
     *
     * @param taskInstanceId  instance identifier
     */
    public void setTaskInstanceId(String taskInstanceId) {
        this.taskInstanceId = taskInstanceId;
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
     * Sets the wf instance id.
     *
     * @param wfInstanceId  instance identifier
     */
    public void setWfInstanceId(String wfInstanceId) {
        this.wfInstanceId = wfInstanceId;
    }

    /**
     * Returns the workflow id.
     *
     * @return the result as a String
     */
    public String getWorkflowId() {
        return workflowId;
    }

    /**
     * Sets the workflow id.
     *
     * @param workflowId  workflowId identifier
     */
    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    /** Returns the display name of the task definition. */
    public String getTaskName() {return taskName;}

    public void setTaskName(String taskName) {this.taskName = taskName;}

    public String getTaskId() {
        return taskId;
    }

    /**
     * Sets the task id.
     *
     * @param taskId  identifier
     */
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    /**
     * Returns the start date.
     *
     * @return the requested startdate object, or null if not found
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * Sets the start date.
     *
     * @param startDate  date/time value
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /**
     * Returns the end date.
     *
     * @return the requested enddate object, or null if not found
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * Sets the end date.
     *
     * @param endDate  date/time value
     */
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    /**
     * Returns the error flag.
     *
     * @return the result as a String
     */
    public String getErrorFlag() {
        return errorFlag;
    }

    /**
     * Sets the error flag.
     *
     * @param errorFlag  flag value (Y or N)
     */
    public void setErrorFlag(String errorFlag) {
        this.errorFlag = errorFlag;
    }

    /**
     * Returns the status.
     *
     * @return the result as a String
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the status.
     *
     * @param status  status value
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Returns the error message.
     *
     * @return the result as a String
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets the error message.
     *
     * @param errorMessage  descriptive message
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    /**
     * To the string.
     *
     * @return the result as a String
     */
    public String toString() {
        return "TaskInstance{" +
                "taskInstanceId='" + taskInstanceId + '\'' +
                ", wfInstanceId='" + wfInstanceId + '\'' +
                ", workflowId='" + workflowId + '\'' +
                ", taskId='" + taskId + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", errorFlag='" + errorFlag + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
