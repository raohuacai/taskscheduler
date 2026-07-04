/*
 * Description: Entity for workflow definition templates
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.model.entity;

import java.util.Date;
/**
 * Entity representing a workflow definition — the template from which
 * {@link com.ts.model.entity.WfInstance} records are created at runtime.
 */
public class WfDefinition {
    private String workflowId;
    private String workflowName;
    private String workflowDesc;
    private String enableFlag;
    private String pvalue;
    private String createdBy;
    private Date creationDate;
    private String lastUpdatedBy;
    private Date lastUpdateDate;

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
     * Returns the workflow desc.
     *
     * @return the result as a String
     */
    public String getWorkflowDesc() {
        return workflowDesc;
    }

    /**
     * Sets the workflow desc.
     *
     * @param workflowDesc  workflowDesc value
     */
    public void setWorkflowDesc(String workflowDesc) {
        this.workflowDesc = workflowDesc;
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
        return "WorkflowParams{" +
                "workflowId='" + workflowId + '\'' +
                ", workflowName='" + workflowName + '\'' +
                ", workflowDesc='" + workflowDesc + '\'' +
                ", enableFlag='" + enableFlag + '\'' +
                ", pvalue='" + pvalue + '\'' +
                '}';
    }
}