/*
 * Description: HTTP handler for GET /api/workflow/task-log streaming task execution log entries
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.execution;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.model.entity.TaskLog;
import com.ts.model.entity.TaskParameter;
import com.ts.server.handler.BaseHandler;
import com.ts.server.handler.params.TaskParamsHandler;
import com.ts.service.AuthService;
import com.ts.service.ExecutionService;
import com.ts.service.ParamsService;
import com.ts.service.impl.TaskLogService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * HTTP handler for {@code GET /api/workflow/task-log}.
 *
 * <p>Streams the accumulated execution log entries for a task instance,
 * useful for real-time monitoring in the UI.
 */
public class TaskLogHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(TaskParamsHandler.class);
    private final ExecutionService executionService;

    /**
     * Constructor
     */
    public TaskLogHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, ExecutionService executionService) {
        super(gson, ipRestrictionEnabled, allowedIps, authService);
        this.executionService = executionService;
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
                List<TaskLog> taskLogs = TaskLogService.getTaskLogsByTaskInstanceId(taskInstanceId);
                sendJsonResponse(exchange, 200, taskLogs);
            } else {
                sendErrorResponse(exchange, 400, "Missing taskInstanceId, id, or wfInstanceId parameter");
            }
        } catch (Exception e) {
            logger.error("Error in task params handler", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }
}