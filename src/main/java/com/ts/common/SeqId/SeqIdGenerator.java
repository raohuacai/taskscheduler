/*
 * Description: Application facade for database sequence ID generation
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.common.SeqId;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
/**
 * Application facade for database-backed sequence ID generation.
 *
 * <p>Delegates to an {@link ISeqIdGenerator} implementation chosen at startup
 * based on the configured database type. Provides named factory methods
 * ({@link #generateWfInstId()}, {@link #generateTaskInstId()}, etc.) so
 * callers use meaningful names rather than raw sequence queries.
 */
public class SeqIdGenerator {
    private static final Logger logger = LogManager.getLogger(SeqIdGenerator.class);
    private static SqlSessionFactory sqlSessionFactory;
    private static ISeqIdGenerator instance;

    /**
     * Prevent direct instantiation
     */
    private SeqIdGenerator() {
    }

    /**
     * Initialize sequence generator
     *
     * @param factory MyBatis SqlSessionFactory
     */
    public static synchronized void initialize(SqlSessionFactory factory) {
        sqlSessionFactory = factory;
        String dbType = detectDatabaseType(factory);

        if ("ORACLE".equalsIgnoreCase(dbType)) {
            logger.info("Using Oracle sequence generator");
            OracleSeqIdGenerator.refresh(factory);
            instance = new OracleSeqIdGeneratorAdapter();
        } else {
            logger.info("Using MySQL sequence generator");
            MySQLSeqIdGenerator.refresh(factory);
            instance = new MySQLSeqIdGeneratorAdapter();
        }
    }

    /**
     * Detect database type
     *
     * @param factory SqlSessionFactory
     * @return Database type string
     */
    private static String detectDatabaseType(SqlSessionFactory factory) {
        try (SqlSession session = factory.openSession()) {
            Connection conn = session.getConnection();
            DatabaseMetaData metaData = conn.getMetaData();
            String dbProductName = metaData.getDatabaseProductName().toLowerCase();

            if (dbProductName.contains("oracle")) {
                return "ORACLE";
            } else if (dbProductName.contains("mysql") || dbProductName.contains("mariadb")) {
                return "MYSQL";
            } else {
                logger.warn("Unknown database type: " + dbProductName + ", defaulting to MySQL implementation");
                return "MYSQL";
            }
        } catch (SQLException e) {
            logger.error("Failed to detect database type: " + e.getMessage() + ", defaulting to MySQL implementation");
            return "MYSQL";
        }
    }

    /**
     * Get sequence generator instance
     */
    private static ISeqIdGenerator getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Sequence generator not initialized, please call initialize method first");
        }
        return instance;
    }

    /**
     * Generate workflow ID
     *
     * @return Workflow ID
     */
    public static String generateWfId() {
        return getInstance().generateWfId();
    }

    /**
     * Generate task ID
     *
     * @return Task ID
     */
    public static String generateTaskId() {
        return getInstance().generateTaskId();
    }

    /**
     * Generate workflow instance ID
     *
     * @return Workflow instance ID
     */
    public static String generateWfInstId() {
        return getInstance().generateWfInstId();
    }

    /**
     * Generate audit log ID
     *
     * @return Audit log ID
     */
    public static String generateAuditId() {
        return getInstance().generateAuditId();
    }

    /**
     * Oracle sequence generator adapter
     */
    private static class OracleSeqIdGeneratorAdapter implements ISeqIdGenerator {
        @Override
        /**
         * Generates the wf id.
         *
         * @return the result as a String
         */
        public String generateWfId() {
            return OracleSeqIdGenerator.generateWfId();
        }

        @Override
        /**
         * Generates the task id.
         *
         * @return the result as a String
         */
        public String generateTaskId() {
            return OracleSeqIdGenerator.generateTaskId();
        }

        @Override
        /**
         * Generates the wf inst id.
         *
         * @return the result as a String
         */
        public String generateWfInstId() {
            return OracleSeqIdGenerator.generateWfInstId();
        }

        @Override
        /**
         * Generates the audit id.
         *
         * @return the result as a String
         */
        public String generateAuditId() {
            return OracleSeqIdGenerator.generateAuditId();
        }
    }

    /**
     * MySQL sequence generator adapter
     */
    private static class MySQLSeqIdGeneratorAdapter implements ISeqIdGenerator {
        @Override
        /**
         * Generates the wf id.
         *
         * @return the result as a String
         */
        public String generateWfId() {
            return MySQLSeqIdGenerator.generateWfId();
        }

        @Override
        /**
         * Generates the task id.
         *
         * @return the result as a String
         */
        public String generateTaskId() {
            return MySQLSeqIdGenerator.generateTaskId();
        }

        @Override
        /**
         * Generates the wf inst id.
         *
         * @return the result as a String
         */
        public String generateWfInstId() {
            return MySQLSeqIdGenerator.generateWfInstId();
        }

        @Override
        /**
         * Generates the audit id.
         *
         * @return the result as a String
         */
        public String generateAuditId() {
            return MySQLSeqIdGenerator.generateAuditId();
        }
    }
}
