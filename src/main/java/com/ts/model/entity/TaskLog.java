/*
 * Description: Entity for timestamped task execution log entries
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.model.entity;

import java.util.Date;
/**
 * Entity representing a timestamped log entry for a task execution.
 */
public class TaskLog {
    private String taskLogId;
    private String wfInstanceId;
    private String taskInstanceId;
    private Date creationDate;
    private String message;

    // Getters and setters
    /**
     * Returns the task log id.
     *
     * @return the result as a String
     */
    public String getTaskLogId() {
        return taskLogId;
    }

    /**
     * Sets the task log id.
     *
     * @param taskLogId  taskLogId identifier
     */
    public void setTaskLogId(String taskLogId) {
        this.taskLogId = taskLogId;
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
     * Returns the creation date.
     *
     * @return the requested creationdate object, or null if not found
     */
    public Date getCreationDate() {
        return creationDate;
    }

    /**
     * Sets the creation date.
     *
     * @param creationDate  date/time value
     */
    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * Returns the message.
     *
     * @return the result as a String
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the message.
     *
     * @param message  descriptive message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    /**
     * To the string.
     *
     * @return the result as a String
     */
    public String toString() {
        return "TaskLog{" +
                "taskLogId='" + taskLogId + '\'' +
                ", wfInstanceId='" + wfInstanceId + '\'' +
                ", taskInstanceId='" + taskInstanceId + '\'' +
                ", creationDate=" + creationDate +
                ", message='" + message + '\'' +
                '}';
    }
}