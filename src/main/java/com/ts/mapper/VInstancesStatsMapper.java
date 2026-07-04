/*
 * Description: MyBatis mapper for workflow instance statistics view
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.mapper;

import com.ts.model.vo.VInstancesStatsVO;
import java.util.List;
/**
 * MyBatis mapper interface for the workflow-instance statistics view.
 */
public interface VInstancesStatsMapper {
    List<VInstancesStatsVO> findAll();
}
