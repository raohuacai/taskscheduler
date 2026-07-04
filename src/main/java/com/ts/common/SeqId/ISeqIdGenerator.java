/*
 * Description: Contract for database-backed sequence ID generators
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.common.SeqId;
/**
 * Contract for database sequence ID generators that produce unique numeric IDs
 * for workflow and task instances.
 *
 * <p>Implementations query a database sequence (Oracle) or an auto-increment
 * table (MySQL) to ensure globally unique, monotonically increasing IDs even
 * across multiple JVM instances.
 */
public interface ISeqIdGenerator {
    /**
     * Generate workflow ID
     * Format: 6-digit number, padded with leading zeros if necessary
     *
     * @return Workflow ID
     */
    String generateWfId();

    /**
     * Generate task ID
     * Format: 6-digit number, padded with leading zeros if necessary
     *
     * @return Task ID
     */
    String generateTaskId();

    /**
     * Generate workflow instance ID
     * Format: 7-digit number, padded with leading zeros if necessary
     *
     * @return Workflow instance ID
     */
    String generateWfInstId();

    /**
     * Generate audit log ID
     * Format: 9-digit number, padded with leading zeros if necessary
     *
     * @return Audit log ID
     */
    String generateAuditId();
}
