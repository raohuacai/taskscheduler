/*
 * Description: HTTP handler for cron-schedule definition CRUD
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.scheduler;

import com.github.pagehelper.PageInfo;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.common.constants.Constants;
import com.ts.core.scheduler.CronExpression;
import com.ts.model.entity.WfScheduler;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import com.ts.service.SchedulerService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;

/**
 * Handler for schedule operations
 */
public class ScheduleHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(ScheduleHandler.class);
    private final SchedulerService schedulerService;

    /**
     * Constructor
     */
    public ScheduleHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, SchedulerService schedulerService) {
        super(gson, ipRestrictionEnabled, allowedIps, authService);
        this.schedulerService = schedulerService;
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
                    handleCreate(exchange);
                    break;
                case "PUT":
                    handleUpdate(exchange);
                    break;
                case "DELETE":
                    handleDelete(exchange);
                    break;
                default:
                    sendErrorResponse(exchange, 405, "Method not allowed");
            }
        } catch (Exception e) {
            logger.error("Error in schedule handler", e);
            sendErrorResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        // If a path parameter is present, do an exact primary-key lookup for edit
        String pathParam = getPathParameter(exchange);
        if (pathParam != null && !pathParam.isEmpty()) {
            try {
                WfScheduler sc = schedulerService.search(pathParam);
                if (sc == null) {
                    sendErrorResponse(exchange, 404, "Schedule not found: " + pathParam);
                    return;
                }
                if ("Y".equals(sc.getEnableFlag())) {
                    try {
                        String cron = sc.getCron();
                        if (cron != null && !cron.isEmpty()) {
                            CronExpression cronExpr = new CronExpression(cron);
                            sc.setNextRunTime(cronExpr.getNextValidTimeAfter(new Date()));
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse cron for workflow {}: {}", pathParam, e.getMessage());
                    }
                }
                sendJsonResponse(exchange, 200, sc);
            } catch (Exception e) {
                logger.error("Error getting schedule by id: " + pathParam, e);
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
            PageInfo<WfScheduler> pageInfo = schedulerService.search(workflowId, workflowName, pageNum, pageSize, orderBy);

            // Calculate the next scheduled run time for UI display
            Date now = new Date();
            List<WfScheduler> schedulers = pageInfo.getList();
            for (WfScheduler scheduler : schedulers) {
                // Only calculate the next run time when enableFlag is 'Y'
                if ("Y".equals(scheduler.getEnableFlag())) {
                    try {
                        String cronExpression = scheduler.getCron();
                        if (cronExpression != null && !cronExpression.isEmpty()) {
                            CronExpression cron = new CronExpression(cronExpression);
                            Date nextRunTime = cron.getNextValidTimeAfter(now);
                            scheduler.setNextRunTime(nextRunTime);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse cron expression for workflow {}: {}", scheduler.getWorkflowId(), e.getMessage());
                    }
                } else {
                    scheduler.setNextRunTime(null);
                }
            }

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
            logger.error("Error getting schedulers", e);
            sendErrorResponse(exchange, 500, e.getMessage());
        }
    }

    @Override
    protected boolean isAllowedColumn(String column) {
        Set<String> allowedColumns = Set.of("WORKFLOW_ID", "WORKFLOW_NAME", "ENABLE_FLAG", "CREATION_DATE");
        return allowedColumns.contains(column.toUpperCase());
    }

    private void handleCreate(HttpExchange exchange) throws IOException {
        try {
            // Parse request body
            String requestBody = readRequestBody(exchange);
            Map<String, Object> request = gson.fromJson(requestBody, Map.class);

            String workflowId = (String) request.get("workflowId");
            String cronExpression = (String) request.get("cronExpression");
            boolean enableFlag = request.get("enableFlag") != null ? (boolean) request.get("enableFlag").equals(Constants.FLAG_YES) : false;
            String createdBy = (String) request.get("createdBy");

            if (workflowId == null || workflowId.isEmpty()) {
                sendErrorResponse(exchange, 400, "Missing workflowId");
                return;
            }

            if (cronExpression == null || cronExpression.isEmpty()) {
                sendErrorResponse(exchange, 400, "Missing cronExpression");
                return;
            } else {
                //validate cron expression
                CronExpression.validateExpression(cronExpression);
            }

            // Schedule workflow
            boolean success = schedulerService.createScheduler(workflowId, cronExpression, enableFlag, createdBy);

            // Send response
            Map<String, Object> response = new HashMap<>();
            response.put("status", success ? "success" : "failure");
            sendJsonResponse(exchange, 201, response);
        } catch (Exception e) {
            logger.error("Error scheduling workflow", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }

    private void handleUpdate(HttpExchange exchange) throws IOException {
        try {
            // Parse request body
            String requestBody = readRequestBody(exchange);
            Map<String, Object> request = gson.fromJson(requestBody, Map.class);

            String workflowId = (String) request.get("workflowId");
            String cronExpression = (String) request.get("cronExpression");
            boolean enableFlag = request.get("enableFlag") != null ? (boolean) request.get("enableFlag").equals(Constants.FLAG_YES) : false;
            String lastUpdatedBy = (String) request.get("lastUpdatedBy");

            if (workflowId == null || workflowId.isEmpty()) {
                sendErrorResponse(exchange, 400, "Missing workflowId");
                return;
            }

            if (cronExpression == null || cronExpression.isEmpty()) {
                sendErrorResponse(exchange, 400, "Missing cronExpression");
                return;
            } else {
                //validate cron expression
                CronExpression.validateExpression(cronExpression);
            }

            // Update schedule
            boolean success = schedulerService.updateScheduler(workflowId, cronExpression, enableFlag, lastUpdatedBy);

            // Send response
            Map<String, Object> response = new HashMap<>();
            response.put("status", success ? "success" : "failure");
            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            logger.error("Error updating schedule", e);
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
            // Delete schedule
            boolean success = schedulerService.deleteScheduler(workflowId);

            // Send response
            Map<String, Object> response = new HashMap<>();
            response.put("status", success ? "success" : "failure");
            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            logger.error("Error deleting schedule", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }
}