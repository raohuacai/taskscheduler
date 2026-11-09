/*
 * Description: HTTP handler for workflow definition CRUD and paginated search
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.definition;

import com.github.pagehelper.PageInfo;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.exception.WorkflowNotFoundException;
import com.ts.model.entity.WfDefinition;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import com.ts.service.DefinitionService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handler for workflow definition operations
 */
public class WorkflowDefinitionHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(WorkflowDefinitionHandler.class);
    private final DefinitionService definitionService;

    /**
     * Constructor
     */
    public WorkflowDefinitionHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, DefinitionService definitionService) {
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
            logger.error("Error in workflow definition handler", e);
            sendErrorResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        // If a path parameter is present, do an exact primary-key lookup for edit
        String pathParam = getPathParameter(exchange);
        if (pathParam != null && !pathParam.isEmpty()) {
            try {
                WfDefinition wf = definitionService.getWorkflow(pathParam);
                sendJsonResponse(exchange, 200, wf);
            } catch (WorkflowNotFoundException e) {
                sendErrorResponse(exchange, 404, e.getMessage());
            } catch (Exception e) {
                logger.error("Error getting workflow by id: " + pathParam, e);
                sendErrorResponse(exchange, 500, e.getMessage());
            }
            return;
        }

        Map<String, String> queryParams = parseQueryParameters(exchange);

        // Read filter parameters from the query string
        String workflowId = queryParams.get("workflowId");
        String workflowName = queryParams.get("workflowName");

        // Read pagination parameters with sensible defaults
        int pageNum = 1;
        int pageSize = 10;
        String orderBy = null;

        try {
            // Attempt to parse pagination parameters as integers
            if (queryParams.containsKey("pageNum")) {
                pageNum = Integer.parseInt(queryParams.get("pageNum"));
                if (pageNum < 1) pageNum = 1; // Clamp page number to a minimum of 1
            }

            if (queryParams.containsKey("pageSize")) {
                pageSize = Integer.parseInt(queryParams.get("pageSize"));
                if (pageSize < 1) pageSize = 10; // Clamp page size to a minimum of 1
                if (pageSize > 100) pageSize = 100; // Cap page size at 100 to prevent excessively large queries
            }

            // Retrieve sorting parameters from the query string
            if (queryParams.containsKey("orderBy")) {
                orderBy = queryParams.get("orderBy");
                if (!isValidOrderByColumn(orderBy)) orderBy = null;
            }

            // Invoke the service layer to execute the paginated query
            PageInfo<WfDefinition> pageInfo = definitionService.search(workflowId, workflowName, pageNum, pageSize, orderBy);

            // Build and send the JSON response
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
            logger.error("Error getting workflow definition", e);
            sendErrorResponse(exchange, 500, e.getMessage());
        }
    }

    @Override
    protected boolean isAllowedColumn(String column) {
        Set<String> allowedColumns = Set.of("WORKFLOW_ID", "WORKFLOW_NAME", "ENABLE_FLAG", "CREATION_DATE");
        return allowedColumns.contains(column.toUpperCase());
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        try {
            // Parse request body
            String requestBody = readRequestBody(exchange);
            WfDefinition workflowDefinition = gson.fromJson(requestBody, WfDefinition.class);

            // Create workflow
            String workflowId = definitionService.createWorkflow(workflowDefinition);

            // Send response
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("workflowId", workflowId);
            sendJsonResponse(exchange, 201, response);
        } catch (Exception e) {
            logger.error("Error creating workflow definition", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }

    private void handlePut(HttpExchange exchange) throws IOException {
        try {
            // Parse request body
            String requestBody = readRequestBody(exchange);
            WfDefinition workflowDefinition = gson.fromJson(requestBody, WfDefinition.class);

            // Update workflow
            boolean success = definitionService.updateWorkflow(workflowDefinition);

            // Send response
            Map<String, Object> response = new HashMap<>();
            response.put("status", success ? "success" : "failure");
            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            logger.error("Error updating workflow definition", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        // Get workflowId from URL path
        String workflowId = getPathParameter(exchange);

        if (workflowId == null || workflowId.isEmpty()) {
            sendErrorResponse(exchange, 400, "Missing workflowId");
            return;
        }

        try {
            // Delete workflow
            boolean success = definitionService.deleteWorkflow(workflowId);

            // Send response
            Map<String, Object> response = new HashMap<>();
            response.put("status", success ? "success" : "failure");
            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            logger.error("Error deleting workflow definition", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }
}