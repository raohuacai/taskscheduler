/*
 * Description: ThreadLocal container for audit metadata populated by BaseHandler per HTTP request
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server;
/**
 * Thread-local container for the audit context of the current HTTP request.
 *
 * <p>Populated by {@link BaseHandler} from the request JWT and path, then
 * read by {@link com.ts.service.AuditService} after the handler completes.
 * Using a {@link ThreadLocal} avoids passing audit metadata through every
 * method call in the handler chain.
 */
public final class AuditContext {

    private static final ThreadLocal<String>  BODY_HOLDER   = new ThreadLocal<>();
    private static final ThreadLocal<String>  RESULT_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String>  ERROR_HOLDER  = new ThreadLocal<>();

    private AuditContext() { }

    // ── Cached request body (HttpExchange stream can only be read once) ────────

    /**
     * Stores the raw request body for the current thread's audit context.
     * Called by {@link com.ts.server.handler.BaseHandler} before dispatching to the handler.
     *
     * @param body  the raw HTTP request body string; may be {@code null} for bodyless requests
     */
    public static void   setRequestBody(String body) { BODY_HOLDER.set(body); }

    /**
     * Returns the raw request body stored for the current thread, or {@code null}
     * if none was set (e.g. for bodyless GET requests).
     *
     * @return the request body string, or {@code null}
     */
    public static String getRequestBody()            { return BODY_HOLDER.get(); }

    // ── Explicit result override ───────────────────────────────────────────────
    // Handlers that swallow exceptions internally can call markFailure() so the
    // finally block in BaseHandler picks up the correct result instead of SUCCESS.

    /**
     * Mark the current request as failed with the given error message.
     * Call this inside a handler method when returning an error response without
     * rethrowing the exception (e.g. sendErrorResponse(exchange, 400, msg)).
     */
    public static void markFailure(String errorMsg) {
        RESULT_HOLDER.set("FAILURE");
        ERROR_HOLDER.set(errorMsg);
    }

    /** Returns the explicitly set result, or null if not set (BaseHandler uses "SUCCESS" as default). */
    /**
     * Returns the explicitly set result string for the current thread,
     * or {@code null} if not set (BaseHandler defaults to {@code "SUCCESS"}).
     *
     * @return the result string, or {@code null}
     */
    public static String getResult()   { return RESULT_HOLDER.get(); }

    /** Returns the explicitly set error message, or null if not set. */
    public static String getErrorMsg() { return ERROR_HOLDER.get(); }

    // ── Cleanup (must be called in finally block) ─────────────────────────────

    /**
     * Removes all thread-local audit values for the current thread.
     *
     * <p>Must be called in the {@code finally} block of
     * {@link com.ts.server.handler.BaseHandler#handle} to prevent
     * {@link ThreadLocal} leaks in pooled thread environments.
     */
    public static void clear() {
        BODY_HOLDER.remove();
        RESULT_HOLDER.remove();
        ERROR_HOLDER.remove();
    }
}
