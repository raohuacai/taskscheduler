/*
 * Description: DAO interface for global parameter record access
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao;

import com.ts.model.entity.GlobalParams;

import java.util.List;
import java.util.Map;
/**
 * DAO interface for reading and updating the global parameter record.
 */
public interface GlobalParamsDao {
    List<GlobalParams> getAllGlobalParams();

    GlobalParams getGlobalParams(String pName);

    boolean updateGlobalParams(String pName, Map<String, Object> params);
}