/*
 * Description: Oracle SEQUENCE NEXTVAL-based sequence ID generator
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.common.SeqId;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
/**
 * {@link ISeqIdGenerator} implementation that queries an Oracle database
 * sequence ({@code NEXTVAL}) to produce unique IDs.
 */
public class OracleSeqIdGenerator {
    private static SqlSessionFactory sqlSessionFactory;
    private static final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Initialize ID generator
     *
     * @param factory MyBatis SqlSessionFactory
     */
    public static void init(SqlSessionFactory factory) {
        lock.writeLock().lock();
        try {
            sqlSessionFactory = factory;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Generate workflow ID
     * Format: 6-digit number, padded with leading zeros if necessary
     *
     * @return Workflow ID
     */
    public static String generateWfId() {
        Long seqValue = getNextSequenceValue("WF_ID_SEQ");
        return String.format("%06d", seqValue);
    }

    /**
     * Generate task ID
     * Format: 6-digit number, padded with leading zeros if necessary
     *
     * @return Task ID
     */
    public static String generateTaskId() {
        Long seqValue = getNextSequenceValue("TASK_ID_SEQ");
        return String.format("%06d", seqValue);
    }

    /**
     * Generate workflow instance ID
     * Format: 7-digit number, padded with leading zeros if necessary
     *
     * @return Workflow instance ID
     */
    public static String generateWfInstId() {
        Long seqValue = getNextSequenceValue("WF_INST_ID_SEQ");
        return String.format("%07d", seqValue);
    }

    /**
     * Generate audit log ID
     * Format: 9-digit number, padded with leading zeros if necessary
     *
     * @return Audit log ID
     */
    public static String generateAuditId() {
        Long seqValue = getNextSequenceValue("AUDIT_ID_SEQ");
        return String.format("%09d", seqValue);
    }

    /**
     * Get the next value from the specified sequence
     * Thread-safe implementation
     *
     * @param sequenceName Sequence name
     * @return Sequence value
     */
    private static Long getNextSequenceValue(String sequenceName) {
        checkInitialization();
        lock.readLock().lock();
        try {
            SqlSessionFactory factory = sqlSessionFactory;
            try (SqlSession sqlSession = factory.openSession()) {
                try (Connection conn = sqlSession.getConnection();
                     PreparedStatement ps = conn.prepareStatement("SELECT " + sequenceName + ".NEXTVAL FROM DUAL");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    } else {
                        throw new RuntimeException("Unable to get sequence value: " + sequenceName);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get sequence value: " + sequenceName, e);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Refresh SqlSessionFactory
     * Can be called when database connection information changes
     *
     * @param factory New SqlSessionFactory
     */
    public static void refresh(SqlSessionFactory factory) {
        lock.writeLock().lock();
        try {
            sqlSessionFactory = factory;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Check if initialized
     */
    private static void checkInitialization() {
        if (sqlSessionFactory == null) {
            throw new IllegalStateException("OracleSeqIdGenerator not initialized, please call init method first");
        }
    }
}
