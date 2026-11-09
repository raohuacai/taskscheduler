/*
 * Description: View object aggregating WfInstance with nested TaskInstanceVO list
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.model.vo;

import java.util.Date;
import java.util.List;

/**
 * View object that aggregates a {@link com.ts.model.entity.WfInstance} with its
 * nested {@link TaskInstanceVO} list for display in the monitoring UI and
 * history views.
 */
public class WorkflowInstanceVO {
    private String parentInstanceId;
    private String topParentInstanceId;
    private String instanceId;
    private String workflowId;
    private String workflowName;
    private Date startDate;
    private Date endDate;
    private String status;
    private List<TaskInstanceVO> tasks;

    // Calculate duration in minutes
    /**
     * Returns the duration minutes.
     *
     * @return the numeric result
     */
    public long getDurationMinutes() {
        if (startDate == null) {
            return 0;
        }

        Date end = endDate != null ? endDate : new Date();
        long durationMs = end.getTime() - startDate.getTime();
        return durationMs / (60 * 1000);
    }

    // Format duration as a string (e.g., "1h 30m")
    /**
     * Returns the duration formatted.
     *
     * @return the result as a String
     */
    public String getDurationFormatted() {
        long minutes = getDurationMinutes();
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;

        if (hours > 0) {
            return hours + "h " + remainingMinutes + "m";
        } else {
            return minutes + "m";
        }
    }

    // Getters and setters
    /**
     * Returns the parent instance id.
     *
     * @return the result as a String
     */
    public String getParentInstanceId() {
        return parentInstanceId;
    }

    /**
     * Sets the parent instance id.
     *
     * @param parentInstanceId  instance identifier
     */
    public void setParentInstanceId(String parentInstanceId) {
        this.parentInstanceId = parentInstanceId;
    }

    /**
     * Returns the top parent instance id.
     *
     * @param getInstanceId(  instance identifier
     * @return the result as a String
     */
    public String getTopParentInstanceId() {return topParentInstanceId;}

    public void setTopParentInstanceId(String topParentInstanceId) {this.topParentInstanceId = topParentInstanceId;}

    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Sets the instance id.
     *
     * @param instanceId  instance identifier
     */
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
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

    /**
     * Returns the workflow name.
     *
     * @return the result as a String
     */
    public String getWorkflowName() {
        return workflowName;
    }

    /**
     * Sets the workflow name.
     *
     * @param workflowName  workflowName name
     */
    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
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
     * Returns the tasks.
     *
     * @return a list of matching records
     */
    public List<TaskInstanceVO> getTasks() {
        return tasks;
    }

    /**
     * Sets the tasks.
     *
     * @param tasks  tasks value
     */
    public void setTasks(List<TaskInstanceVO> tasks) {
        this.tasks = tasks;
    }
}