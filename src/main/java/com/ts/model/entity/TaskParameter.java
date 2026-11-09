/*
 * Description: Entity for task-level parameter key-value pairs
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.model.entity;

import java.util.Date;
/**
 * Entity representing one parameter entry (key + value) at the task level
 * in the multi-level parameter inheritance hierarchy.
 */
public class TaskParameter {
    private String taskParamId;
    private String wfInstanceId;
    private String taskInstanceId;
    private String workflowId;
    private String taskId;
    private String taskType;
    private String enableFlag;
    private String predecessors;
    private String pvalue;
    private Date creationDate;
    private String taskName;
    // Getters and setters

    /**
     * Returns the pvalue.
     *
     * @return the result as a String
     */
    public String getPvalue() {
        return pvalue;
    }

    /**
     * Sets the pvalue.
     *
     * @param pvalue  pvalue value
     */
    public void setPvalue(String pvalue) {
        this.pvalue = pvalue;
    }

    /**
     * Returns the task param id.
     *
     * @return the result as a String
     */
    public String getTaskParamId() {
        return taskParamId;
    }

    /**
     * Sets the task param id.
     *
     * @param taskParamId  taskParamId identifier
     */
    public void setTaskParamId(String taskParamId) {
        this.taskParamId = taskParamId;
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
     * Returns the enable flag.
     *
     * @return the result as a String
     */
    public String getEnableFlag() {
        return enableFlag;
    }

    /**
     * Sets the enable flag.
     *
     * @param enableFlag  flag value (Y or N)
     */
    public void setEnableFlag(String enableFlag) {
        this.enableFlag = enableFlag;
    }

    /**
     * Returns the predecessors.
     *
     * @return the result as a String
     */
    public String getPredecessors() {
        return predecessors;
    }

    /**
     * Sets the predecessors.
     *
     * @param predecessors  predecessors value
     */
    public void setPredecessors(String predecessors) {
        this.predecessors = predecessors;
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

    @Override
    /**
     * To the string.
     *
     * @return the result as a String
     */
    public String toString() {
        return "TaskParams{" +
                "taskParamId='" + taskParamId + '\'' +
                ", wfInstanceId='" + wfInstanceId + '\'' +
                ", taskInstanceId='" + taskInstanceId + '\'' +
                ", workflowId='" + workflowId + '\'' +
                ", taskId='" + taskId + '\'' +
                ", taskType='" + taskType + '\'' +
                ", enableFlag='" + enableFlag + '\'' +
                ", predecessors='" + predecessors + '\'' +
                ", pvalue='" + pvalue + '\'' +
                ", creationDate=" + creationDate +
                ", taskName='" + taskName + '\'' +
                '}';
    }
}
