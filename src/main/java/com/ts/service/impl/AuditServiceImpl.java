/*
 * Description: Async audit log service dispatching writes to a single-thread executor
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service.impl;

import com.ts.model.entity.AuditLog;
import com.ts.config.AuditRouteConfig;
import com.ts.common.utils.AuditUtils;
import com.ts.common.SeqId.SeqIdGenerator;
import com.ts.dao.AuditLogDao;
import com.ts.service.AuditService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
/**
 * Asynchronous implementation of {@link com.ts.service.AuditService} that
 * persists {@link com.ts.model.entity.AuditLog} records via
 * {@link com.ts.dao.AuditLogDao}.
 *
 * <p>Audit writes are dispatched to a single-thread executor so they do not
 * block the HTTP handler thread. Failures are logged but do not propagate to
 * callers.
 */
public class AuditServiceImpl implements AuditService {

    private static final Logger logger = LogManager.getLogger(AuditServiceImpl.class);

    private final AuditLogDao auditLogDao;

    /**
     * Daemon single-thread executor dedicated to audit writes.
     * Using a single thread avoids contention and keeps insert order deterministic.
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "audit-writer");
        t.setDaemon(true);   // Does not prevent JVM shutdown
        return t;
    });

    /**
     * Constructs a new AuditServiceImpl instance.
     *
     * @param auditLogDao  DAO dependency
     */
    public AuditServiceImpl(AuditLogDao auditLogDao) {
        this.auditLogDao = auditLogDao;
    }

    @Override
    /**
     * Logs.
     *
     * @param operator  operator value
     * @param httpMethod  httpMethod value
     * @param requestPath  file-system path
     * @param requestBody  requestBody value
     * @param clientIp  clientIp value
     * @param result  result value
     * @param errorMsg  descriptive message
     */
    public void log(String operator,
                    String httpMethod,
                    String requestPath,
                    String requestBody,
                    String clientIp,
                    String result,
                    String errorMsg) {
        // Only audit write operations; skip GETs and OPTIONS
        if (shouldSkip(httpMethod)) return;

        AuditRouteConfig.AuditMeta meta = AuditRouteConfig.resolve(httpMethod, requestPath);
        if (meta == null) return;   // Not a registered auditable route

        String operation = AuditUtils.resolveOperation(meta.operation, httpMethod, requestPath, requestBody);

        AuditLog auditLog = new AuditLog();
        auditLog.setAuditId(SeqIdGenerator.generateAuditId());
        auditLog.setOperator(operator != null ? operator : "ANONYMOUS");
        auditLog.setOperation(operation);
        auditLog.setModule(meta.module);
        auditLog.setHttpMethod(httpMethod);
        auditLog.setRequestPath(requestPath);
        auditLog.setDataJson(AuditUtils.sanitiseJson(requestBody));
        auditLog.setResult(result);
        auditLog.setErrorMsg(AuditUtils.trimErrorMsg(errorMsg));
        auditLog.setClientIp(clientIp);
        auditLog.setOperateTime(new Date());

        writeAsync(auditLog);
    }

    @Override
    /**
     * Logs the login.
     *
     * @param username  username name
     * @param clientIp  clientIp value
     * @param success  success value
     * @param errorMsg  descriptive message
     */
    public void logLogin(String username, String clientIp, boolean success, String errorMsg) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAuditId(SeqIdGenerator.generateAuditId());
        auditLog.setOperator(username != null ? username : "UNKNOWN");
        auditLog.setOperation(success ? "LOGIN_SUCCESS" : "LOGIN_FAILURE");
        auditLog.setModule("AUTH");
        auditLog.setHttpMethod("POST");
        auditLog.setRequestPath("/api/auth/login");
        auditLog.setDataJson("{\"username\":\"" + AuditUtils.escapeJson(username) + "\"}");
        auditLog.setResult(success ? "SUCCESS" : "FAILURE");
        auditLog.setErrorMsg(AuditUtils.trimErrorMsg(errorMsg));
        auditLog.setClientIp(clientIp);
        auditLog.setOperateTime(new Date());

        writeAsync(auditLog);
    }

    @Override
    /**
     * Shuts down.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void writeAsync(AuditLog auditLog) {
        executor.submit(() -> {
            // Audit failures must never propagate to callers
            auditLogDao.saveAuditLog(auditLog);
        });
    }

    /**
     * Returns true for request types that do not require auditing.
     */
    private boolean shouldSkip(String method) {
        if ("GET".equalsIgnoreCase(method))     return true;
        if ("OPTIONS".equalsIgnoreCase(method)) return true;
        return false;
    }
}
