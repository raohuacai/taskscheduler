/*
 * Description: View object carrying aggregated workflow instance status counts
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.model.vo;

import java.util.Date;
/**
 * View object carrying aggregated workflow instance statistics derived from
 * the {@code V_WF_INSTANCES_STATS} database view.
 *
 * <p>Used by the dashboard to display counts of instances in each status
 * (running, completed, error, etc.) within a configurable time window.
 */
public class VInstancesStatsVO {
    private String dtype;
    private Date startDate;
    private Integer completedCount;
    private Integer errorCount;
    private Integer cancelCount;

    /**
     * Returns the dtype.
     *
     * @return the result as a String
     */
    public String getDtype() {
        return dtype;
    }

    /**
     * Sets the dtype.
     *
     * @param dtype  type discriminator
     */
    public void setDtype(String dtype) {
        this.dtype = dtype;
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
     * Returns the completed count.
     *
     * @return the requested completedcount object, or null if not found
     */
    public Integer getCompletedCount() {
        return completedCount;
    }

    /**
     * Sets the completed count.
     *
     * @param completedCount  count or limit
     */
    public void setCompletedCount(Integer completedCount) {
        this.completedCount = completedCount;
    }

    /**
     * Returns the error count.
     *
     * @return the requested errorcount object, or null if not found
     */
    public Integer getErrorCount() {
        return errorCount;
    }

    /**
     * Sets the error count.
     *
     * @param errorCount  count or limit
     */
    public void setErrorCount(Integer errorCount) {
        this.errorCount = errorCount;
    }

    /**
     * Returns the cancel count.
     *
     * @return the requested cancelcount object, or null if not found
     */
    public Integer getCancelCount() {
        return cancelCount;
    }

    /**
     * Sets the cancel count.
     *
     * @param cancelCount  count or limit
     */
    public void setCancelCount(Integer cancelCount) {
        this.cancelCount = cancelCount;
    }

    @Override
    /**
     * To the string.
     *
     * @return the result as a String
     */
    public String toString() {
        return "VInstancesStatsVO{" +
                "dtype='" + dtype + '\'' +
                ", startDate=" + startDate +
                ", completedCount=" + completedCount +
                ", errorCount=" + errorCount +
                ", cancelCount=" + cancelCount +
                '}';
    }
}
