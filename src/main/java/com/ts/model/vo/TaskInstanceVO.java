/*
 * Description: View object flattening TaskInstance data for UI display
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.model.vo;

import java.util.Date;

/**
 * View object that flattens {@link com.ts.model.entity.TaskInstance} data
 * for display in the monitoring UI and history views.
 *
 * <p>Includes computed display fields such as formatted duration and
 * human-readable status labels that are not present on the raw entity.
 */
public class TaskInstanceVO {
    private String taskInstanceId;
    private String taskId;
    private String taskName;
    private String taskType;
    private Date startDate;
    private Date endDate;
    private String status;
    private String errorFlag;

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
     * Returns the task id.
     *
     * @return the result as a String
     */
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
     * Returns the task name.
     *
     * @return the result as a String
     */
    public String getTaskName() {
        return taskName;
    }

    /**
     * Sets the task name.
     *
     * @param taskName  taskName name
     */
    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    /**
     * Returns the task type.
     *
     * @return the result as a String
     */
    public String getTaskType() {
        return taskType;
    }

    /**
     * Sets the task type.
     *
     * @param taskType  type discriminator
     */
    public void setTaskType(String taskType) {
        this.taskType = taskType;
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
}