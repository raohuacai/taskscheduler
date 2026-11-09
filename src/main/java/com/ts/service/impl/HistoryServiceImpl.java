/*
 * Description: History service querying the workflow run-history view with PageHelper pagination
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ts.dao.HistoryDao;
import com.ts.model.vo.WorkflowInstanceVO;
import com.ts.service.HistoryService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;
import java.util.List;

/**
 * Implementation of {@link com.ts.service.HistoryService} that queries the
 * workflow run-history view via {@link com.ts.dao.HistoryDao} using
 * {@link com.github.pagehelper.PageHelper} for transparent pagination.
 */
public class HistoryServiceImpl implements HistoryService {
    private static final Logger logger = LogManager.getLogger(HistoryServiceImpl.class);

    private final HistoryDao historyDao;

    /**
     * Constructs a new HistoryServiceImpl instance.
     *
     * @param historyDao  DAO dependency
     */
    public HistoryServiceImpl(HistoryDao historyDao) {
        this.historyDao = historyDao;
    }

    @Override
    /**
     * Searches workflow instances with optional filters and returns a paginated result.
     *
     * @param workflowId  workflowId identifier
     * @param workflowName  workflowName name
     * @param instanceId  instance identifier
     * @param parentWfInstanceId  instance identifier
     * @param topParentInstanceId  instance identifier
     * @param status  status value
     * @param startDateFrom  date/time value
     * @param startDateTo  date/time value
     * @param pageNum  page number (1-based)
     * @param pageSize  page size
     * @param orderBy  ORDER BY clause
     * @return a paginated result set
     */
    public PageInfo<WorkflowInstanceVO> search(
            String workflowId,
            String workflowName,
            String instanceId,
            String parentWfInstanceId,
            String topParentInstanceId,
            String status,
            Date startDateFrom,
            Date startDateTo,
            int pageNum,
            int pageSize,
            String orderBy) {

        logger.debug("Searching workflow history with filters: workflowId={}, workflowName={}, instanceId={}, parentWfInstanceId={}, topParentInstanceId={}, status={}",
                workflowId, workflowName, instanceId, parentWfInstanceId, topParentInstanceId, status);

        // Start pagination with order by
        //PageHelper.startPage(pageNum, pageSize, (orderBy == null || orderBy.isEmpty()) ? "START_DATE DESC" : orderBy);
        PageHelper.startPage(pageNum, pageSize, null);

        // Execute search query
        List<WorkflowInstanceVO> instances = historyDao.search(workflowId, workflowName, instanceId,
                parentWfInstanceId, topParentInstanceId, status, startDateFrom, startDateTo);

        // Create page info
        return new PageInfo<>(instances);
    }

    @Override
    /**
     * Search the all.
     *
     * @param workflowId  workflowId identifier
     * @param workflowName  workflowName name
     * @param instanceId  instance identifier
     * @param parentWfInstanceId  instance identifier
     * @param topParentInstanceId  instance identifier
     * @param status  status value
     * @param startDateFrom  date/time value
     * @param startDateTo  date/time value
     * @return a list of matching records
     */
    public List<WorkflowInstanceVO> searchAll(
            String workflowId,
            String workflowName,
            String instanceId,
            String parentWfInstanceId,
            String topParentInstanceId,
            String status,
            Date startDateFrom,
            Date startDateTo) {
        logger.debug("Searching workflow history with filters: workflowId={}, workflowName={}, instanceId={}, parentWfInstanceId={}, topParentInstanceId={}, status={}",
                workflowId, workflowName, instanceId, parentWfInstanceId, topParentInstanceId, status);
        // Only support export 100 000 records
        PageHelper.startPage(1, 100000, null);
        // Execute search query
        List<WorkflowInstanceVO> instances = historyDao.search(workflowId, workflowName, instanceId,
                parentWfInstanceId, topParentInstanceId, status, startDateFrom, startDateTo);

        return instances;
    }

}
