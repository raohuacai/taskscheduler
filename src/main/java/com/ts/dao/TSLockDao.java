/*
 * Description: DAO interface for distributed lock operations on TS_LOCK
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao;

import com.ts.model.entity.TSLock;

import java.util.List;
/**
 * DAO interface for distributed lock persistence on the {@code TS_LOCK} table.
 *
 * <p>Provides acquire (insert) and release (delete) operations.
 * {@link com.ts.service.impl.LockServiceImpl} uses this DAO as the database
 * authority for lock ownership.
 */
public interface TSLockDao {
    /**
     * Acquires a distributed lock by inserting a new lock record into the database
     *
     * @param lock the lock entity to persist
     * @return {@code true} if the lock was successfully acquired (record inserted)
     */
    boolean acquireLock(TSLock lock);

    /**
     * Releases a distributed lock by deleting the matching lock record from the database
     *
     * @param lockName  the name of the lock to release
     * @return the number of rows deleted (1 if successful, 0 if the lock was not found)
     */
    boolean releaseLock(String lockName);

    /**
     * Retrieves all active lock records from the TS_LOCK table
     *
     * @return a list of TSLock records
     */
    List<TSLock> getAllLocks();

    /**
     * Retrieves all locks held by the specified owner
     *
     * @param lockOwner the owner whose locks should be retrieved
     * @return a list of TSLock records
     */
    List<TSLock> getLocksByOwner(String lockOwner);

    /**
     * Retrieves all lock records with the specified lock name
     *
     * @param lockName the name of the lock
     * @return a list of TSLock records
     */
    List<TSLock> getLocksByName(String lockName);

}
