/*
 * Description: HTTP handler for POST /api/workflow/start
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.execution;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.common.constants.Constants;
import com.ts.core.engine.WorkflowResult;
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
 * Handler for starting a workflow
 */
public class StartWorkflowHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(StartWorkflowHandler.class);
    private final ExecutionService executionService;

    /**
     * Constructor
     */
    public StartWorkflowHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, ExecutionService executionService) {
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
            // Parse request body
            String requestBody = readRequestBody(exchange);
            Map<String, Object> request = gson.fromJson(requestBody, Map.class);
            String workflowId = (String) request.get("workflowId");

            Map<String, Object> parameters = (Map<String, Object>) request.get("parameters");
            String triggeredBy = (String) request.get(Constants.PARAM_WORKFLOW_TRIGGERBY);

            if (workflowId == null || workflowId.isEmpty()) {
                sendErrorResponse(exchange, 400, "Missing workflowId");
                return;
            }

            if (parameters == null) {
                parameters = new HashMap<>();
            }

            if(triggeredBy != null && !triggeredBy.isEmpty()) {
                parameters.put(Constants.PARAM_WORKFLOW_TRIGGERBY, triggeredBy);
            }

            // Start the workflow
            WorkflowResult result = executionService.startWorkflow(workflowId, parameters);

            // Prepare response
            Map<String, Object> response = new HashMap<>();

            if (result.isSuccess()) {
                // Success case
                response.put("status", "success");
                response.put("instanceId", result.getWfInstanceId());
            } else {
                // Error case - treated as a normal response
                response.put("status", "failure");
                response.put("error", result.getErrorMessage());
            }

            // Send response
            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            logger.error("Server error starting workflow", e);
            sendErrorResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }
}