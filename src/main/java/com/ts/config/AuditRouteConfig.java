/*
 * Description: Static registry mapping HTTP method+path to audit metadata and sensitive field mask list
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
/**
 * Static registry mapping (HTTP method, request path) pairs to
 * {@code (OPERATION, MODULE)} audit metadata, and declaring the set of JSON
 * field names that must be masked before persisting audit data.
 *
 * <p>Path matching applies exact match first, then wildcard-suffix match
 * ({@code /api/tasktype/*} matches {@code /api/tasktype/123}). Paths not
 * registered here produce a {@code null} {@link AuditMeta}, meaning the request
 * is not audited (e.g., read-only GET operations).
 *
 * <p>Sensitive fields (passwords, tokens) listed in {@code SENSITIVE_KEYS} are
 * replaced with {@code "***"} in the persisted DATA_JSON column.
 */
public final class AuditRouteConfig {

    // ── Inner value type ──────────────────────────────────────────────────────

    /**
     * Immutable value type pairing an operation name (e.g. {@code CREATE_WORKFLOW})
     * with a module name (e.g. {@code WORKFLOW_DEF}) for use in audit log records.
     * Instances are created by the static {@code reg()} initialiser only.
     */
    public static class AuditMeta {
        /** The operation name for this audit route. */
        public final String operation;
        /** The module name for this audit route. */
        public final String module;

        AuditMeta(String operation, String module) {
            this.operation = operation;
            this.module    = module;
        }
    }

    // ── Route table:  "METHOD:PATH" → AuditMeta ──────────────────────────────

    private static final Map<String, AuditMeta> ROUTE_MAP = new HashMap<>();

    static {
        // ── Auth ──────────────────────────────────────────────────────────────
        // Login is handled directly in LoginHandler; entry here for reference only.
        reg("POST",   "/api/auth/login",                    "LOGIN",                 "AUTH");

        // ── Global Parameters ─────────────────────────────────────────────────
        reg("PUT",    "/api/params/global",                 "UPDATE_GLOBAL_PARAMS",  "GLOBAL_PARAMS");

        // ── Task Types ────────────────────────────────────────────────────────
        reg("POST",   "/api/tasktype",                      "CREATE_TASK_TYPE",      "TASK_TYPE");
        reg("PUT",    "/api/tasktype",                      "UPDATE_TASK_TYPE",      "TASK_TYPE");
        reg("DELETE", "/api/tasktype/*",                    "DELETE_TASK_TYPE",      "TASK_TYPE");

        // ── Workflow Definitions ──────────────────────────────────────────────
        reg("POST",   "/api/definition/workflow",           "CREATE_WORKFLOW_DEF",   "WORKFLOW_DEF");
        reg("PUT",    "/api/definition/workflow",           "UPDATE_WORKFLOW_DEF",   "WORKFLOW_DEF");
        reg("DELETE", "/api/definition/workflow/*",         "DELETE_WORKFLOW_DEF",   "WORKFLOW_DEF");

        // ── Task Definitions ──────────────────────────────────────────────────
        reg("POST",   "/api/definition/task",               "CREATE_TASK_DEF",       "WORKFLOW_DEF");
        reg("PUT",    "/api/definition/task",               "UPDATE_TASK_DEF",       "WORKFLOW_DEF");
        reg("DELETE", "/api/definition/task/*",             "DELETE_TASK_DEF",       "WORKFLOW_DEF");

        // ── Workflow DAG ──────────────────────────────────────────────────────
        reg("POST",   "/api/definition/dag",                "SAVE_WORKFLOW_DAG",     "WORKFLOW_DAG");
        reg("PUT",    "/api/definition/dag",                "SAVE_WORKFLOW_DAG",     "WORKFLOW_DAG");

        // ── Excel Import ──────────────────────────────────────────────────────
        reg("POST",   "/api/definition/excel/import",       "IMPORT_WORKFLOW_EXCEL", "WORKFLOW_DEF");

        // ── Scheduler ─────────────────────────────────────────────────────────
        // UPDATE_SCHEDULER may be promoted to TRIGGER_SCHEDULER by AuditUtils.resolveOperation()
        reg("POST",   "/api/scheduler/schedule",            "CREATE_SCHEDULER",      "SCHEDULER");
        reg("PUT",    "/api/scheduler/schedule",            "UPDATE_SCHEDULER",      "SCHEDULER");
        reg("DELETE", "/api/scheduler/schedule/*",          "DELETE_SCHEDULER",      "SCHEDULER");

        // ── Monitoring / Execution ────────────────────────────────────────────
        reg("POST",   "/api/workflow/start",                "START_WORKFLOW",        "MONITORING");
        reg("POST",   "/api/workflow/recover",              "RECOVER_WORKFLOW",      "MONITORING");
        reg("POST",   "/api/workflow/cancel",               "CANCEL_WORKFLOW",       "MONITORING");
        reg("POST",   "/api/workflow/canceltask",           "CANCEL_TASK",           "MONITORING");
        reg("POST",   "/api/workflow/skip",                 "SKIP_TASK",             "MONITORING");
        reg("POST",   "/api/workflow/retry",                "RETRY_TASK",            "MONITORING");

        // ── Locks ─────────────────────────────────────────────────────────────
        reg("POST",   "/api/lock/release",                  "RELEASE_LOCK",          "LOCK");
        reg("POST",   "/api/lock/release/*",                "RELEASE_LOCK",          "LOCK");
        reg("POST",   "/api/lock/releaseAll",               "RELEASE_ALL_LOCKS",     "LOCK");
        reg("POST",   "/api/lock/releaseByOwner",           "RELEASE_LOCK_BY_OWNER", "LOCK");

        // ── Users ─────────────────────────────────────────────────────────────
        reg("POST",   "/api/users",                         "CREATE_USER",           "USER_MGMT");
        reg("PUT",    "/api/users",                         "UPDATE_USER",           "USER_MGMT");
        reg("DELETE", "/api/users/*",                       "DELETE_USER",           "USER_MGMT");
        reg("POST",   "/api/users/resetpwd",                "RESET_PASSWORD",        "USER_MGMT");
        reg("POST",   "/api/users/changepwd",               "CHANGE_PASSWORD",       "USER_MGMT");

        // ── Event ─────────────────────────────────────────────────────────────
        // Add this line inside the static { } block of AuditRouteConfig.java,
        // after the Locks section.
        reg("POST",   "/api/event/trigger",                 "TRIGGER_EVENT",         "EVENT");
    }

