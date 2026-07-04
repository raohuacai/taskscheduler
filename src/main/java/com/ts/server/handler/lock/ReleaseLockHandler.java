/*
 * Description: HTTP handler for DELETE /api/lock/release
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.lock;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import com.ts.service.LockService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Handler for releasing a lock by name
 */
public class ReleaseLockHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(ReleaseLockHandler.class);
    private final LockService lockService;

    /**
     * Constructor
     */
    public ReleaseLockHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, LockService lockService) {
        super(gson, ipRestrictionEnabled, allowedIps,authService);
        this.lockService = lockService;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendErrorResponse(exchange, 405, "Method not allowed");
            return;
        }

        try {
            // Get lockName from URL path or request body
            String lockName = getPathParameter(exchange);

            if (lockName == null || lockName.isEmpty()) {
                String requestBody = readRequestBody(exchange);
                Map<String, Object> request = gson.fromJson(requestBody, Map.class);
                lockName = (String) request.get("lockName");
            }

            if (lockName == null || lockName.isEmpty()) {
                sendErrorResponse(exchange, 400, "Missing lockName");
                return;
            }

            // Release the lock
            boolean result = lockService.releaseLock(lockName);

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("status", result ? "success" : "failure");
            response.put("lockName", lockName);

            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            logger.error("Error releasing lock", e);
            sendErrorResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }
}