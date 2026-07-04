/*
 * Description: Entity for cron-based workflow schedule definitions
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.model.entity;

import java.util.Date;
/**
 * Entity representing a cron-based schedule that triggers a workflow definition
 * at the specified times.
 */
public class WfScheduler {
    private String workflowId;
    private String workflowName;
    private String cron;
    private String enableFlag;
    private String createdBy;
    private Date creationDate;
    private String lastUpdatedBy;
    private Date lastUpdateDate;
    private Date nextRunTime;
    // Getters and setters

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

    /** Returns the workflow definition name this schedule will trigger. */
    public String getWorkflowName() {return workflowName;}

    public void setWorkflowName(String workflowName) {this.workflowName = workflowName;}

    public String getCron() {
        return cron;
    }

    /**
     * Sets the cron.
     *
     * @param cron  cron value
     */
    public void setCron(String cron) {
        this.cron = cron;
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

    /**
     * Returns the next run time.
     *
     * @return the requested nextruntime object, or null if not found
     */
    public Date getNextRunTime() {
        return nextRunTime;
    }

    /**
     * Sets the next run time.
     *
     * @param nextRunTime  date/time value
     */
    public void setNextRunTime(Date nextRunTime) {
        this.nextRunTime = nextRunTime;
    }

    @Override
    /**
     * To the string.
     *
     * @return the result as a String
     */
    public String toString() {
        return "WfScheduler{" +
                "workflowId='" + workflowId + '\'' +
                ", cron='" + cron + '\'' +
                ", enableFlag='" + enableFlag + '\'' +
                '}';
    }
}