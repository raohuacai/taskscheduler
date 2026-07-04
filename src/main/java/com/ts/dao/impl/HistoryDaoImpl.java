/*
 * Description: MyBatis implementation of HistoryDao for run-history view queries
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao.impl;

import com.ts.common.utils.ParamUtils;
import com.ts.dao.HistoryDao;
import com.ts.mapper.HistoryMapper;
import com.ts.model.vo.WorkflowInstanceVO;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * MyBatis-based implementation of {@link com.ts.dao.HistoryDao} that queries
 * the workflow run-history view via {@link com.ts.mapper.HistoryMapper}.
 */
public class HistoryDaoImpl implements HistoryDao {
    private static final Logger logger = LogManager.getLogger(HistoryDaoImpl.class);
    private final SqlSessionFactory sqlSessionFactory;

    /**
     * Constructs a new HistoryDaoImpl instance.
     *
     * @param sqlSessionFactory  factory instance
     */
    public HistoryDaoImpl(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
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
     * @return a list of matching records
     */
    public List<WorkflowInstanceVO> search(
            String workflowId,
            String workflowName,
            String instanceId,
            String parentWfInstanceId,
            String topParentInstanceId,
            String status,
            Date startDateFrom,
            Date startDateTo) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            HistoryMapper mapper = sqlSession.getMapper(HistoryMapper.class);
            return mapper.search(
                    ParamUtils.likePattern(workflowId), ParamUtils.likePattern(workflowName), ParamUtils.likePattern(instanceId),
                    ParamUtils.likePattern(parentWfInstanceId), ParamUtils.likePattern(topParentInstanceId),
                    status, startDateFrom, startDateTo);
        } catch (Exception e) {
            logger.error("Error getting history log", e);
            return new ArrayList<>();
        }
    }

}