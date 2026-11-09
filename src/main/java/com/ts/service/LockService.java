/*
 * Description: Contract for distributed lock operations backed by TS_LOCK table
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service;

import java.util.List;

import com.github.pagehelper.PageInfo;
import com.ts.model.entity.TSLock;
import com.ts.model.entity.WfDefinition;
/**
 * Contract for distributed lock operations backed by the {@code TS_LOCK} table.
 */
public interface LockService {

    boolean acquireLock(String lockName, String lockOwner);

    boolean releaseLock(String lockName);

    int releaseAllLocks();

    int releaseAllLocksByOwner(String lockOwner);

    List<TSLock> getAllLocks();

    List<TSLock> getLocksByOwner(String lockOwner);

    List<TSLock> getLocksByName(String lockName);
}