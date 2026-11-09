/*
 * Description: HTTP handler for DAG structure retrieval and save for the visual workflow editor
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.definition;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.model.entity.TaskDefinition;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import com.ts.service.DefinitionService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Handler for Definition DAG operations
 */
public class DefinitionDagHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(DefinitionDagHandler.class);
    private final DefinitionService definitionService;

    /**
     * Constructor
     */
    public DefinitionDagHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, DefinitionService definitionService) {
        super(gson, ipRestrictionEnabled, allowedIps, authService);
        this.definitionService = definitionService;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        try {
            switch (method) {
                case "GET":
                    handleGet(exchange);
                    break;
                case "PUT":
                    handleUpdate(exchange);
                    break;
                default:
                    sendErrorResponse(exchange, 405, "Method not allowed");
            }
        } catch (Exception e) {
            logger.error("Error in Definition DAG handler", e);
            sendErrorResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        // Get workflowId from URL path or query parameter
        String workflowId = getPathParameter(exchange);
        Map<String, String> queryParams = parseQueryParameters(exchange);

        if (workflowId == null || workflowId.isEmpty()) {
            workflowId = queryParams.get("workflowId");
        }

        if (workflowId == null || workflowId.isEmpty()) {
            sendErrorResponse(exchange, 400, "Missing workflowId");
            return;
        }

        try {
            // Get DAG from Definition Service (static definition)
            Map<String, Object> dag = definitionService.getWorkflowDAG(workflowId);

            // Send response
            sendJsonResponse(exchange, 200, dag);
        } catch (Exception e) {
            logger.error("Error getting workflow definition DAG", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }

    private void handleUpdate(HttpExchange exchange) throws IOException {
        // Get workflowId from request body or query parameters
        String requestBody = readRequestBody(exchange);
        Map<String, Object> request = gson.fromJson(requestBody, Map.class);

        String workflowId = (String) request.get("workflowId");
        String taskId = (String) request.get("taskId");
        String predecessors = (String) request.get("predecessors");
        String lastUpdatedBy = (String) request.get("lastUpdatedBy");

        if (workflowId == null || workflowId.isEmpty()) {
            sendErrorResponse(exchange, 400, "Missing workflowId");
            return;
        }
        if (taskId == null || taskId.isEmpty()) {
            sendErrorResponse(exchange, 400, "Missing taskId");
            return;
        }

        try {
            // Validate DAG using Definition Service
            TaskDefinition taskDefinition = new TaskDefinition();
            taskDefinition.setWorkflowId(workflowId);
            taskDefinition.setTaskId(taskId);
            taskDefinition.setPredecessors(predecessors);
            taskDefinition.setLastUpdatedBy(lastUpdatedBy);
            boolean result = definitionService.updatePredecessors(taskDefinition);

            if (result) {
                // Send response
                Map<String, Object> response = new HashMap<>();
                response.put("result", result);
                sendJsonResponse(exchange, 200, response);
            }else {
                sendErrorResponse(exchange, 400, "Did not update predecessors for TaskId: " + taskId);
                return;
            }

        } catch (Exception e) {
            logger.error("Error update predecessors", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }
}