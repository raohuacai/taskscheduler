/*
 * Description: Entity tracking runtime state of one workflow execution
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.model.entity;

import java.util.Date;
/**
 * Entity representing one runtime execution of a workflow definition.
 *
 * <p>Tracks status, start/end times, error flags, parent/top-parent instance
 * relationships (for sub-workflow hierarchies), and the trigger parameters.
 */
public class WfInstance {
    private String wfInstanceId;
    private String workflowId;
    private String workflowName;
    private Date startDate;
    private Date endDate;
    private String errorFlag;
    private String status;
    private String errorMessage;
    private String parentWfInstanceId;
   private String topParentInstanceId;
    private String taskErrorFlag;

    // Getters and setters
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

    /** Returns the workflow definition name for this instance. */
    public String getWorkflowName() {return workflowName;}

    public void setWorkflowName(String workflowName) {this.workflowName = workflowName;}

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

    /**
     * Returns the parent wf instance id.
     *
     * @return the result as a String
     */
    public String getParentWfInstanceId() {
        return parentWfInstanceId;
    }

    /**
     * Sets the parent wf instance id.
     *
     * @param parentWfInstanceId  instance identifier
     */
    public void setParentWfInstanceId(String parentWfInstanceId) {
        this.parentWfInstanceId = parentWfInstanceId;
    }

    /** Returns the root ancestor workflow instance ID in a nested sub-workflow hierarchy. */
    public String getTopParentInstanceId() {return topParentInstanceId;}

    public void setTopParentInstanceId(String topParentInstanceId) {this.topParentInstanceId = topParentInstanceId;}

    public String getTaskErrorFlag() {return taskErrorFlag;}

    public void setTaskErrorFlag(String taskErrorFlag) {this.taskErrorFlag = taskErrorFlag;}

    @Override
    public String toString() {
        return "WfInstance{" +
                "wfInstanceId='" + wfInstanceId + '\'' +
                ", workflowId='" + workflowId + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", errorFlag='" + errorFlag + '\'' +
                ", status='" + status + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", parentWfInstanceId='" + parentWfInstanceId + '\'' +
                ", topParentInstanceId='" + topParentInstanceId + '\'' +
                '}';
    }
}