/*
 * Description: MySQL auto-increment-table-based sequence ID generator
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
 * {@link ISeqIdGenerator} implementation that uses a MySQL auto-increment
 * table as a sequence source.
 *
 * <p>Each call inserts a row and reads back the generated key, providing
 * unique, monotonically increasing IDs without relying on MySQL-specific
 * sequence objects.
 */
public class MySQLSeqIdGenerator {
    private static SqlSessionFactory sqlSessionFactory;
    private static final ReadWriteLock lock = new ReentrantReadWriteLock();

    // Define sequence name constants
    private static final String WF_ID_SEQ       = "WF_ID_SEQ";
    private static final String TASK_ID_SEQ     = "TASK_ID_SEQ";
    private static final String WF_INST_ID_SEQ  = "WF_INST_ID_SEQ";
    private static final String AUDIT_ID_SEQ    = "AUDIT_ID_SEQ";

    /**
     * Initialize ID generator
     *
     * @param factory MyBatis SqlSessionFactory
     */
    public static void init(SqlSessionFactory factory) {
        lock.writeLock().lock();
        try {
            sqlSessionFactory = factory;
            initSequencesIfNeeded();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Initialize sequence table, create if it doesn't exist
     */
    private static void initSequencesIfNeeded() {
        checkInitialization();
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection conn = sqlSession.getConnection();

            // Check if sequence table exists, create it if not
            boolean tableExists = checkTableExists(conn, "ts_sequence");
            if (!tableExists) {
                createSequenceTable(conn);
            }

            // Initialize sequence values
            initSequenceValue(conn, TASK_ID_SEQ,    100, 999999);
            initSequenceValue(conn, WF_ID_SEQ,       10, 999999);
            initSequenceValue(conn, WF_INST_ID_SEQ,   1, 9999999);
            initSequenceValue(conn, AUDIT_ID_SEQ,     1, 999999999);

            sqlSession.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize sequence table", e);
        }
    }

    /**
     * Check if table exists
     */
    private static boolean checkTableExists(Connection conn, String tableName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    /**
     * Create sequence table
     */
    private static void createSequenceTable(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS ts_sequence (" +
                        "    seq_name VARCHAR(50) NOT NULL PRIMARY KEY," +
                        "    current_value BIGINT NOT NULL," +
                        "    min_value BIGINT NOT NULL," +
                        "    max_value BIGINT NOT NULL," +
                        "    increment_by INT NOT NULL DEFAULT 1," +
                        "    cycle_flag BOOLEAN NOT NULL DEFAULT TRUE" +
                        ")"
        )) {
            ps.executeUpdate();
        }
    }

    /**
     * Initialize sequence value, insert if sequence doesn't exist
     */
    private static void initSequenceValue(Connection conn, String seqName, long startValue, long maxValue) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ts_sequence (seq_name, current_value, min_value, max_value) " +
                        "VALUES (?, ?, 1, ?) " +
                        "ON DUPLICATE KEY UPDATE current_value = current_value"
        )) {
            ps.setString(1, seqName);
            ps.setLong(2, startValue);
            ps.setLong(3, maxValue);
            ps.executeUpdate();
        }
    }

    /**
     * Generate workflow ID
     * Format: 6-digit number, padded with leading zeros if necessary
     *
     * @return Workflow ID
     */
    public static String generateWfId() {
        Long seqValue = getNextSequenceValue(WF_ID_SEQ);
        return String.format("%06d", seqValue);
    }

    /**
     * Generate task ID
     * Format: 6-digit number, padded with leading zeros if necessary
     *
     * @return Task ID
     */
    public static String generateTaskId() {
        Long seqValue = getNextSequenceValue(TASK_ID_SEQ);
        return String.format("%06d", seqValue);
    }

    /**
     * Generate workflow instance ID
     * Format: 7-digit number, padded with leading zeros if necessary
     *
     * @return Workflow instance ID
     */
    public static String generateWfInstId() {
        Long seqValue = getNextSequenceValue(WF_INST_ID_SEQ);
        return String.format("%07d", seqValue);
    }

    /**
     * Generate audit log ID
     * Format: 9-digit number, padded with leading zeros if necessary
     *
     * @return Audit log ID
     */
    public static String generateAuditId() {
        Long seqValue = getNextSequenceValue(AUDIT_ID_SEQ);
        return String.format("%09d", seqValue);
    }

    /**
     * Get the next value from the specified sequence
     * Thread-safe implementation, using MySQL transaction isolation to ensure concurrency safety
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
                Connection conn = sqlSession.getConnection();

                // Start transaction and set isolation level to SERIALIZABLE
                conn.setAutoCommit(false);
                conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

                // Get current value and check if cycling is needed
                long currentValue;
                long minValue;
                long maxValue;
                boolean cycleFlag;

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT current_value, min_value, max_value, cycle_flag " +
                                "FROM ts_sequence " +
                                "WHERE seq_name = ? FOR UPDATE"
                )) {
                    ps.setString(1, sequenceName);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            currentValue = rs.getLong("current_value");
                            minValue     = rs.getLong("min_value");
                            maxValue     = rs.getLong("max_value");
                            cycleFlag    = rs.getBoolean("cycle_flag");
                        } else {
                            throw new RuntimeException("Sequence does not exist: " + sequenceName);
                        }
                    }
                }

                // Calculate next value
                long nextValue = currentValue + 1;

                // Check if cycling is needed
                if (nextValue > maxValue) {
                    if (cycleFlag) {
                        nextValue = minValue;
                    } else {
                        throw new RuntimeException("Sequence has reached maximum value: " + sequenceName);
                    }
                }

                // Update sequence value
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE ts_sequence " +
                                "SET current_value = ? " +
                                "WHERE seq_name = ?"
                )) {
                    ps.setLong(1, nextValue);
                    ps.setString(2, sequenceName);
                    ps.executeUpdate();
                }

                // Commit transaction
                conn.commit();

                return nextValue;
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
            initSequencesIfNeeded();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Check if initialized
     */
    private static void checkInitialization() {
        if (sqlSessionFactory == null) {
            throw new IllegalStateException("MySQLSeqIdGenerator not initialized, please call init method first");
        }
    }
}
