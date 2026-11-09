/*
 * Description: Entity for workflow-level parameter key-value pairs
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.model.entity;

import java.util.Date;
/**
 * Entity representing one parameter entry at the workflow level in the
 * multi-level parameter inheritance hierarchy.
 */
public class WfParameter {
    private String wfParamId;
    private String wfInstanceId;
    private String workflowId;
    private String enableFlag;
    private String pvalue;
    private Date creationDate;
    private String workflowName;
    // Getters and setters

    /**
     * Returns the wf param id.
     *
     * @return the result as a String
     */
    public String getWfParamId() {
        return wfParamId;
    }

    /**
     * Sets the wf param id.
     *
     * @param wfParamId  wfParamId identifier
     */
    public void setWfParamId(String wfParamId) {
        this.wfParamId = wfParamId;
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

    @Override
    /**
     * To the string.
     *
     * @return the result as a String
     */
    public String toString() {
        return "WfParams{" +
                "wfParamId='" + wfParamId + '\'' +
                ", wfInstanceId='" + wfInstanceId + '\'' +
                ", workflowId='" + workflowId + '\'' +
                ", enableFlag='" + enableFlag + '\'' +
                ", pvalue='" + pvalue + '\'' +
                ", creationDate=" + creationDate +
                ", workflowName='" + workflowName + '\'' +
                '}';
    }
}
