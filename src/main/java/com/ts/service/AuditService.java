/*
 * Description: Contract for asynchronous audit log persistence
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service;
/**
 * Contract for persisting structured audit log entries that record who performed
 * which operation on which resource.
 *
 * <p>Audit records are written asynchronously so that write latency does not
 * affect API response times.
 */
public interface AuditService {

    /**
     * Build and asynchronously persist an audit log entry from request metadata.
     * Called by BaseHandler after handleRequest() completes (success or failure).
     *
     * @param operator    username extracted from JWT (null for unauthenticated routes)
     * @param httpMethod  e.g. "POST"
     * @param requestPath e.g. "/api/definition/workflow"
     * @param requestBody raw request body (will be sanitised before storage)
     * @param clientIp    remote address
     * @param result      "SUCCESS" or "FAILURE"
     * @param errorMsg    exception message when result is FAILURE, otherwise null
     */
    void log(String operator,
             String httpMethod,
             String requestPath,
             String requestBody,
             String clientIp,
             String result,
             String errorMsg);

    /**
     * Convenience method for LoginHandler, which manages its own audit logic
     * (needs to distinguish success vs failure and capture username before JWT is issued).
     *
     * @param username username submitted in the login request
     * @param clientIp remote address
     * @param success  true when authentication succeeded
     * @param errorMsg failure reason (null on success)
     */
    void logLogin(String username, String clientIp, boolean success, String errorMsg);

    /**
     * Graceful shutdown – flush pending audit writes before JVM exits.
     * Called from WorkflowHttpServer.stop().
     */
    void shutdown();
}
