/*
 * Description: HTTP handler for parameter inheritance DAG retrieval
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.params;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import com.ts.service.ParamsService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Handler for Runtime DAG operations (from ParamsService)
 */
public class ParamsDagHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(ParamsDagHandler.class);
    private final ParamsService paramsService;

    /**
     * Constructor
     */
    public ParamsDagHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, ParamsService paramsService) {
        super(gson, ipRestrictionEnabled, allowedIps,authService);
        this.paramsService = paramsService;
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
                    handleValidate(exchange);
                    break;
                default:
                    sendErrorResponse(exchange, 405, "Method not allowed");
            }
        } catch (Exception e) {
            logger.error("Error in Runtime DAG handler", e);
            sendErrorResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        // Get wfInstanceId from URL path or query parameter
        String wfInstanceId = getPathParameter(exchange);
        Map<String, String> queryParams = parseQueryParameters(exchange);

        if (wfInstanceId == null || wfInstanceId.isEmpty()) {
            wfInstanceId = queryParams.get("wfInstanceId");
        }

        if (wfInstanceId == null || wfInstanceId.isEmpty()) {
            sendErrorResponse(exchange, 400, "Missing wfInstanceId");
            return;
        }

        try {
            // Get DAG from Params Service (runtime data)
            Map<String, Object> dag = paramsService.getWorkflowDAG(wfInstanceId);
            // Send response
            sendJsonResponse(exchange, 200, dag);
        } catch (Exception e) {
            logger.error("Error getting workflow runtime DAG", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }

    private void handleValidate(HttpExchange exchange) throws IOException {
        // Get wfInstanceId from request body or query parameters
        Map<String, String> queryParams = parseQueryParameters(exchange);
        String wfInstanceId = queryParams.get("wfInstanceId");

        if (wfInstanceId == null || wfInstanceId.isEmpty()) {
            String requestBody = readRequestBody(exchange);
            Map<String, Object> request = gson.fromJson(requestBody, Map.class);
            wfInstanceId = (String) request.get("wfInstanceId");
        }

        if (wfInstanceId == null || wfInstanceId.isEmpty()) {
            sendErrorResponse(exchange, 400, "Missing wfInstanceId");
            return;
        }

        try {
            // Validate DAG using Params Service (runtime validation)
            boolean isValid = paramsService.validateWorkflowDAG(wfInstanceId);
            // Send response
            Map<String, Object> response = new HashMap<>();
            response.put("valid", isValid);
            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            logger.error("Error validating workflow runtime DAG", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }
}