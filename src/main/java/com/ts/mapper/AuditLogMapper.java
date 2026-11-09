/*
 * Description: MyBatis mapper for TS_AUDIT_LOG table
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.mapper;

import com.ts.model.entity.AuditLog;
/**
 * MyBatis mapper interface for {@code TS_AUDIT_LOG} table operations.
 */
public interface AuditLogMapper {

    int insert(AuditLog auditLog);
}