    private static void reg(String method, String path, String operation, String module) {
        ROUTE_MAP.put(key(method, path), new AuditMeta(operation, module));
    }

    private static String key(String method, String path) {
        return method.toUpperCase() + ":" + path;
    }

    // ── Sensitive JSON field keys to mask before persisting DATA_JSON ─────────

    private static final Set<String> SENSITIVE_KEYS = new HashSet<>();

    static {
        SENSITIVE_KEYS.add("password");
        SENSITIVE_KEYS.add("newPassword");
        SENSITIVE_KEYS.add("oldPassword");
        SENSITIVE_KEYS.add("confirmPassword");
        SENSITIVE_KEYS.add("http_auth_password");   // Constants.HTTP_AUTH_PASSWORD
        SENSITIVE_KEYS.add("http_auth_token");      // Constants.HTTP_AUTH_TOKEN
    }

    /**
     * Returns the sensitive keys.
     *
     * @return the result as a String
     */
    public static Set<String> getSensitiveKeys() {
        return SENSITIVE_KEYS;
    }

    // ── Route lookup ──────────────────────────────────────────────────────────

    /**
     * Resolve (HTTP method, request path) to AuditMeta.
     * Returns null when the route is not registered (e.g. read-only GET).
     */
    public static AuditMeta resolve(String method, String path) {
        if (method == null || path == null) return null;

        // 1. Exact match
        AuditMeta meta = ROUTE_MAP.get(key(method, path));
        if (meta != null) return meta;

        // 2. Wildcard suffix match – strip trailing path segments one at a time
        //    e.g. DELETE /api/tasktype/123  →  matches  DELETE /api/tasktype/*
        String candidate = path;
        while (candidate.contains("/")) {
            int lastSlash = candidate.lastIndexOf('/');
            candidate = candidate.substring(0, lastSlash);
            meta = ROUTE_MAP.get(key(method, candidate + "/*"));
            if (meta != null) return meta;
        }

        return null;
    }

    private AuditRouteConfig() { }
}
