/*
 * Description: HTTP handler for POST /api/auth/login, issuing JWT tokens on successful authentication
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.auth;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuditService;
import com.ts.service.AuthService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
/**
 * HTTP handler for the {@code POST /api/auth/login} endpoint.
 *
 * <p>Validates credentials via {@link com.ts.service.AuthService#login} and
 * returns a JWT token on success. This path is exempt from the normal JWT
 * pre-check in {@link BaseHandler} (listed in {@code AUTH_WHITELIST}).
 */
public class LoginHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(LoginHandler.class);

    /**
     * Constructor
     *
     * @param gson The Gson instance for JSON serialization/deserialization
     * @param ipRestrictionEnabled Whether IP restriction is enabled
     * @param allowedIps Set of allowed IPs if restriction is enabled
     * @param authService The authentication service
     * @param auditService The audit log service
     * @param authenticationRequired Whether authentication is required for this handler
     */
    public LoginHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps,
                        AuthService authService, AuditService auditService,
                        boolean authenticationRequired) {
        super(gson, ipRestrictionEnabled, allowedIps, authService, auditService, authenticationRequired);
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendErrorResponse(exchange, 405, "Method not allowed");
            return;
        }

        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        String username = null;

        try {
            String requestBody = readRequestBody(exchange);
            Map<String, String> credentials = gson.fromJson(requestBody, Map.class);

            username = credentials.get("username");
            String password = credentials.get("password");

            if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
                logger.warn("Login attempt with missing credentials from IP: {}",
                        exchange.getRemoteAddress().getAddress().getHostAddress());
                // Audit: login failure due to missing credentials
                auditService.logLogin(username, clientIp, false, "Username or password is empty");
                sendErrorResponse(exchange, 400, "Username and password are required");
                return;
            }

            // Authenticate – returns a JWT token containing the role claim
            String token = authService.authenticate(username, password);

            if (token == null) {
                logger.warn("Failed login attempt for user: {} from IP: {}",
                        username,
                        exchange.getRemoteAddress().getAddress().getHostAddress());
                // Audit: login failure due to invalid credentials
                auditService.logLogin(username, clientIp, false, "Invalid username or password");
                sendErrorResponse(exchange, 401, "Invalid username or password");
                return;
            }

            // Extract role from the freshly-issued token so the frontend can
            // store it in localStorage without parsing the JWT itself
            String role = authService.getRoleFromToken(token);

            Map<String, Object> response = new HashMap<>();
            response.put("token",    token);
            response.put("username", username);
            response.put("role",     role);

            logger.info("Successful login for user: {} (role={}) from IP: {}",
                    username, role,
                    exchange.getRemoteAddress().getAddress().getHostAddress());
            // Audit: login success
            auditService.logLogin(username, clientIp, true, null);

            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            logger.error("Error during login", e);
            // Audit: login failure due to unexpected error
            auditService.logLogin(username, clientIp, false, e.getMessage());
            sendErrorResponse(exchange, 500, "Login error: " + e.getMessage());
        }
    }
}
