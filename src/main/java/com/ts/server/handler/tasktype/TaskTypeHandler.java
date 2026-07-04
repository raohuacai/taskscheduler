/*
 * Description: HTTP handler for task-type registry CRUD
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.tasktype;

import com.github.pagehelper.PageInfo;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.ts.model.entity.TaskType;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import com.ts.service.TaskTypeService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Handler for task type operations
 */
public class TaskTypeHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(TaskTypeHandler.class);
    private final TaskTypeService taskTypeService;

    /**
     * Constructor
     */
    public TaskTypeHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, TaskTypeService taskTypeService) {
        super(gson, ipRestrictionEnabled, allowedIps, authService);
        this.taskTypeService = taskTypeService;
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
                    handleRegister(exchange);
                    break;
                case "PUT":
                    handleUpdate(exchange);
                    break;
                case "DELETE":
                    handleUnregister(exchange);
                    break;
                default:
                    sendErrorResponse(exchange, 405, "Method not allowed");
            }
        } catch (Exception e) {
            logger.error("Error in task type handler", e);
            sendErrorResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        String pathParam = getPathParameter(exchange);
        if (pathParam != null && !pathParam.isEmpty()) {
            handleGetOne(exchange, pathParam);
        } else {
            handleSearch(exchange);
        }
    }

    // GET /api/tasktype/{typeName} — exact match, used to pre-populate the edit form
    private void handleGetOne(HttpExchange exchange, String typeName) throws IOException {
        try {
            TaskType taskType = taskTypeService.getTaskType(typeName);
            if (taskType == null) {
                sendErrorResponse(exchange, 404, "Task type not found: " + typeName);
                return;
            }
            sendJsonResponse(exchange, 200, taskType);
        } catch (Exception e) {
            logger.error("Error getting task type: {}", typeName, e);
            sendErrorResponse(exchange, 500, e.getMessage());
        }
    }

    // GET /api/tasktype?typeGroup=&typeName=&pageNum=&pageSize=&orderBy= — fuzzy search
    private void handleSearch(HttpExchange exchange) throws IOException {
        Map<String, String> queryParams = parseQueryParameters(exchange);

        String typeName  = queryParams.get("typeName");
        String typeGroup = queryParams.get("typeGroup");

        int pageNum  = 1;
        int pageSize = 10;
        String orderBy = null;

        try {
            if (queryParams.containsKey("pageNum")) {
                pageNum = Integer.parseInt(queryParams.get("pageNum"));
                if (pageNum < 1) pageNum = 1;
            }

            if (queryParams.containsKey("pageSize")) {
                pageSize = Integer.parseInt(queryParams.get("pageSize"));
                if (pageSize < 1) pageSize = 10;
                if (pageSize > 100) pageSize = 100;
            }

            if (queryParams.containsKey("orderBy")) {
                orderBy = queryParams.get("orderBy");
                if (!isValidOrderByColumn(orderBy)) orderBy = null;
            }

            PageInfo<TaskType> pageInfo = taskTypeService.search(typeGroup, typeName, pageNum, pageSize, orderBy);

            Map<String, Object> response = new HashMap<>();
            response.put("records", pageInfo.getList());
            response.put("total", pageInfo.getTotal());
            response.put("pages", pageInfo.getPages());
            response.put("pageNum", pageInfo.getPageNum());
            response.put("pageSize", pageInfo.getPageSize());

            sendJsonResponse(exchange, 200, response);
        } catch (NumberFormatException e) {
            logger.error("Invalid pagination parameters", e);
            sendErrorResponse(exchange, 400, "Invalid pagination parameters");
        } catch (Exception e) {
            logger.error("Error getting task types", e);
            sendErrorResponse(exchange, 500, e.getMessage());
        }
    }

    /**
     * Checks whether the given column name is in the ORDER BY allowlist for
     * the task-type list query, preventing SQL injection via caller-supplied
     * sort parameters.
     *
     * @param column  the column name extracted from the {@code orderBy} query parameter
     * @return {@code true} if the column is in the static allowlist; {@code false} otherwise
     */
    protected boolean isAllowedColumn(String column) {
        Set<String> allowedColumns = Set.of(
                "TYPE_NAME", "TYPE_GROUP", "TYPE_DESC",
                "CLASS_NAME", "PVALUE", "INTERRUPTABLE_FLAG",
                "CREATION_DATE"
        );

        return allowedColumns.contains(column.toUpperCase());
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        try {
            // Read the request body
            String requestBody = readRequestBody(exchange);

            // Parse the request body as JSON to extract the lastUpdatedBy field
            JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();

            // Extract lastUpdatedBy from the parsed JSON
            String lastUpdatedBy = null;
            if (jsonObject.has("lastUpdatedBy")) {
                lastUpdatedBy = jsonObject.get("lastUpdatedBy").getAsString();
            }

            // Validate user permissions for this operation
            validateAdminUser(lastUpdatedBy);

            // Deserialise the JSON request body into a TaskType entity
            TaskType taskType = gson.fromJson(requestBody, TaskType.class);

            // Register task type
            boolean success = taskTypeService.registerTaskType(taskType);

            // Send the JSON response back to the client
            Map<String, Object> response = new HashMap<>();
            response.put("status", success ? "success" : "failure");
            sendJsonResponse(exchange, 201, response);
        } catch (Exception e) {
            logger.error("Error registering task type", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }

    private void handleUpdate(HttpExchange exchange) throws IOException {
        try {
            // Read the request body
            String requestBody = readRequestBody(exchange);

            // Parse the request body as JSON to extract the lastUpdatedBy field
            JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();

            // Extract lastUpdatedBy from the parsed JSON
            String lastUpdatedBy = null;
            if (jsonObject.has("lastUpdatedBy")) {
                lastUpdatedBy = jsonObject.get("lastUpdatedBy").getAsString();
            }

            // Validate user permissions for this operation
            validateAdminUser(lastUpdatedBy);

            // Deserialise the JSON request body into a TaskType entity
            TaskType taskType = gson.fromJson(requestBody, TaskType.class);

            // Update task type
            boolean success = taskTypeService.updateTaskType(taskType);

            // Send the JSON response back to the client
            Map<String, Object> response = new HashMap<>();
            response.put("status", success ? "success" : "failure");
            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            logger.error("Error updating task type", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }

    private void handleUnregister(HttpExchange exchange) throws IOException {
        // Get type name from URL path
        String typeName = getPathParameter(exchange);

        if (typeName == null || typeName.isEmpty()) {
            sendErrorResponse(exchange, 400, "Missing task type name");
            return;
        }

        try {
            // Retrieve lastUpdatedBy from query parameters
            Map<String, String> queryParams = parseQueryParameters(exchange);
            String lastUpdatedBy = queryParams.get("lastUpdatedBy");

            // Validate user permissions for this operation
            validateAdminUser(lastUpdatedBy);

            // Unregister task type
            boolean success = taskTypeService.unregisterTaskType(typeName);

            // Send the JSON response back to the client
            Map<String, Object> response = new HashMap<>();
            response.put("status", success ? "success" : "failure");
            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            logger.error("Error unregistering task type", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }

    // Validate that the requesting user has admin privileges
    private void validateAdminUser(String username) throws Exception {
        if (username == null || !username.equals("admin")) {
            throw new Exception("Please use 'admin' to modify task type");
        }
    }
}