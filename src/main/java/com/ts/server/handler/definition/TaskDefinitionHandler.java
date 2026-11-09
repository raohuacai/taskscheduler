/*
 * Description: HTTP handler for task definition CRUD within a workflow
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.definition;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.ts.model.entity.TaskDefinition;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import com.ts.service.DefinitionService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handler for task definition operations
 */
public class TaskDefinitionHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(TaskDefinitionHandler.class);
    private final DefinitionService definitionService;

    /**
     * Constructor
     */
    public TaskDefinitionHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, DefinitionService definitionService) {
        super(gson, ipRestrictionEnabled, allowedIps,authService);
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
                case "POST":
                    handlePost(exchange);
                    break;
                case "PUT":
                    handlePut(exchange);
                    break;
                case "DELETE":
                    handleDelete(exchange);
                    break;
                default:
                    sendErrorResponse(exchange, 405, "Method not allowed");
            }
        } catch (Exception e) {
            logger.error("Error in task definition handler", e);
            sendErrorResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        // Get taskId from URL path
        String taskId = getPathParameter(exchange);
        Map<String, String> queryParams = parseQueryParameters(exchange);

        try {
            if (taskId != null && !taskId.isEmpty()) {
                // Get specific task
                TaskDefinition task = definitionService.getTask(taskId);
                if (task != null) {
                    sendJsonResponse(exchange, 200, task);
                } else {
                    sendErrorResponse(exchange, 404, "Task not found: " + taskId);
                }
            } else if (queryParams.containsKey("workflowId")) {
                // Get tasks for workflow
                String workflowId = queryParams.get("workflowId");
                List<TaskDefinition> tasks = definitionService.getTasksByWorkflowId(workflowId);
                sendJsonResponse(exchange, 200, tasks);
            } else {
                sendErrorResponse(exchange, 400, "Missing taskId or workflowId parameter");
            }
        } catch (Exception e) {
            logger.error("Error getting task definition", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        try {
            // Parse request body
            String requestBody = readRequestBody(exchange);
            Map<String, Object> requestMap = gson.fromJson(requestBody, Map.class);

            String workflowId = (String) requestMap.get("workflowId");
            if (workflowId == null || workflowId.isEmpty()) {
                sendErrorResponse(exchange, 400, "Missing workflowId");
                return;
            }

            // Convert task definition
            Type taskType = new TypeToken<TaskDefinition>() {}.getType();
            TaskDefinition taskDefinition = gson.fromJson(
                    gson.toJson(requestMap.get("taskDefinition")), taskType);

            // Create task
            String taskId = definitionService.createTask(workflowId, taskDefinition);

            // Send response
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("taskId", taskId);
            sendJsonResponse(exchange, 201, response);
        } catch (Exception e) {
            logger.error("Error creating task definition", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }

    private void handlePut(HttpExchange exchange) throws IOException {
        try {
            // Parse request body
            String requestBody = readRequestBody(exchange);
            TaskDefinition taskDefinition = gson.fromJson(requestBody, TaskDefinition.class);

            // Update task
            boolean success = definitionService.updateTask(taskDefinition);

            // Send response
            Map<String, Object> response = new HashMap<>();
            response.put("status", success ? "success" : "failure");
            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            logger.error("Error updating task definition", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        // Get taskId from URL path
        String taskId = getPathParameter(exchange);

        if (taskId == null || taskId.isEmpty()) {
            sendErrorResponse(exchange, 400, "Missing taskId");
            return;
        }

        try {
            // Delete task
            boolean success = definitionService.deleteTask(taskId);

            // Send response
            Map<String, Object> response = new HashMap<>();
            response.put("status", success ? "success" : "failure");
            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            logger.error("Error deleting task definition", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }
}