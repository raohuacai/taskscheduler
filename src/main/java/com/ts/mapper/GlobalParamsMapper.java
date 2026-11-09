/*
 * Description: MyBatis mapper for global parameters table
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.mapper;

import com.ts.model.entity.GlobalParams;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * MyBatis mapper interface for the global-parameters table.
 */
public interface GlobalParamsMapper {
    List<GlobalParams> selectAll();

    GlobalParams selectByPrimaryKey(@Param("pname") String pname);

    int updateByPrimaryKey(GlobalParams record);
}