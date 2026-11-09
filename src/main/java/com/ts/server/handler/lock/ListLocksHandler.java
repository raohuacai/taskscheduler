/*
 * Description: HTTP handler for GET /api/lock/list
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.lock;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.model.entity.TSLock;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import com.ts.service.LockService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handler for listing locks
 */
public class ListLocksHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(ListLocksHandler.class);
    private final LockService lockService;

    /**
     * Constructor
     */
    public ListLocksHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, LockService lockService) {
        super(gson, ipRestrictionEnabled, allowedIps,authService);
        this.lockService = lockService;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendErrorResponse(exchange, 405, "Method not allowed");
            return;
        }

        try {
            Map<String, String> queryParams = parseQueryParameters(exchange);
            String lockOwner = queryParams.get("owner");
            String lockName = queryParams.get("name");

            List<TSLock> locks;

            if (lockOwner != null && !lockOwner.isEmpty()) {
                // Get locks by owner
                locks = lockService.getLocksByOwner(lockOwner);
            } else if (lockName != null && !lockName.isEmpty()) {
                // Get locks by name
                locks = lockService.getLocksByName(lockName);
            } else {
                // Get all locks
                locks = lockService.getAllLocks();
            }

            sendJsonResponse(exchange, 200, locks);
        } catch (Exception e) {
            logger.error("Error listing locks", e);
            sendErrorResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }
}