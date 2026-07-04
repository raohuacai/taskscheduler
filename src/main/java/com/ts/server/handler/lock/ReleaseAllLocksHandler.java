/*
 * Description: HTTP handler for DELETE /api/lock/release-all
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
 * Handler for releasing all locks
 */
public class ReleaseAllLocksHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(ReleaseAllLocksHandler.class);
    private final LockService lockService;

    /**
     * Constructor
     */
    public ReleaseAllLocksHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, LockService lockService) {
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
            // Release all locks
            int releasedCount = lockService.releaseAllLocks();

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("releasedCount", releasedCount);

            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            logger.error("Error releasing all locks", e);
            sendErrorResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }
}