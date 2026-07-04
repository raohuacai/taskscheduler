/*
 * Description: HTTP handler for GET /api/workflow/info returning single workflow instance details
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.execution;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.model.entity.WfInstance;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import com.ts.service.ExecutionService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Set;

/**
 * Handler for getting workflow info
 */
public class WorkflowInfoHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(WorkflowInfoHandler.class);
    private final ExecutionService executionService;

    /**
     * Constructor
     */
    public WorkflowInfoHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, ExecutionService executionService) {
        super(gson, ipRestrictionEnabled, allowedIps,authService);
        this.executionService = executionService;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendErrorResponse(exchange, 405, "Method not allowed");
            return;
        }

        try {
            // Get instanceId from URL path
            String instanceId = getPathParameter(exchange);

            if (instanceId == null || instanceId.isEmpty()) {
                sendErrorResponse(exchange, 400, "Missing instanceId");
                return;
            }

            // Get workflow instance
            WfInstance wfInstance = executionService.getWfInstance(instanceId);

            // Send response
            sendJsonResponse(exchange, 200, wfInstance);
        } catch (Exception e) {
            logger.error("Error getting workflow info", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }
}