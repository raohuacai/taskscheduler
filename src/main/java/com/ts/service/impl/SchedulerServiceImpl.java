/*
 * Description: Scheduler service persisting and propagating cron schedule changes to the live CronScheduler
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ts.common.constants.Constants;
import com.ts.core.scheduler.Scheduler;
import com.ts.dao.WfDefinitionDao;
import com.ts.dao.WfSchedulerDao;
import com.ts.exception.WorkflowNotFoundException;
import com.ts.exception.WorkflowValidationException;
import com.ts.model.entity.WfDefinition;
import com.ts.model.entity.WfScheduler;
import com.ts.service.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.util.CronExpression;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
/**
 * Implementation of {@link com.ts.service.SchedulerService} that persists
 * schedule records and propagates changes to the live
 * {@link com.ts.core.scheduler.CronScheduler}.
 *
 * <p>Validates cron expressions using the Quartz
 * {@link org.apache.logging.log4j.core.util.CronExpression} parser before
 * persisting; invalid expressions are rejected with an exception.
 */
public class SchedulerServiceImpl implements SchedulerService {
    private static final Logger logger = LogManager.getLogger(SchedulerServiceImpl.class);

    private final WfSchedulerDao wfSchedulerDao;
    private final WfDefinitionDao wfDefinitionDao;
    private Scheduler cronScheduler;

    /**
     * Constructs a new SchedulerServiceImpl instance.
     *
     * @param wfSchedulerDao  DAO dependency
     * @param wfDefinitionDao  DAO dependency
     */
    public SchedulerServiceImpl(WfSchedulerDao wfSchedulerDao, WfDefinitionDao wfDefinitionDao) {
        this.wfSchedulerDao = wfSchedulerDao;
        this.wfDefinitionDao = wfDefinitionDao;

    }

    @Override
    /**
     * Sets the scheduler.
     *
     * @param scheduler  scheduler value
     */
    public void setScheduler(Scheduler scheduler) {
        this.cronScheduler = scheduler;
    }

    @Override
    /**
     * Searches workflow instances with optional filters and returns a paginated result.
     *
     * @param workflowId  workflowId identifier
     * @param workflowName  workflowName name
     * @param pageNum  page number (1-based)
     * @param pageSize  page size
     * @param orderBy  ORDER BY clause
     * @return a paginated result set
     */
    public PageInfo<WfScheduler> search(String workflowId, String workflowName, int pageNum, int pageSize, String orderBy) {
        // Begin paginated query
        //PageHelper.startPage(pageNum, pageSize);
        PageHelper.startPage(pageNum, pageSize, (orderBy == null || orderBy.isEmpty()) ? "WORKFLOW_ID" : orderBy);
        // Invoke the existing query method with filters
        List<WfScheduler> wfSchedulers = wfSchedulerDao.getAllSchedules(workflowId, workflowName);
        // Wrap results with pagination metadata
        return new PageInfo<>(wfSchedulers);
    }

    @Override
    /**
     * Searches workflow instances with optional filters and returns a paginated result.
     *
     * @param workflowId  workflowId identifier
     * @return the result of the operation
     */
    public WfScheduler search(String workflowId) {
        if (workflowId == null || workflowId.isEmpty()) {
            throw new IllegalArgumentException("Workflow ID cannot be null or empty");
        }
        return wfSchedulerDao.getSchedule(workflowId);
    }

