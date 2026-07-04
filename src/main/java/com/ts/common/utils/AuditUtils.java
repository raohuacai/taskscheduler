/*
 * Description: Utility class extracting audit metadata from HTTP exchanges for BaseHandler
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.common.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ts.config.AuditRouteConfig;

import java.util.Map;
import java.util.Set;
/**
 * Utility class providing helpers for extracting audit metadata from HTTP exchanges.
 *
 * <p>Used by {@link com.ts.server.handler.BaseHandler} to populate the
 * {@link com.ts.server.AuditContext} thread-local before delegating to the
 * concrete handler.
 */
public final class AuditUtils {

    /** Maximum characters stored in DATA_JSON (guards against CLOB / TEXT overflow). */
    private static final int MAX_DATA_JSON_LEN = 65000;

    /** Maximum characters stored in ERROR_MSG column. */
    private static final int MAX_ERROR_MSG_LEN = 1000;

    private AuditUtils() { }

    // ── JSON sanitisation ─────────────────────────────────────────────────────

    /**
     * Mask sensitive fields in a JSON string and truncate to {@value #MAX_DATA_JSON_LEN} chars.
     * Non-JSON input is returned as-is (still truncated).
     *
     * @param json raw request body
     * @return sanitised string, or null when input is null / blank
     */
    public static String sanitiseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonElement el = JsonParser.parseString(json);
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject().deepCopy();
                maskSensitiveFields(obj);
                json = new Gson().toJson(obj);
            }
        } catch (Exception ignored) {
            // Not valid JSON – store raw value, still subject to length truncation
        }
        return trimToLength(json, MAX_DATA_JSON_LEN);
    }

    /**
     * Recursively replace values of sensitive keys with "******".
     */
    private static void maskSensitiveFields(JsonObject obj) {
        Set<String> sensitiveKeys = AuditRouteConfig.getSensitiveKeys();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (sensitiveKeys.contains(entry.getKey())) {
                obj.addProperty(entry.getKey(), "******");
            } else if (entry.getValue().isJsonObject()) {
                maskSensitiveFields(entry.getValue().getAsJsonObject());
            }
        }
    }

    // ── Operation name resolution ─────────────────────────────────────────────

    /**
     * Fine-tune the operation name for routes that serve multiple logical actions
     * on the same HTTP method + path.
     *
     * <p>Currently handled: Scheduler PUT with {@code "action":"trigger"} in the
     * request body is promoted from UPDATE_SCHEDULER to TRIGGER_SCHEDULER.
     *
     * @param defaultOp   operation resolved from AuditRouteConfig
     * @param httpMethod  HTTP method of the request
     * @param requestPath request path
     * @param requestBody raw request body (may be null)
     * @return refined operation name
     */
    public static String resolveOperation(String defaultOp,
                                          String httpMethod,
                                          String requestPath,
                                          String requestBody) {
        if ("UPDATE_SCHEDULER".equals(defaultOp)
                && "PUT".equalsIgnoreCase(httpMethod)
                && requestBody != null
                && requestBody.contains("\"trigger\"")) {
            try {
                JsonObject body   = JsonParser.parseString(requestBody).getAsJsonObject();
                JsonElement action = body.get("action");
                if (action != null && "trigger".equalsIgnoreCase(action.getAsString())) {
                    return "TRIGGER_SCHEDULER";
                }
            } catch (Exception ignored) {
                // Fall through to default
            }
        }
        return defaultOp;
    }

    // ── String helpers ────────────────────────────────────────────────────────

    /**
     * Truncate a string to {@value #MAX_ERROR_MSG_LEN} characters (for ERROR_MSG column).
     */
    public static String trimErrorMsg(String msg) {
        return trimToLength(msg, MAX_ERROR_MSG_LEN);
    }

    /**
     * Truncate a string to the given maximum length, appending "[TRUNCATED]" when trimmed.
     */
    public static String trimToLength(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...[TRUNCATED]";
    }

    /**
     * Escape a string for safe embedding inside a JSON string value.
     */
    public static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
