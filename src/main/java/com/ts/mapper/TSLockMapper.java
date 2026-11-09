/*
 * Description: MyBatis mapper for TS_LOCK distributed lock table
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.mapper;

import com.ts.model.entity.TSLock;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * MyBatis mapper interface for distributed lock CRUD on the {@code TS_LOCK} table.
 */
public interface TSLockMapper {

    /**
     * Inserts a new lock record into the TS_LOCK table
     * @param lock the lock entity to persist
     * @return the number of rows affected (1 on success)
     */
    int insertLock(TSLock lock);

    /**
     * Deletes a lock record identified by lock name and owner
     * @param lockName the name of the lock
     * @return the number of rows affected (1 on success)
     */
    int deleteLock(@Param("lockName") String lockName);

    /**
     * Deletes all lock records from the TS_LOCK table
     * @return the number of rows affected (1 on success)
     */
    int deleteAllLocks();

    /**
     * Deletes all lock records held by the specified owner
     * @param lockOwner the owner whose locks should be retrieved
     * @return the number of rows affected (1 on success)
     */
    int deleteAllLocksByOwner(@Param("lockOwner") String lockOwner);

    /**
     * Retrieves all active lock records from the TS_LOCK table
     * @return a list of TSLock records
     */
    List<TSLock> selectAllLocks();

    /**
     * Retrieves all lock records held by the specified owner
     * @param lockOwner the owner whose locks should be retrieved
     * @return a list of TSLock records
     */
    List<TSLock> selectLocksByOwner(@Param("lockOwner") String lockOwner);

    /**
     * Retrieves all lock records with the specified lock name
     * @param lockName the name of the lock
     * @return a list of TSLock records
     */
    List<TSLock> selectLocksByName(@Param("lockName") String lockName);

    /**
     * Checks whether a lock with the specified name exists
     * @param lockName the name of the lock
     * @return the count of matching lock records (0 or 1)
     */
    int checkLockExists(@Param("lockName") String lockName);
}
