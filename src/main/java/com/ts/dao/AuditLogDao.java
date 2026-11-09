/*
 * Description: DAO interface for AuditLog persistence
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao;

import com.ts.model.entity.AuditLog;
/**
 * DAO interface for persisting {@link com.ts.model.entity.AuditLog} records.
 */
public interface AuditLogDao {

    boolean saveAuditLog(AuditLog auditLog);
}
