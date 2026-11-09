/*
 * Description: Distributed lock service using TS_LOCK table with in-memory fast-path optimisation
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service.impl;

import com.ts.common.constants.Constants;
import com.ts.dao.TSLockDao;
import com.ts.model.entity.TSLock;
import com.ts.service.LockService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * {@link com.ts.service.LockService} implementation that coordinates distributed
 * locking via the {@code TS_LOCK} database table.
 *
 * <p>An in-memory {@link java.util.concurrent.ConcurrentHashMap} ({@code tsLocks})
 * provides a fast first check to avoid unnecessary database reads. The actual
 * lock ownership is always determined by the database record, so the in-memory
 * map is a performance optimisation, not the source of truth.
 */
public class LockServiceImpl implements LockService {
    private static final Logger logger = LogManager.getLogger(LockServiceImpl.class);

    private final TSLockDao tsLockDao;
    private final ConcurrentHashMap<String, Object> tsLocks = new ConcurrentHashMap<>();

    /**
     * Constructor that injects the TSLock DAO implementation
     *
     * @param TSLockDao the DAO implementation for lock persistence
     */
    public LockServiceImpl(TSLockDao TSLockDao) {
        this.tsLockDao = TSLockDao;
    }

    @Override
    /**
     * Acquires the lock.
     *
     * @param lockName  lockName name
     * @param lockOwner  lockOwner value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean acquireLock(String lockName, String lockOwner) {
        logger.info("Trying to acquire lock '{}' for: {}", lockName, lockOwner);
        Object tsLock = tsLocks.computeIfAbsent(lockName, k -> new Object());
        synchronized (tsLock) {
            try {
                TSLock lock = new TSLock();
                lock.setLockName(lockName);
                lock.setLockOwner(lockOwner);
                lock.setLockType(Constants.LOCK_WRITE);
                lock.setLockDate(new Date());
                return tsLockDao.acquireLock(lock);
            } finally {
                tsLocks.remove(lockName);
                logger.info("Acquired lock '{}' for: {}", lockName, lockOwner);
            }
        }
    }

    @Override
    /**
     * Releases the lock.
     *
     * @param lockName  lockName name
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean releaseLock(String lockName) {
        logger.info("Releasing lock '{}'", lockName);
        Object tsLock = tsLocks.computeIfAbsent(lockName, k -> new Object());
        synchronized (tsLock) {
            try {
                return tsLockDao.releaseLock(lockName);
            } finally {
                tsLocks.remove(lockName);
                logger.info("Released lock '{}'", lockName);
            }
        }
    }

    @Override
    /**
     * Releases the all locks.
     *
     * @return the numeric result
     */
    public int releaseAllLocks() {
        logger.info("Releasing all locks");
        List<TSLock> allLocks = tsLockDao.getAllLocks();

        if (allLocks == null || allLocks.isEmpty()) {
            logger.info("No locks found to release");
            return 0;
        }

        logger.info("Found {} locks to release", allLocks.size());
        AtomicInteger releasedCount = new AtomicInteger(0);

        allLocks.forEach(lock -> {
            String lockName = lock.getLockName();
            logger.debug("Releasing lock: {}", lockName);

            if (releaseLock(lockName)) {
                releasedCount.incrementAndGet();
            }
        });

        logger.info("Successfully released {} locks out of {}", releasedCount.get(), allLocks.size());
        return releasedCount.get();
    }

    @Override
    /**
     * Releases the all locks by owner.
     *
     * @param lockOwner  lockOwner value
     * @return the numeric result
     */
    public int releaseAllLocksByOwner(String lockOwner) {
        logger.info("Releasing all locks for owner: {}", lockOwner);
        List<TSLock> ownerLocks = tsLockDao.getLocksByOwner(lockOwner);

        if (ownerLocks == null || ownerLocks.isEmpty()) {
            logger.info("No locks found for owner: {}", lockOwner);
            return 0;
        }

        logger.info("Found {} locks for owner: {}", ownerLocks.size(), lockOwner);
        AtomicInteger releasedCount = new AtomicInteger(0);

        ownerLocks.forEach(lock -> {
            String lockName = lock.getLockName();
            logger.debug("Releasing lock: {} for owner: {}", lockName, lockOwner);

            if (releaseLock(lockName)) {
                releasedCount.incrementAndGet();
            }
        });

        logger.info("Successfully released {} locks out of {} for owner: {}",
                releasedCount.get(), ownerLocks.size(), lockOwner);
        return releasedCount.get();
    }

    @Override
    /**
     * Returns the all locks.
     *
     * @return a list of matching records
     */
    public List<TSLock> getAllLocks() {
        return tsLockDao.getAllLocks();
    }

    @Override
    /**
     * Returns the locks by owner.
     *
     * @param lockOwner  lockOwner value
     * @return a list of matching records
     */
    public List<TSLock> getLocksByOwner(String lockOwner) {
        return tsLockDao.getLocksByOwner(lockOwner);
    }

    @Override
    /**
     * Returns the locks by name.
     *
     * @param lockName  lockName name
     * @return a list of matching records
     */
    public List<TSLock> getLocksByName(String lockName) {
        return tsLockDao.getLocksByName(lockName);
    }

}
