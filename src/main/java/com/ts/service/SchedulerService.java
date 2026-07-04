/*
 * Description: Contract for cron-schedule definition CRUD with live scheduler propagation
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service;

import com.github.pagehelper.PageInfo;
import com.ts.core.scheduler.Scheduler;
import com.ts.model.entity.WfScheduler;
/**
 * Contract for CRUD operations on cron-based workflow schedule definitions.
 *
 * <p>Schedule changes are applied immediately to the running
 * {@link com.ts.core.scheduler.CronScheduler} without requiring a restart.
 */
public interface SchedulerService {

    void setScheduler(Scheduler scheduler);

    PageInfo<WfScheduler> search(String workflowId, String workflowName, int pageNum, int pageSize, String orderBy);

    WfScheduler search(String workflowId);

    boolean createScheduler(String workflowId, String cronExpression, boolean enableFlag, String createdBy);

    boolean updateScheduler(String workflowId, String cronExpression, boolean enableFlag, String lastUpdatedBy);

    boolean deleteScheduler(String workflowId);

}