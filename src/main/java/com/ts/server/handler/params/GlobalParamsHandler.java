/*
 * Description: HTTP handler for global parameter read and update
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.params;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.ts.common.constants.Constants;
import com.ts.model.entity.GlobalParams;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import com.ts.service.ParamsService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handler for global parameters operations
 */
public class GlobalParamsHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(GlobalParamsHandler.class);
    private final ParamsService paramsService;

    /**
     * Constructor
     */
    public GlobalParamsHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, ParamsService paramsService) {
        super(gson, ipRestrictionEnabled, allowedIps, authService);
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
                case "PUT":
                    handleUpdate(exchange);
                    break;
                default:
                    sendErrorResponse(exchange, 405, "Method not allowed");
            }
        } catch (Exception e) {
            logger.error("Error in global params handler", e);
            sendErrorResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        try {
            // Get global parameters
            List<GlobalParams> params = paramsService.getAllGlobalParams();
            // Send response
            sendJsonResponse(exchange, 200, params);
        } catch (Exception e) {
            logger.error("Error getting global parameters", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }

    private void handleUpdate(HttpExchange exchange) throws IOException {
        try {
            // Parse request body
            String requestBody = readRequestBody(exchange);
            Type mapType = new TypeToken<Map<String, Object>>() {
            }.getType();
            Map<String, Object> params = gson.fromJson(requestBody, mapType);

            if (params != null && params.get("lastUpdatedBy") != null) {
                String lastUpdatedBy = (String) params.get("lastUpdatedBy");
                params.remove("lastUpdatedBy");
                // if it is not admin then throw exception
                if (!lastUpdatedBy.equalsIgnoreCase("admin")) {
                    throw new Exception("Please use 'admin' to modify global params");
                }
            }

            // Get parameter name from query string if available
            Map<String, String> queryParams = parseQueryParameters(exchange);
            String paramName = queryParams.getOrDefault("paramName", Constants.WF_GLOBAL_PARAM_NAME);

            // Update based on parameter type
            boolean success = false;
            switch (paramName) {
                case Constants.WF_GLOBAL_PARAM_NAME:
                    success = paramsService.updateWfGlobalParams(params);
                    break;
                case Constants.THREAD_POOL_CONFIG_NAME:
                    success = paramsService.updateThreadPoolConfig(params);
                    break;
                case Constants.EMAIL_CONFIG_NAME:
                    success = paramsService.updateMailConfig(params);
                    break;
                case Constants.EMAIL_TEMPLATE_CONFIG_NAME:
                    success = paramsService.updateEmailTemplateConfig(params);
                    break;
                case Constants.HTTP_SERVER_CONFIG_NAME:
                    success = paramsService.updateHttpServerConfig(params);
                    break;
                default:
                    // Does not update any record
                    //success = paramsService.updateWfGlobalParams(params);
            }

            // Send response
            Map<String, Object> response = new HashMap<>();
            response.put("status", success ? "success" : "failure");
            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            logger.error("Error updating global parameters", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }
}