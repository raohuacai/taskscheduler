/*
 * Description: HTTP handler for DELETE /api/lock/release-by-owner
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
 * Handler for releasing locks by owner
 */
public class ReleaseByOwnerHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(ReleaseByOwnerHandler.class);
    private final LockService lockService;

    /**
     * Constructor
     */
    public ReleaseByOwnerHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, LockService lockService) {
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
            // Get lockOwner from URL path or request body
            String lockOwner = getPathParameter(exchange);

            if (lockOwner == null || lockOwner.isEmpty()) {
                String requestBody = readRequestBody(exchange);
                Map<String, Object> request = gson.fromJson(requestBody, Map.class);
                lockOwner = (String) request.get("lockOwner");
            }

            if (lockOwner == null || lockOwner.isEmpty()) {
                sendErrorResponse(exchange, 400, "Missing lockOwner");
                return;
            }

            // Release locks by owner
            int releasedCount = lockService.releaseAllLocksByOwner(lockOwner);

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("lockOwner", lockOwner);
            response.put("releasedCount", releasedCount);

            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            logger.error("Error releasing locks by owner", e);
            sendErrorResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }
}