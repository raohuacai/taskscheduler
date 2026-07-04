/*
 * Description: Task type that acquires or releases a distributed database lock with retry semantics
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.task.core;

import com.ts.Main;
import com.ts.common.constants.Constants;
import com.ts.common.utils.ParamUtils;
import com.ts.exception.TaskExecutionException;
import com.ts.exception.TaskTimeoutException;
import com.ts.service.LockService;
import com.ts.service.impl.TaskLogService;
import com.ts.task.AbstractTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * Task type that acquires or releases a database-level distributed lock.
 *
 * <p>Two actions are supported via the {@code lockAction} parameter:
 * <ul>
 *   <li><b>acquireLock</b> (default) — attempts to insert a lock record into
 *       {@code TS_LOCK}. If the insert fails (record already exists), the task
 *       sleeps {@code DEFAULT_RETRY_INTERVAL} ms and retries until the task timeout
 *       is reached, then throws {@link com.ts.exception.TaskTimeoutException}.</li>
 *   <li><b>releaseLock</b> — deletes the matching lock record. If the task was
 *       cancelled or timed out after successfully acquiring the lock, the lock
 *       is still released in {@link #cleanupResources()} to prevent orphaned locks.</li>
 * </ul>
 *
 * <p>Lock name and owner support {@code {variable}} substitution via
 * {@link com.ts.common.utils.ParamUtils}.
 *
 * @see com.ts.service.LockService
 */
public class LockTask extends AbstractTask {
    private static final Logger logger = LogManager.getLogger(LockTask.class);


    // Polling parameters for lock retry loop
    private static final int DEFAULT_RETRY_INTERVAL = 5000; // Default retry interval in milliseconds (5 seconds)

    private final LockService lockService;
    private String lockName;
    private String lockOwner;
    private String lockAction;

    /**
     * Constructs a new LockTask instance.
     */
    public LockTask() {
        this.lockService = Main.getLockService();
    }

    @Override
    /**
     * Prepares resources and reads task parameters before the main action.
     * Called by {@link AbstractTask#execute} before {@link #action}.
     *
     * @param parameters  merged task parameter map supplied by the workflow engine
     * @throws Exception  if a required parameter is missing or resource initialisation fails
     */
    public void preAction(Map<String, Object> parameters) throws Exception {
        super.preAction(parameters);

        // Retrieve the lock name parameter and apply variable substitution
        lockName = ParamUtils.getLockName(parameters);
        logger.debug("Lock name after variable replacement: {}", lockName);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Lock name after variable replacement: " + lockName);

        // Retrieve the lock owner parameter and apply variable substitution
        lockOwner = ParamUtils.getLockOwner(parameters);
        logger.debug("Lock owner after variable replacement: {}", lockOwner);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Lock owner after variable replacement: " + lockOwner);

        // Retrieve the lock action parameter; defaults to 'acquireLock'
        lockAction = ParamUtils.getParameter(parameters, Constants.LOCK_ACTION, Constants.LOCK_ACQUIRE);
        logger.debug("Lock action: {}", lockAction);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Lock action: " + lockAction);
    }

    @Override
    /**
     * Executes the core task logic.
     * Called by {@link AbstractTask#execute} after {@link #preAction}.
     *
     * @param parameters  merged task parameter map
     * @throws Exception  if the task logic fails
     */
    public void action(Map<String, Object> parameters) throws Exception {
        if (Constants.LOCK_ACQUIRE.equalsIgnoreCase(lockAction)) {
            acquireLockWithRetry();
        } else {
            releaseLock();
        }
    }

    /**
     * Attempts to acquire the distributed lock; retries on failure until the timeout is reached
     */
    private void acquireLockWithRetry() throws InterruptedException {
        logger.info("Attempting to acquire lock: {}, owner: {}. Task: {}, {}, {}", lockName, lockOwner, taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Attempting to acquire lock");

        long startTime = System.currentTimeMillis();
        boolean acquired = false;
        int attempts = 0;

        while (!acquired && !canceled.get()) {
            attempts++;
            acquired = lockService.acquireLock(lockName, lockOwner);

            if (acquired) {
                logger.info("Successfully acquired lock: {}, owner: {} (attempt {}). Task: {}, {}, {}", lockName, lockOwner, attempts, taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Successfully acquired lock");
                break;
            }

            // Check whether the maximum retry duration has been exceeded
            long elapsedTime = System.currentTimeMillis() - startTime;
            if (elapsedTime > timeout) {
                throw new TaskTimeoutException(
                        String.format("Failed to acquire lock: %s, owner: %s after %d attempts and %.2f minutes",
                                lockName, lockOwner, attempts, elapsedTime / 60000.0),
                        null, taskId, taskInstanceId
                );
            }

            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Failed to acquire lock: Retry in " + DEFAULT_RETRY_INTERVAL / 1000 + " seconds (attempt " + attempts + ")");
            logger.info("Failed to acquire lock: {}, owner: {}. Retry in {} seconds (attempt {}). Task: {}, {}, {}",
                    lockName, lockOwner, DEFAULT_RETRY_INTERVAL / 1000, attempts, taskId, taskName, taskInstanceId);

            // Sleep before the next retry attempt
            Thread.sleep(DEFAULT_RETRY_INTERVAL);
        }

        // If canceled, throw — cleanupResources() will release the lock if it was acquired.
        if (canceled.get()) {
            throw new TaskExecutionException("Lock acquisition canceled: " + lockName, null, taskId, taskInstanceId);
        }
    }

    /**
     * Release a distributed lock
     */
    private void releaseLock() {
        logger.info("Releasing lock: {}. Task: {}, {}, {}", lockName, taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Releasing lock");
        boolean released = lockService.releaseLock(lockName);

        if (released) {
            logger.info("Successfully released lock: {}. Task: {}, {}, {}", lockName, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Successfully released lock");
        } else {
            logger.warn("Failed to release lock or lock not found: {}. Task: {}, {}, {}", lockName, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Failed to release lock or lock not found");
        }
        if (canceled.get()) {
            throw new TaskExecutionException("Lock release interrupted by cancellation: " + lockName, null, taskId, taskInstanceId);
        }
    }

    @Override
    /**
     * Cleanup the resources.
     */
    public void cleanupResources() {
        //Thread.interrupted();
        // If the task was cancelled or timed out after acquiring the lock, ensure the lock is released
        if (canceled.get() && Constants.LOCK_ACQUIRE.equalsIgnoreCase(lockAction)) {
            try {
                logger.info("Task canceled. Cleaning up by releasing lock: {}. Task: {}, {}, {}", lockName, taskId, taskName, taskInstanceId);
                lockService.releaseLock(lockName);
            } catch (Exception e) {
                logger.error("Error releasing lock during cleanup: {}. Task: {}, {}, {}", lockName, taskId, taskName, taskInstanceId, e);
            }
        }
        super.cleanupResources();
    }
}