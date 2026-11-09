/*
 * Description: HTTP handler for POST /api/workflow/retry-task
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
 * Handler for retrying a task
 */
public class RetryTaskHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(RetryTaskHandler.class);
    private final ExecutionService executionService;

    /**
     * Constructor
     */
    public RetryTaskHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, ExecutionService executionService) {
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
            // Get taskInstanceId from URL path or request body
            String taskInstanceId = getPathParameter(exchange);

            if (taskInstanceId == null || taskInstanceId.isEmpty()) {
                String requestBody = readRequestBody(exchange);
                Map<String, Object> request = gson.fromJson(requestBody, Map.class);
                taskInstanceId = (String) request.get("taskInstanceId");
            }

            if (taskInstanceId == null || taskInstanceId.isEmpty()) {
                sendErrorResponse(exchange, 400, "Missing taskInstanceId");
                return;
            }

            // Retry the task
            boolean result = executionService.retryTask(taskInstanceId);

            // Send response
            Map<String, Object> response = new HashMap<>();
            response.put("status", result ? "success" : "failure");
            response.put("taskInstanceId", taskInstanceId);

            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            logger.error("Error retrying task", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }
}