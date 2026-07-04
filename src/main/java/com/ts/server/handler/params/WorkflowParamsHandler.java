/*
 * Description: HTTP handler for workflow-level parameter read and update
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.params;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.model.entity.WfParameter;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import com.ts.service.ParamsService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Handler for workflow parameters operations
 */
public class WorkflowParamsHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(WorkflowParamsHandler.class);
    private final ParamsService paramsService;

    /**
     * Constructor
     */
    public WorkflowParamsHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, ParamsService paramsService) {
        super(gson, ipRestrictionEnabled, allowedIps,authService);
        this.paramsService = paramsService;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if (!method.equalsIgnoreCase("GET")) {
            sendErrorResponse(exchange, 405, "Method not allowed");
            return;
        }

        try {
            Map<String, String> queryParams = parseQueryParameters(exchange);
            String wfInstanceId = queryParams.get("wfInstanceId");
            String wfInstanceParamId = queryParams.get("id");

            if (wfInstanceId != null && !wfInstanceId.isEmpty()) {
                // Get by instance ID
                WfParameter params = paramsService.getWfParamsByInstanceId(wfInstanceId);
                sendJsonResponse(exchange, 200, params);
            } else if (wfInstanceParamId != null && !wfInstanceParamId.isEmpty()) {
                // Get by parameter ID
                WfParameter params = paramsService.getWfParamById(wfInstanceParamId);
                sendJsonResponse(exchange, 200, params);
            } else {
                sendErrorResponse(exchange, 400, "Missing wfInstanceId or id parameter");
            }
        } catch (Exception e) {
            logger.error("Error in workflow params handler", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }
}