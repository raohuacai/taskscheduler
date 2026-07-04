/*
 * Description: HTTP handler for GET /api/workflow/tasks listing task instances of a workflow
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.execution;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.model.entity.TaskInstance;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import com.ts.service.ExecutionService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Handler for listing tasks for a workflow
 */
public class TasksListHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(TasksListHandler.class);
    private final ExecutionService executionService;

    /**
     * Constructor
     */
    public TasksListHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, ExecutionService executionService) {
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

            // Get task instances
            List<TaskInstance> tasks = executionService.getTaskInstancesForWorkflow(instanceId);

            // Send response
            sendJsonResponse(exchange, 200, tasks);
        } catch (Exception e) {
            logger.error("Error listing tasks", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }
}