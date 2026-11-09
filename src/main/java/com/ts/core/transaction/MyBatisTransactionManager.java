/*
 * Description: ThreadLocal-based MyBatis transaction manager ensuring per-thread SqlSession isolation
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.core.transaction;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
/**
 * {@link TransactionManager} implementation that manages a MyBatis
 * {@link org.apache.ibatis.session.SqlSession} per thread using {@link ThreadLocal}.
 *
 * <p>Each call to {@link #beginTransaction()} opens a new non-auto-commit
 * {@code SqlSession} and stores it in a {@code ThreadLocal}. Subsequent DAO
 * calls on the same thread reuse the same session, giving them ACID semantics.
 * {@link #commitTransaction()} and {@link #rollbackTransaction()} delegate to
 * the stored session, and {@link #close()} removes the entry from the
 * {@code ThreadLocal} to prevent memory leaks.
 */
public class MyBatisTransactionManager implements TransactionManager {
    private static final Logger logger = LogManager.getLogger(MyBatisTransactionManager.class);

    private final SqlSessionFactory sqlSessionFactory;
    private final ThreadLocal<SqlSession> threadLocalSession = new ThreadLocal<>();

    /**
     * Constructs a new MyBatisTransactionManager instance.
     *
     * @param sqlSessionFactory  factory instance
     */
    public MyBatisTransactionManager(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    /**
     * Begins the transaction.
     *
     * @return the result of the operation
     */
    public SqlSession beginTransaction() {
        if (threadLocalSession.get() != null) {
            logger.warn("Transaction already started on this thread");
            return threadLocalSession.get();
        }

        SqlSession session = sqlSessionFactory.openSession(false); // false = manual commit mode
        threadLocalSession.set(session);
        logger.debug("Transaction started");
        return session;
    }

    @Override
    /**
     * Commits the transaction.
     */
    public void commitTransaction() {
        SqlSession session = threadLocalSession.get();
        if (session != null) {
            try {
                session.commit();
                logger.debug("Transaction committed");
            } catch (Exception e) {
                logger.error("Transaction commit failed", e);
                throw new RuntimeException("Transaction commit failed", e);
            } finally {
                closeSession();
            }
        } else {
            logger.warn("No active transaction to commit");
        }
    }

    @Override
    /**
     * Rolls back the transaction.
     */
    public void rollbackTransaction() {
        SqlSession session = threadLocalSession.get();
        if (session != null) {
            try {
                session.rollback();
                logger.debug("Transaction rolled back");
            } catch (Exception e) {
                logger.error("Transaction rollback failed", e);
            } finally {
                closeSession();
            }
        } else {
            logger.warn("No active transaction to rollback");
        }
    }

    @Override
    /**
     * Checks whether the transaction active.
     *
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean isTransactionActive() {
        return threadLocalSession.get() != null;
    }

    @Override
    /**
     * Returns the current session.
     *
     * @return the requested currentsession object, or null if not found
     */
    public SqlSession getCurrentSession() {
        return threadLocalSession.get();
    }

    @Override
    /**
     * Closes.
     */
    public void close() {
        closeSession();
    }

    private void closeSession() {
        SqlSession session = threadLocalSession.get();
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                logger.error("Error closing session", e);
            } finally {
                threadLocalSession.remove();
            }
        }
    }
}
