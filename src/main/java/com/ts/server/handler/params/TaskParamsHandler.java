/*
 * Description: HTTP handler for task-level parameter read and update
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.params;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.model.entity.TaskParameter;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import com.ts.service.ParamsService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handler for task parameters operations
 */
public class TaskParamsHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(TaskParamsHandler.class);
    private final ParamsService paramsService;

    /**
     * Constructor
     */
    public TaskParamsHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, ParamsService paramsService) {
        super(gson, ipRestrictionEnabled, allowedIps, authService);
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
            String taskInstanceId = queryParams.get("taskInstanceId");

            if (taskInstanceId != null && !taskInstanceId.isEmpty()) {
                // Get by task instance ID
                TaskParameter params = paramsService.getTaskParamsByInstanceId(taskInstanceId);
                sendJsonResponse(exchange, 200, params);
            } else {
                sendErrorResponse(exchange, 400, "Missing taskInstanceId, id, or wfInstanceId parameter");
            }
        } catch (Exception e) {
            logger.error("Error in task params handler", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }
}