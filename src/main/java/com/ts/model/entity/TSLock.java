/*
 * Description: Entity for TS_LOCK distributed lock records
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.model.entity;

import java.util.Date;
/**
 * Entity representing a distributed lock record in the {@code TS_LOCK} table.
 *
 * <p>A row exists for as long as the lock is held; deletion constitutes release.
 */
public class TSLock {
    /**
     * Unique name identifying the lock resource
     */
    private String lockName;

    /**
     * Owner of the lock, typically the workflow instance ID or task instance ID
     */
    private String lockOwner;

    /**
     * Lock type (e.g. READ or WRITE)
     */
    private String lockType;

    /**
     * Timestamp when the lock was acquired
     */
    private Date lockDate;

    
    public TSLock() {
    }

    /**
     * Constructs a fully-populated TSLock with all fields set.
     *
     * @param lockName  unique name identifying the locked resource
     * @param lockOwner owner identity (typically a workflow instance ID)
     * @param lockType  lock category (e.g. READ or WRITE)
     * @param lockDate  timestamp when the lock was acquired
     */
    public TSLock(String lockName, String lockOwner, String lockType, Date lockDate) {
        this.lockName = lockName;
        this.lockOwner = lockOwner;
        this.lockType = lockType;
        this.lockDate = lockDate;
    }

    /**
     * Returns the lock name.
     *
     * @return the result as a String
     */
    public String getLockName() {
        return lockName;
    }

    /**
     * Sets the lock name.
     *
     * @param lockName  lockName name
     */
    public void setLockName(String lockName) {
        this.lockName = lockName;
    }

    /**
     * Returns the lock owner.
     *
     * @return the result as a String
     */
    public String getLockOwner() {
        return lockOwner;
    }

    /**
     * Sets the lock owner.
     *
     * @param lockOwner  lockOwner value
     */
    public void setLockOwner(String lockOwner) {
        this.lockOwner = lockOwner;
    }

    /**
     * Returns the lock type.
     *
     * @return the result as a String
     */
    public String getLockType() {
        return lockType;
    }

    /**
     * Sets the lock type.
     *
     * @param lockType  type discriminator
     */
    public void setLockType(String lockType) {
        this.lockType = lockType;
    }

    /**
     * Returns the lock date.
     *
     * @return the requested lockdate object, or null if not found
     */
    public Date getLockDate() {
        return lockDate;
    }

    /**
     * Sets the lock date.
     *
     * @param lockDate  date/time value
     */
    public void setLockDate(Date lockDate) {
        this.lockDate = lockDate;
    }

    /**
     * To the string.
     *
     * @return the result as a String
     */
    public String toString() {
        return "TSLock{" +
                "lockName='" + lockName + '\'' +
                ", lockOwner='" + lockOwner + '\'' +
                ", lockType='" + lockType + '\'' +
                ", lockDate=" + lockDate +
                '}';
    }
}
