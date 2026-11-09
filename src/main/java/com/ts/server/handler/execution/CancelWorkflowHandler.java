/*
 * Description: HTTP handler for POST /api/workflow/cancel
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.execution;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import com.ts.service.ExecutionService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Handler for cancelling a workflow
 */
public class CancelWorkflowHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(CancelWorkflowHandler.class);
    private final ExecutionService executionService;

    /**
     * Constructor
     */
    public CancelWorkflowHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, ExecutionService executionService) {
        super(gson, ipRestrictionEnabled, allowedIps,authService);
        this.executionService = executionService;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendErrorResponse(exchange, 405, "Method not allowed");
            return;
        }

        try {
            // Get instanceId from URL path or request body
            String instanceId = getPathParameter(exchange);

            if (instanceId == null || instanceId.isEmpty()) {
                String requestBody = readRequestBody(exchange);
                Map<String, Object> request = gson.fromJson(requestBody, Map.class);
                instanceId = (String) request.get("instanceId");
            }

            if (instanceId == null || instanceId.isEmpty()) {
                sendErrorResponse(exchange, 400, "Missing instanceId");
                return;
            }

            // Cancel the workflow
            boolean result = executionService.cancelWorkflow(instanceId);

            // Send response
            Map<String, Object> response = new HashMap<>();
            response.put("status", result ? "success" : "failure");
            response.put("instanceId", instanceId);

            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            logger.error("Error cancelling workflow", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }
}