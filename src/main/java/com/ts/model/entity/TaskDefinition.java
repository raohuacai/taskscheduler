/*
 * Description: Entity for TS_TASK_DEFINITION workflow task templates
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.model.entity;

import java.util.Date;
/**
 * Entity representing a task definition within a workflow definition.
 *
 * <p>Stores the task type, display name, parameter JSON, dependency JSON
 * (used to build the {@link com.ts.core.dag.WorkflowDAG}), and position
 * metadata for the visual editor.
 */
public class TaskDefinition {
    private String taskId;
    private String workflowId;
    private String taskName;
    private String taskDesc;
    private String taskType;
    private String enableFlag;
    private String predecessors;
    private String pvalue;
    private String createdBy;
    private Date creationDate;
    private String lastUpdatedBy;
    private Date lastUpdateDate;
    // Getters and setters

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
     * Returns the task desc.
     *
     * @return the result as a String
     */
    public String getTaskDesc() {
        return taskDesc;
    }

    /**
     * Sets the task desc.
     *
     * @param taskDesc  taskDesc value
     */
    public void setTaskDesc(String taskDesc) {
        this.taskDesc = taskDesc;
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
     * Returns the created by.
     *
     * @return the result as a String
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * Sets the created by.
     *
     * @param createdBy  createdBy value
     */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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
     * Returns the last updated by.
     *
     * @return the result as a String
     */
    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    /**
     * Sets the last updated by.
     *
     * @param lastUpdatedBy  date/time value
     */
    public void setLastUpdatedBy(String lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }

    /**
     * Returns the last update date.
     *
     * @return the requested lastupdatedate object, or null if not found
     */
    public Date getLastUpdateDate() {
        return lastUpdateDate;
    }

    /**
     * Sets the last update date.
     *
     * @param lastUpdateDate  date/time value
     */
    public void setLastUpdateDate(Date lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    @Override
    /**
     * To the string.
     *
     * @return the result as a String
     */
    public String toString() {
        return "TaskParams{" +
                "taskId='" + taskId + '\'' +
                ", workflowId='" + workflowId + '\'' +
                ", taskName='" + taskName + '\'' +
                ", taskDesc='" + taskDesc + '\'' +
                ", taskType='" + taskType + '\'' +
                ", enableFlag='" + enableFlag + '\'' +
                ", predecessors='" + predecessors + '\'' +
                ", pvalue='" + pvalue + '\'' +
                '}';
    }
}