    @Override
    /**
     * Creates the scheduler.
     *
     * @param workflowId  workflowId identifier
     * @param cronExpression  cronExpression value
     * @param enableFlag  flag value (Y or N)
     * @param createdBy  createdBy value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean createScheduler(String workflowId, String cronExpression, boolean enableFlag, String createdBy) {
        if (workflowId == null || workflowId.isEmpty()) {
            throw new IllegalArgumentException("Workflow ID cannot be null or empty");
        }

        if (cronExpression == null || cronExpression.isEmpty()) {
            throw new IllegalArgumentException("Cron expression cannot be null or empty");
        }

        try {
            CronExpression.validateExpression(cronExpression);
        } catch (ParseException e) {
            throw new IllegalArgumentException("The cronExpression is not valid:" + cronExpression);
        }

        // Check if workflow exists
        WfDefinition workflow = wfDefinitionDao.getWorkflow(workflowId);
        if (workflow == null) {
            throw new WorkflowNotFoundException("Workflow not found: " + workflowId);
        }

        // Check if schedule already exists
        WfScheduler existingSchedule = wfSchedulerDao.getSchedule(workflowId);
        if (existingSchedule == null) {
            // Create wfScheduler if not exists
            WfScheduler wfScheduler = new WfScheduler();
            wfScheduler.setWorkflowId(workflowId);
            wfScheduler.setCron(cronExpression);
            //wfScheduler.setEnableFlag(Constants.FLAG_YES);
            if (enableFlag) {
                wfScheduler.setEnableFlag(Constants.FLAG_YES);
            } else {
                wfScheduler.setEnableFlag(Constants.FLAG_NO);
            }
            wfScheduler.setCreationDate(new Date());
            wfScheduler.setLastUpdateDate(new Date());
            wfScheduler.setCreatedBy(createdBy);
            wfScheduler.setLastUpdatedBy(createdBy);

            wfSchedulerDao.createSchedule(wfScheduler);
            existingSchedule = wfSchedulerDao.getSchedule(workflowId);
        } else {
            throw new WorkflowValidationException("Workflow already exists: " + workflowId);
        }

        // Schedule in the cronScheduler
        if (existingSchedule.getEnableFlag().equals(Constants.FLAG_YES)) {
            cronScheduler.schedule(workflowId, cronExpression);
            logger.info("Scheduled workflow: {} with cron: {}", workflowId, cronExpression);
        } else {
            cronScheduler.unSchedule(workflowId);
            logger.info("Unscheduled workflow: {}", workflowId);
        }
        return true;
    }

    @Override
    /**
     * Updates the scheduler.
     *
     * @param workflowId  workflowId identifier
     * @param cronExpression  cronExpression value
     * @param enableFlag  flag value (Y or N)
     * @param lastUpdatedBy  date/time value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean updateScheduler(String workflowId, String cronExpression, boolean enableFlag, String lastUpdatedBy) {
        if (workflowId == null || workflowId.isEmpty()) {
            throw new IllegalArgumentException("Workflow ID cannot be null or empty");
        }

        if (cronExpression == null || cronExpression.isEmpty()) {
            throw new IllegalArgumentException("Cron expression cannot be null or empty");
        }

        try {
            CronExpression.validateExpression(cronExpression);
        } catch (ParseException e) {
            throw new IllegalArgumentException("The cronExpression is not valid:" + cronExpression);
        }

        // Check if schedule exists
        WfScheduler existingSchedule = wfSchedulerDao.getSchedule(workflowId);
        if (existingSchedule == null) {
            throw new IllegalArgumentException("Workflow is not scheduled: " + workflowId);
        }

        // Update schedule
        existingSchedule.setCron(cronExpression);
        existingSchedule.setLastUpdateDate(new Date());
        if (enableFlag) {
            existingSchedule.setEnableFlag(Constants.FLAG_YES);
        } else {
            existingSchedule.setEnableFlag(Constants.FLAG_NO);
        }
        existingSchedule.setLastUpdateDate(new Date());
        existingSchedule.setLastUpdatedBy(lastUpdatedBy);

        wfSchedulerDao.updateSchedule(existingSchedule);

        // Update in the cronScheduler
        if (enableFlag) {
            cronScheduler.updateSchedule(workflowId, cronExpression);
        } else {
            cronScheduler.unSchedule(workflowId);
        }

        logger.info("Updated schedule for workflow: {} with cron: {}", workflowId, cronExpression);
        return true;
    }

    @Override
    /**
     * Deletes the scheduler.
     *
     * @param workflowId  workflowId identifier
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean deleteScheduler(String workflowId) {
        if (workflowId == null || workflowId.isEmpty()) {
            throw new IllegalArgumentException("Workflow ID cannot be null or empty");
        }

        // Check if schedule exists
        WfScheduler existingSchedule = wfSchedulerDao.getSchedule(workflowId);
        if (existingSchedule == null) {
            throw new IllegalArgumentException("Workflow is not scheduled: " + workflowId);
        }

        // UnSchedule in the cronScheduler first
        cronScheduler.unSchedule(workflowId);

        // Delete from database
        boolean result = wfSchedulerDao.deleteSchedule(workflowId);

        if (result) {
            logger.info("Deleted schedule for workflow: {}", workflowId);
        } else {
            logger.warn("Failed to delete schedule for workflow: {}", workflowId);
        }

        return result;
    }
}