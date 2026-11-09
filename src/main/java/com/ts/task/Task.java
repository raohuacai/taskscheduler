/*
 * Description: Core contract for all executable task types
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.task;

import java.util.Map;
/**
 * Core contract that every executable task type must implement.
 *
 * <p>The lifecycle is: {@code execute()} orchestrates {@code preAction()},
 * {@code action()}, and {@code postAction()} in sequence, with cancellation
 * checks between each phase. Implementations should place resource acquisition
 * in {@code preAction()}, business logic in {@code action()}, and cleanup /
 * result persistence in {@code postAction()}.
 *
 * <p>{@link #cancel()} must be idempotent and interrupt the executing thread
 * if the task is blocked on I/O or sleep. {@link #isCanceled()} must be safe
 * to call from any thread.
 *
 * @see AbstractTask
 */
public interface Task {
    /**
     * Execute the task
     *
     * @param parameters Task parameters
     * @throws Exception if task execution fails
     */
    void execute(Map<String, Object> parameters) throws Exception;

    /**
     * Pre-action to be executed before the main task action
     *
     * @param parameters Task parameters
     * @throws Exception if pre-action fails
     */
    void preAction(Map<String, Object> parameters) throws Exception;

    /**
     * Main task action
     *
     * @param parameters Task parameters
     * @throws Exception if action fails
     */
    void action(Map<String, Object> parameters) throws Exception;

    /**
     * Post-action to be executed after the main task action
     *
     * @param parameters Task parameters
     * @throws Exception if post-action fails
     */
    void postAction(Map<String, Object> parameters) throws Exception;

    /**
     * Cancel the task
     */
    boolean cancel();

    boolean isCanceled();

    void cleanupResources();
}
