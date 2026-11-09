/*
 * Description: MyBatis implementation of AuditLogDao
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao.impl;

import com.ts.model.entity.AuditLog;
import com.ts.dao.AuditLogDao;
import com.ts.mapper.AuditLogMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
/**
 * MyBatis-based implementation of {@link com.ts.dao.AuditLogDao}.
 */
public class AuditLogDaoImpl implements AuditLogDao {

    private static final Logger logger = LogManager.getLogger(AuditLogDaoImpl.class);

    private final SqlSessionFactory sqlSessionFactory;

    /**
     * Constructs a new AuditLogDaoImpl instance.
     *
     * @param sqlSessionFactory  factory instance
     */
    public AuditLogDaoImpl(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    /**
     * Saves the audit log.
     *
     * @param auditLog  auditLog value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean saveAuditLog(AuditLog auditLog) {
        if (auditLog == null) {
            return false;
        }
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            AuditLogMapper mapper = sqlSession.getMapper(AuditLogMapper.class);
            mapper.insert(auditLog);
            sqlSession.commit();
            return true;
        } catch (Exception e) {
            logger.error("Error saving audit log [op={}, operator={}]",
                    auditLog.getOperation(), auditLog.getOperator(), e);
            return false;
        }
    }
}
