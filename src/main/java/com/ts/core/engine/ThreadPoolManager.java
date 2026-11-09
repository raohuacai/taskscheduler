/*
 * Description: Factory and lifecycle manager for the shared task-execution thread pool
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.core.engine;

import com.ts.config.ThreadPoolConfig;
import com.ts.service.ParamsService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.*;
/**
 * Factory and lifecycle manager for the workflow task-execution thread pool.
 *
 * <p>Reads pool configuration (core size, max size, queue capacity, keep-alive)
 * from the {@link com.ts.service.ParamsService} global parameter store and
 * constructs a {@link java.util.concurrent.ThreadPoolExecutor} accordingly.
 * Falls back to a sensible default configuration when no parameters are present.
 *
 * <p>Exposes {@link #shutdownExecutor(java.util.concurrent.ThreadPoolExecutor)}
 * for graceful shutdown with a configurable drain timeout.
 */
public class ThreadPoolManager {
    private static final Logger logger = LogManager.getLogger(ThreadPoolManager.class);

    private final ParamsService paramsService;

    /**
     * Constructs a new ThreadPoolManager instance.
     *
     * @param paramsService  service dependency
     */
    public ThreadPoolManager(ParamsService paramsService) {
        this.paramsService = paramsService;
        logger.info("Initialized ThreadPoolManager with ParamsService");
    }

    /**
     * Creates a thread pool executor based on the configuration from the parameter service
     *
     * @return Configured ThreadPoolExecutor
     */
    public ThreadPoolExecutor createExecutorService() {
        // Get configuration from parameter service
        ThreadPoolConfig config = paramsService.getThreadPoolConfig();
        logger.debug("Creating thread pool with configuration: {}", config);

        // Get configuration values
        int corePoolSize = config.getCorePoolSize();
        int maxPoolSize = config.getMaxPoolSize();
        int keepAliveSeconds = config.getKeepAliveSeconds();
        int queueCapacity = config.getQueueCapacity();
        String rejectionPolicy = config.getRejectionPolicy();

        // Create work queue with configured capacity
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(queueCapacity);

        // Use default thread factory with custom naming pattern
        ThreadFactory threadFactory = new ThreadFactory() {
            private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();
            private final java.util.concurrent.atomic.AtomicInteger threadNumber = new java.util.concurrent.atomic.AtomicInteger(1);

            @Override
            /**
             * Creates the thread.
             *
             * @param r  r value
             * @return the result of the operation
             */
            public Thread newThread(Runnable r) {
                Thread thread = defaultFactory.newThread(r);
                thread.setName("workflow-executor-" + threadNumber.getAndIncrement());
                return thread;
            }
        };

        // Configure rejection policy
        RejectedExecutionHandler handler = createRejectionHandler(rejectionPolicy);

        // Create and configure ThreadPoolExecutor
        ThreadPoolExecutor executorService = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                workQueue,
                threadFactory,
                handler
        );

        // Allow core threads to time out if they are idle
        executorService.allowCoreThreadTimeOut(true);

        logger.info("Created workflow thread pool: core={}, max={}, keepAliveTime={}s, queue={}, rejectionPolicy={}",
                corePoolSize, maxPoolSize, keepAliveSeconds, queueCapacity, rejectionPolicy);

        return executorService;
    }

    /**
     * Gets a RejectedExecutionHandler based on the configured policy name
     *
     * @param rejectionPolicy Policy name (CallerRunsPolicy, AbortPolicy, etc.)
     * @return Appropriate RejectedExecutionHandler
     */
    private RejectedExecutionHandler createRejectionHandler(String rejectionPolicy) {
        switch (rejectionPolicy) {
            case "AbortPolicy":
                return new ThreadPoolExecutor.AbortPolicy();
            case "DiscardPolicy":
                return new ThreadPoolExecutor.DiscardPolicy();
            case "DiscardOldestPolicy":
                return new ThreadPoolExecutor.DiscardOldestPolicy();
            case "CallerRunsPolicy":
            default:
                return new ThreadPoolExecutor.CallerRunsPolicy();
        }
    }

    /**
     * Updates the thread pool with new configuration values
     *
     * @param executor The executor to update
     */
    public void updateThreadPool(ThreadPoolExecutor executor) {
        if (executor == null) {
            logger.warn("Cannot update null executor");
            return;
        }

        ThreadPoolConfig config = paramsService.getThreadPoolConfig();
        logger.info("Updating thread pool with configuration: {}", config);

        // Update core pool size
        executor.setCorePoolSize(config.getCorePoolSize());

        // Update maximum pool size (must be set after core pool size if we're reducing the pool size)
        executor.setMaximumPoolSize(config.getMaxPoolSize());

        // Update keep alive time
        executor.setKeepAliveTime(config.getKeepAliveSeconds(), TimeUnit.SECONDS);

        // Note: We cannot update the queue capacity or rejection policy without recreating the executor

        logger.info("Thread pool updated: core={}, max={}, keepAliveTime={}s",
                config.getCorePoolSize(), config.getMaxPoolSize(), config.getKeepAliveSeconds());
    }

    /**
     * Safely shuts down the executor service
     *
     * @param executor The executor to shut down
     * @param timeout The timeout period
     * @param unit The timeout time unit
     * @return true if all tasks completed before timeout, false otherwise
     */
    public boolean shutdownExecutorService(ThreadPoolExecutor executor, long timeout, TimeUnit unit) {
        if (executor == null || executor.isShutdown()) {
            return true;
        }

        logger.info("Shutting down workflow executor service");

        try {
            // Disable new tasks from being submitted
            executor.shutdown();

            // Wait for existing tasks to terminate
            if (!executor.awaitTermination(timeout, unit)) {
                // Cancel currently executing tasks
                executor.shutdownNow();

                // Wait a while for tasks to respond to being cancelled
                if (!executor.awaitTermination(timeout, unit)) {
                    logger.warn("Thread pool did not terminate completely");
                    return false;
                }
            }

            logger.info("Workflow executor service shutdown completed");
            return true;

        } catch (InterruptedException e) {
            // (Re-)Cancel if current thread also interrupted
            executor.shutdownNow();

            // Preserve interrupt status
            Thread.currentThread().interrupt();

            logger.warn("Thread pool shutdown interrupted", e);
            return false;
        }
    }
}