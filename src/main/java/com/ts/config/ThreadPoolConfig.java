/*
 * Description: Configuration bean for the task-execution thread pool parameters
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
/**
 * Configuration bean for the task-execution thread pool.
 *
 * <p>Carries core pool size, maximum pool size, queue capacity, and
 * keep-alive time. Deserialised from the global parameter store at startup
 * by {@link com.ts.core.engine.ThreadPoolManager}.
 */
public class ThreadPoolConfig {
    private int corePoolSize;
    private int maxPoolSize;
    private int keepAliveSeconds;
    private int queueCapacity;
    private String rejectionPolicy;

    // Default constructor with default values
    /**
     * Constructs a new ThreadPoolConfig instance.
     */
    public ThreadPoolConfig() {
        this.corePoolSize = 15;
        this.maxPoolSize = 75;
        this.keepAliveSeconds = 120;
        this.queueCapacity = 150;
        this.rejectionPolicy = "CallerRunsPolicy";
    }

    // Constructor with all parameters
    /**
     * Constructs a new ThreadPoolConfig instance.
     *
     * @param corePoolSize  page size
     * @param maxPoolSize  page size
     * @param keepAliveSeconds  keepAliveSeconds value
     * @param queueCapacity  queueCapacity value
     * @param rejectionPolicy  rejectionPolicy value
     */
    public ThreadPoolConfig(int corePoolSize, int maxPoolSize, int keepAliveSeconds,
                            int queueCapacity, String rejectionPolicy) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveSeconds = keepAliveSeconds;
        this.queueCapacity = queueCapacity;
        this.rejectionPolicy = rejectionPolicy;
    }

    // Getters and Setters
    /**
     * Returns the core pool size.
     *
     * @return the numeric result
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Sets the core pool size.
     *
     * @param corePoolSize  page size
     */
    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    /**
     * Returns the max pool size.
     *
     * @return the numeric result
     */
    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    /**
     * Sets the max pool size.
     *
     * @param maxPoolSize  page size
     */
    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    /**
     * Returns the keep alive seconds.
     *
     * @return the numeric result
     */
    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    /**
     * Sets the keep alive seconds.
     *
     * @param keepAliveSeconds  keepAliveSeconds value
     */
    public void setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }

    /**
     * Returns the queue capacity.
     *
     * @return the numeric result
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /**
     * Sets the queue capacity.
     *
     * @param queueCapacity  queueCapacity value
     */
    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    /**
     * Returns the rejection policy.
     *
     * @return the result as a String
     */
    public String getRejectionPolicy() {
        return rejectionPolicy;
    }

    /**
     * Sets the rejection policy.
     *
     * @param rejectionPolicy  rejectionPolicy value
     */
    public void setRejectionPolicy(String rejectionPolicy) {
        this.rejectionPolicy = rejectionPolicy;
    }

    @Override
    /**
     * To the string.
     *
     * @return the result as a String
     */
    public String toString() {
        return "ThreadPoolConfig{" +
                "corePoolSize=" + corePoolSize +
                ", maxPoolSize=" + maxPoolSize +
                ", keepAliveSeconds=" + keepAliveSeconds +
                ", queueCapacity=" + queueCapacity +
                ", rejectionPolicy='" + rejectionPolicy + '\'' +
                '}';
    }
}