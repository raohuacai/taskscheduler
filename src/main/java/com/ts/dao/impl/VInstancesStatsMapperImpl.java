/*
 * Description: MyBatis implementation querying the workflow instance statistics view
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao.impl;

import com.ts.mapper.VInstancesStatsMapper;
import com.ts.model.vo.VInstancesStatsVO;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.List;
/**
 * MyBatis-based implementation that queries the workflow instance statistics
 * view via {@link com.ts.mapper.VInstancesStatsMapper}.
 */
public class VInstancesStatsMapperImpl implements VInstancesStatsMapper {
    private final SqlSessionFactory sqlSessionFactory;

    /**
     * Constructs a new VInstancesStatsMapperImpl instance.
     *
     * @param sqlSessionFactory  factory instance
     */
    public VInstancesStatsMapperImpl(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    /**
     * Finds the all.
     *
     * @return a list of matching records
     */
    public List<VInstancesStatsVO> findAll() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            VInstancesStatsMapper mapper = sqlSession.getMapper(VInstancesStatsMapper.class);
            return mapper.findAll();
        }

    }
}
