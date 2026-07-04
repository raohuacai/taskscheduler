/*
 * Description: Abstract HTTP handler base providing JWT auth, IP restriction, RBAC, and audit logging
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.ts.server.AuditContext;
import com.ts.service.AuditService;
import com.ts.service.AuthService;
import com.ts.service.impl.AuthServiceImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
/**
 * Abstract base for all HTTP API handlers providing JWT authentication,
 * IP restriction, RBAC enforcement, audit logging, and response utilities.
 *
 * <p>The {@link #handle(com.sun.net.httpserver.HttpExchange)} method runs the
 * full request pipeline:
 * <ol>
 *   <li>IP whitelist check (if enabled).</li>
 *   <li>JWT token extraction and validation (paths not in
 *       {@code AUTH_WHITELIST} are blocked without a valid token).</li>
 *   <li>RBAC check via {@link com.ts.service.AuthService#isAuthorized}.</li>
 *   <li>Dispatch to {@link #handleRequest(com.sun.net.httpserver.HttpExchange)}
 *       (implemented by subclasses).</li>
 *   <li>Audit log write via {@link com.ts.service.AuditService}.</li>
 * </ol>
 *
 * <p>Provides {@link #sendJsonResponse} and {@link #sendErrorResponse} helpers
 * so subclasses do not need to deal with raw HTTP exchange plumbing.
 *
 * @see com.ts.service.AuthService
 * @see com.ts.service.AuditService
 */
public abstract class BaseHandler implements HttpHandler {
    private static final Logger logger = LogManager.getLogger(BaseHandler.class);
    /** Shared Gson instance used by subclasses to serialise response objects
     *  and deserialise request bodies. */
    protected final Gson gson;
    private final boolean ipRestrictionEnabled;
    private final Set<String> allowedIps;

    /** Authentication and RBAC service used to validate JWT tokens and
     *  enforce role-based write restrictions on every request. */
    protected final AuthService authService;

    /** Audit service used to persist a structured log entry after each
     *  request is handled; {@code null} for handlers that manage audit
     *  logging themselves (e.g. {@link com.ts.server.handler.auth.LoginHandler}). */
    protected final AuditService auditService;
    private final boolean authenticationRequired;

    // Paths that don't require authentication
    private static final Set<String> AUTH_WHITELIST = Set.of(
            "/api/auth/login"
    );

