/*
 * Description: Singleton factory that initialises and provides the application-wide TransactionManager
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.core.transaction;

import org.apache.ibatis.session.SqlSessionFactory;
/**
 * Singleton factory that creates and provides the application-wide
 * {@link TransactionManager} instance.
 *
 * <p>Must be initialised exactly once at startup by calling
 * {@link #initialize(org.apache.ibatis.session.SqlSessionFactory)} before any
 * component calls {@link #getInstance()}. Subsequent calls to {@code initialize()}
 * are no-ops (instance is created only once). Provides {@link #reset()} for
 * test isolation.
 */
public class TransactionManagerFactory {

    private static TransactionManager instance;

    /**
     * Initialises the singleton TransactionManager with the provided SqlSessionFactory
     *
     * @param sqlSessionFactory MyBatis SqlSessionFactory
     * @return the created TransactionManager instance
     */
    public static synchronized TransactionManager initialize(SqlSessionFactory sqlSessionFactory) {
        if (instance == null) {
            instance = new MyBatisTransactionManager(sqlSessionFactory);
        }
        return instance;
    }

    /**
     * Retrieves the current TransactionManager instance
     *
     * @return the created TransactionManager instance
     * @throws IllegalStateException if the TransactionManager has not been initialized yet
     */
    public static TransactionManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("TransactionManager has not been initialized. Please call the initialize method first.");
        }
        return instance;
    }

    /**
     * Resets the TransactionManager instance (intended for use in tests)
     */
    public static synchronized void reset() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
}