/*
 * Description: MyBatis mapper for cron-schedule definitions
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.mapper;

import com.ts.model.entity.WfScheduler;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * MyBatis mapper interface for cron-schedule definition persistence.
 */
public interface WfSchedulerMapper {
    WfScheduler selectByPrimaryKey(@Param("workflowId") String workflowId);

    List<WfScheduler> selectAll(@Param("workflowId") String workflowId,
                                @Param("workflowName") String workflowName);

    List<WfScheduler> selectAllActives();

    int insert(WfScheduler record);

    int updateByPrimaryKey(WfScheduler record);

    int deleteByPrimaryKey(@Param("workflowId") String workflowId);

    List<String> selectAllActiveWorkflowIds();

    int countActiveSchedulerWorkflows();
}