    /**
     * Constructor with authentication required (used by all existing sub-handlers)
     */
    public BaseHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService) {
        this(gson, ipRestrictionEnabled, allowedIps, authService, null, true);
    }

    /**
     * Constructor with authentication option (used by all existing sub-handlers)
     */
    public BaseHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps,
                       AuthService authService, boolean authenticationRequired) {
        this(gson, ipRestrictionEnabled, allowedIps, authService, null, authenticationRequired);
    }

    /**
     * Constructor with AuditService (used by LoginHandler only, which manages its own audit calls)
     */
    public BaseHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps,
                       AuthService authService, AuditService auditService,
                       boolean authenticationRequired) {
        this.gson = gson;
        this.ipRestrictionEnabled = ipRestrictionEnabled;
        this.allowedIps = allowedIps;
        this.authService = authService;
        this.auditService = auditService;
        this.authenticationRequired = authenticationRequired;
    }

    @Override
    /**
     * Handles.
     *
     * @param exchange  HTTP exchange context
     * @throws IOException  if an error occurs
     */
    public void handle(HttpExchange exchange) throws IOException {
        String auditResult   = "SUCCESS";
        String auditErrorMsg = null;

        try {
            // Check IP restriction
            if (ipRestrictionEnabled) {
                String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
                if (!allowedIps.contains(clientIp)) {
                    logger.warn("Access denied for IP: {}", clientIp);
                    String errorMessage = "Access denied: Your IP is not authorized to access this service";
                    sendErrorResponse(exchange, 403, errorMessage);
                    return;
                }
            }

            // Set CORS headers
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type,Authorization");

            // Handle OPTIONS (CORS preflight)
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // Cache request body as raw bytes so that:
            //   1. Binary handlers (e.g. WorkflowExcelHandler) can retrieve the original bytes
            //      via exchange.getAttribute("cachedRequestBody").
            //   2. Text/JSON handlers can call readRequestBody() which returns a UTF-8 String
            //      derived from the same cached bytes.
            //   3. The audit layer (finally block) also reads from this cache.
            // The InputStream is consumed exactly once here; no downstream code touches it again.
            byte[] rawBodyBytes = readStreamOnceAsBytes(exchange);
            exchange.setAttribute("cachedRequestBody", rawBodyBytes);
            String cachedBody = new String(rawBodyBytes, StandardCharsets.UTF_8);
            AuditContext.setRequestBody(cachedBody);

            // Check authentication if required
            String requestPath = exchange.getRequestURI().getPath();
            logger.info("{}", requestPath);
            if (authenticationRequired && !AUTH_WHITELIST.contains(requestPath)) {
                if (!authenticate(exchange)) {
                    return; // Authentication failed, response already sent
                }
            }

            // Process the request
            handleRequest(exchange);

        } catch (Exception e) {
            logger.error("Error handling request", e);
            auditResult   = "FAILURE";
            auditErrorMsg = e.getMessage();
            sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
        } finally {
            // Write audit log (async, non-blocking, failures silently swallowed).
            // Skip /api/auth/login: LoginHandler calls auditService.logLogin() directly,
            // so the generic audit here would produce a duplicate entry.
            try {
                String path = exchange.getRequestURI().getPath();
                if (staticAuditService != null && !AUTH_WHITELIST.contains(path)) {
                    String operator = (String) exchange.getAttribute("username");
                    String method   = exchange.getRequestMethod();
                    String body     = AuditContext.getRequestBody();
                    String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

                    // Prefer result/errorMsg explicitly set by handler via AuditContext.markFailure()
                    // over the default SUCCESS inferred from no-exception flow.
                    String contextResult = AuditContext.getResult();
                    String contextError  = AuditContext.getErrorMsg();
                    if (contextResult != null) {
                        auditResult   = contextResult;
                        auditErrorMsg = contextError;
                    }

                    staticAuditService.log(operator, method, path, body, clientIp, auditResult, auditErrorMsg);
                }
            } catch (Exception auditEx) {
                // Audit must never affect the main flow
                logger.error("Unexpected error in audit dispatch", auditEx);
            }
            // Clean up thread-locals to prevent memory leaks
            AuditContext.clear();
            AuthServiceImpl.HTTP_METHOD_HOLDER.remove();
            AuthServiceImpl.TOKEN_HOLDER.remove();
            exchange.close();
        }
    }

    /**
     * Static AuditService reference, set once by WorkflowHttpServer at startup.
     * Avoids changing every sub-handler constructor while giving all handlers
     * access to the same shared audit writer.
     */
    private static AuditService staticAuditService;

    /**
     * Called once by WorkflowHttpServer after AuditService is initialised.
     */
    public static void setAuditService(AuditService auditService) {
        BaseHandler.staticAuditService = auditService;
    }

    /**
     * Authenticate the request using JWT token.
     * Sets HTTP method and raw token into AuthServiceImpl thread-locals so that
     * hasPermission() can evaluate role-based write restrictions.
     */
    private boolean authenticate(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(exchange, 401, "Authentication required");
            return false;
        }

        String token  = authHeader.substring(7);
        String method = exchange.getRequestMethod();

        // Populate thread-locals for AuthServiceImpl.hasPermission()
        AuthServiceImpl.HTTP_METHOD_HOLDER.set(method);
        AuthServiceImpl.TOKEN_HOLDER.set(token);

        try {
            if (!authService.validateToken(token)) {
                sendErrorResponse(exchange, 401, "Invalid or expired token");
                return false;
            }

            String username = authService.getUsernameFromToken(token);
            String path     = exchange.getRequestURI().getPath();

            if (!authService.hasPermission(username, path)) {
                logger.warn("Permission denied for user {} on {} {}", username, method, path);
                sendErrorResponse(exchange, 403, "Permission denied: insufficient role for this operation");
                return false;
            }

            exchange.setAttribute("username", username);
            return true;
        } catch (Exception e) {
            logger.error("Authentication error", e);
            sendErrorResponse(exchange, 401, "Authentication failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Handles the authenticated and authorised HTTP request.
     *
     * <p>Called by {@link #handle} after IP-restriction, JWT validation, and RBAC
     * checks have all passed. Subclasses implement this method to execute their
     * specific API logic and send the response via {@link #sendJsonResponse} or
     * {@link #sendErrorResponse}.
     *
     * @param exchange  the HTTP exchange providing request access and response output
     * @throws IOException  if writing the response fails
     */
    protected abstract void handleRequest(HttpExchange exchange) throws IOException;

    /**
     * Send error response
     */
    protected void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", message);
        sendJsonResponse(exchange, statusCode, errorResponse);
    }

    /**
     * Send JSON response
     */
    protected void sendJsonResponse(HttpExchange exchange, int statusCode, Object response) throws IOException {
        String jsonResponse  = gson.toJson(response);
        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    /**
     * Read request body as string.
     * Returns the cached value from AuditContext when available (stream already consumed).
     */
    protected String readRequestBody(HttpExchange exchange) throws IOException {
        // If the body was already cached by handle() (the normal path), return it directly
        String cached = AuditContext.getRequestBody();
        if (cached != null) {
            return cached;
        }
        // Fallback: read from stream (e.g. when called outside the normal lifecycle)
        byte[] bytes = readStreamOnceAsBytes(exchange);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Read the raw InputStream once and return the bytes.
     * Called exactly once per request (from handle()) before the stream is closed.
     * Binary handlers retrieve the cached bytes via exchange.getAttribute("cachedRequestBody");
     * text/JSON handlers call readRequestBody() which converts the same bytes to a UTF-8 String.
     */
    private byte[] readStreamOnceAsBytes(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            return baos.toByteArray();
        }
    }

    /**
     * Get path parameter (the part after the context path)
     */
    protected String getPathParameter(HttpExchange exchange) {
        String path        = exchange.getRequestURI().getPath();
        String contextPath = exchange.getHttpContext().getPath();
        if (path.length() <= contextPath.length()) {
            return null;
        }

        String paramPath = path.substring(contextPath.length());
        if (paramPath.startsWith("/")) {
            paramPath = paramPath.substring(1);
        }
        return paramPath.isEmpty() ? null : paramPath;
    }

    /**
     * Parse URL query parameters (URL-decoded)
     */
    protected Map<String, String> parseQueryParameters(HttpExchange exchange) {
        Map<String, String> queryParams = new HashMap<>();
        String query = exchange.getRequestURI().getQuery();

        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = pair.substring(0, idx);
                    String value = pair.substring(idx + 1);
                    // Handle URL-encoded path segments
                    try {
                        value = java.net.URLDecoder.decode(value, "UTF-8");
                    } catch (Exception e) {
                        logger.warn("Failed to decode query parameter: " + pair, e);
                    }
                    queryParams.put(key, value);
                }
            }
        }

        return queryParams;
    }

    /**
     * Validate that sort fields are in the allowed list (prevents SQL injection)
     */
    protected boolean isValidOrderByColumn(String orderBy) {
        if (orderBy == null || orderBy.isEmpty()) {
            return false;
        }

        // Split sort clause to validate each field; supports multi-field sorting (e.g. TYPE_NAME ASC, CREATION_DATE DESC)
        String[] parts = orderBy.split(",");
        for (String part : parts) {
            String column = part.trim().split("\\s+")[0]; // Extract the column name portion (strip ASC/DESC)

            // Check whether the column name is in the allowed-columns whitelist
            if (!isAllowedColumn(column)) {
                return false;
            }
        }

        return true;
    }

    /** Subclasses override to declare their allowed sort columns */
    protected boolean isAllowedColumn(String column) {
        // Define the whitelist of column names permitted for ORDER BY clauses
        Set<String> allowedColumns = Set.of("TYPE_NAME", "TYPE_GROUP");

        return allowedColumns.contains(column.toUpperCase());
    }

    /**
     * Log request information
     */
    protected void logRequest(HttpExchange exchange, String operation) {
        String username = (String) exchange.getAttribute("username");
        if (username != null) {
            logger.info("{} operation requested by user {} from {}",
                    operation,
                    username,
                    exchange.getRemoteAddress().getAddress().getHostAddress());
        } else {
            logger.info("{} operation requested from {}",
                    operation,
                    exchange.getRemoteAddress().getAddress().getHostAddress());
        }
    }
}