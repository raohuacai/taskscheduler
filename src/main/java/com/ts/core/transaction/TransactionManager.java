/*
 * Description: Abstraction for thread-bound JDBC transaction lifecycle management
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.core.transaction;

import org.apache.ibatis.session.SqlSession;
/**
 * Abstraction over database transaction lifecycle (begin, commit, rollback, close).
 *
 * <p>Implementations are expected to be thread-bound: each thread obtains its own
 * {@link org.apache.ibatis.session.SqlSession} via {@link #beginTransaction()} and
 * must release it by calling {@link #close()} or letting the transaction complete.
 *
 * @see MyBatisTransactionManager
 * @see TransactionManagerFactory
 */
public interface TransactionManager {
    /**
     * Begins a new database transaction
     *
     * @return the SqlSession associated with the new transaction
     */
    SqlSession beginTransaction();

    /**
     * Commits the current active transaction
     */
    void commitTransaction();

    /**
     * Rolls back the current active transaction
     */
    void rollbackTransaction();

    /**
     * Checks whether there is currently an active transaction
     *
     * @return {@code true} if there is an active transaction, {@code false} otherwise
     */
    boolean isTransactionActive();

    /**
     * Returns the current SqlSession if there is an active transaction
     *
     * @return the current SqlSession, or {@code null} if there is no active transaction
     */
    SqlSession getCurrentSession();

    /**
     * Closes the current transaction and releases all associated resources
     */
    void close();
}