/*
 * Description: MyBatis mapper for workflow-level parameters
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.mapper;

import com.ts.model.entity.WfParameter;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * MyBatis mapper interface for workflow-level parameter definitions.
 */
public interface WfParameterMapper {
    WfParameter selectByPrimaryKey(@Param("wfParamId") String wfParamId);

    WfParameter selectBywfInstanceId(@Param("wfInstanceId") String wfInstanceId);

    List<WfParameter> selectAll();

    int insert(WfParameter record);

    int updatePvalueByPrimaryKey(@Param("wfParamId") String wfParamId, @Param("pvalue") String pvalue);

    int updatePvalueBywfInstanceId(@Param("wfInstanceId") String wfInstanceId, @Param("pvalue") String pvalue);
}