/*
 * Description: HTTP handler for workflow run history with pagination and CSV export
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.history;

import com.github.pagehelper.PageInfo;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.model.vo.TaskInstanceVO;
import com.ts.model.vo.WorkflowInstanceVO;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import com.ts.service.HistoryService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Handler for workflow instance history operations
 */
public class HistoryHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(HistoryHandler.class);
    private final HistoryService historyService;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat exportDateFormat = new SimpleDateFormat("yyyy/M/d HH:mm:ss");

    /**
     * Constructor
     */
    public HistoryHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps,
                          AuthService authService, HistoryService historyService) {
        super(gson, ipRestrictionEnabled, allowedIps, authService);
        this.historyService = historyService;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        try {
            if ("GET".equals(method)) {
                handleGet(exchange);
            } else {
                sendErrorResponse(exchange, 405, "Method not allowed");
            }
        } catch (Exception e) {
            logger.error("Error in history handler", e);
            sendErrorResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        Map<String, String> queryParams = parseQueryParameters(exchange);
        boolean isExport = "true".equals(queryParams.get("export"));

        if (isExport) {
            handleExport(exchange, queryParams);
        } else {
            handleSearch(exchange);
        }
    }

    private void handleSearch(HttpExchange exchange) throws IOException {
        Map<String, String> queryParams = parseQueryParameters(exchange);

        // Read filter parameters from the query string
        String workflowId          = queryParams.get("workflowId");
        String workflowName        = queryParams.get("workflowName");
        String instanceId          = queryParams.get("instanceId");
        String parentWfInstanceId  = queryParams.get("parentWfInstanceId");
        String topParentInstanceId = queryParams.get("topParentInstanceId");
        String status              = queryParams.get("status");

        // Parse date-range filter parameters from the query string
        Date startDateFrom = null;
        Date startDateTo   = null;
        try {
            if (queryParams.containsKey("startDateFrom")) {
                startDateFrom = dateFormat.parse(queryParams.get("startDateFrom"));
            }
            if (queryParams.containsKey("startDateTo")) {
                startDateTo = dateFormat.parse(queryParams.get("startDateTo"));
                // Set to end of day
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(startDateTo);
                calendar.set(Calendar.HOUR_OF_DAY, 23);
                calendar.set(Calendar.MINUTE, 59);
                calendar.set(Calendar.SECOND, 59);
                startDateTo = calendar.getTime();
            }
        } catch (ParseException e) {
            logger.warn("Invalid date format", e);
            sendErrorResponse(exchange, 400, "Invalid date format. Use yyyy-MM-dd");
            return;
        }

        // Read pagination parameters with sensible defaults
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
                if (pageSize < 1)   pageSize = 10;
                if (pageSize > 100) pageSize = 100;
            }
            if (queryParams.containsKey("orderBy")) {
                orderBy = queryParams.get("orderBy");
                if (!isValidOrderByColumn(orderBy)) orderBy = null;
            }

            // Invoke the service layer to execute the paginated query
            PageInfo<WorkflowInstanceVO> pageInfo = historyService.search(
                    workflowId, workflowName, instanceId,
                    parentWfInstanceId, topParentInstanceId,
                    status, startDateFrom, startDateTo,
                    pageNum, pageSize, orderBy);

            // Build and send the JSON response
            Map<String, Object> response = new HashMap<>();
            response.put("list",     pageInfo.getList());
            response.put("total",    pageInfo.getTotal());
            response.put("pages",    pageInfo.getPages());
            response.put("pageNum",  pageInfo.getPageNum());
            response.put("pageSize", pageInfo.getPageSize());

            sendJsonResponse(exchange, 200, response);
        } catch (NumberFormatException e) {
            logger.error("Invalid pagination parameters", e);
            sendErrorResponse(exchange, 400, "Invalid pagination parameters");
        } catch (Exception e) {
            logger.error("Error searching workflow instances", e);
            sendErrorResponse(exchange, 500, e.getMessage());
        }
    }

    private void handleExport(HttpExchange exchange, Map<String, String> queryParams) throws IOException {
        try {
            // Extract filter parameters from the query string
            String workflowId          = queryParams.get("workflowId");
            String workflowName        = queryParams.get("workflowName");
            String instanceId          = queryParams.get("instanceId");
            String parentWfInstanceId  = queryParams.get("parentWfInstanceId");
            String topParentInstanceId = queryParams.get("topParentInstanceId");
            String status              = queryParams.get("status");

            // Parse date string to Date object
            Date startDateFrom = null;
            Date startDateTo   = null;
            try {
                if (queryParams.containsKey("startDateFrom")) {
                    startDateFrom = dateFormat.parse(queryParams.get("startDateFrom"));
                }
                if (queryParams.containsKey("startDateTo")) {
                    startDateTo = dateFormat.parse(queryParams.get("startDateTo"));
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(startDateTo);
                    calendar.set(Calendar.HOUR_OF_DAY, 23);
                    calendar.set(Calendar.MINUTE, 59);
                    calendar.set(Calendar.SECOND, 59);
                    startDateTo = calendar.getTime();
                }
            } catch (ParseException e) {
                logger.warn("Invalid date format", e);
            }

            // No pagination; retrieve all matching records for CSV export
            List<WorkflowInstanceVO> historyData = historyService.searchAll(
                    workflowId, workflowName, instanceId,
                    parentWfInstanceId, topParentInstanceId,
                    status, startDateFrom, startDateTo);

            // Generate CSV content from query results
            StringBuilder csv = new StringBuilder();

            // Write CSV header row
            csv.append("TPInstance ID,PInstance ID,Instance ID,Workflow ID,Workflow Name,Start Time,End Time,Status,Duration,")
                    .append("Task Instance ID,Task Name,Task Type,Task Start,Task End,Task Status,Error\r\n");

            // Append a data row to the CSV output
            for (WorkflowInstanceVO instance : historyData) {
                String wfStartDate = instance.getStartDate() != null ? exportDateFormat.format(instance.getStartDate()) : "-";
                String wfEndDate   = instance.getEndDate()   != null ? exportDateFormat.format(instance.getEndDate())   : "-";
                String duration    = instance.getDurationFormatted();

                if (instance.getTasks() != null && !instance.getTasks().isEmpty()) {
                    for (TaskInstanceVO task : instance.getTasks()) {
                        String taskStartDate = task.getStartDate() != null ? exportDateFormat.format(task.getStartDate()) : "-";
                        String taskEndDate   = task.getEndDate()   != null ? exportDateFormat.format(task.getEndDate())   : "-";

                        csv.append(escapeCsv(instance.getTopParentInstanceId())).append(",")
                                .append(escapeCsv(instance.getParentInstanceId())).append(",")
                                .append(escapeCsv(instance.getInstanceId())).append(",")
                                .append(escapeCsv(instance.getWorkflowId())).append(",")
                                .append(escapeCsv(instance.getWorkflowName())).append(",")
                                .append(escapeCsv(wfStartDate)).append(",")
                                .append(escapeCsv(wfEndDate)).append(",")
                                .append(escapeCsv(instance.getStatus())).append(",")
                                .append(escapeCsv(duration)).append(",")
                                .append(escapeCsv(task.getTaskInstanceId())).append(",")
                                .append(escapeCsv(task.getTaskName())).append(",")
                                .append(escapeCsv(task.getTaskType())).append(",")
                                .append(escapeCsv(taskStartDate)).append(",")
                                .append(escapeCsv(taskEndDate)).append(",")
                                .append(escapeCsv(task.getStatus())).append(",")
                                .append(escapeCsv("Y".equals(task.getErrorFlag()) ? "Yes" : "No")).append("\r\n");
                    }
                } else {
                    csv.append(escapeCsv(instance.getTopParentInstanceId())).append(",")
                            .append(escapeCsv(instance.getParentInstanceId())).append(",")
                            .append(escapeCsv(instance.getInstanceId())).append(",")
                            .append(escapeCsv(instance.getWorkflowId())).append(",")
                            .append(escapeCsv(instance.getWorkflowName())).append(",")
                            .append(escapeCsv(wfStartDate)).append(",")
                            .append(escapeCsv(wfEndDate)).append(",")
                            .append(escapeCsv(instance.getStatus())).append(",")
                            .append(escapeCsv(duration)).append(",")
                            .append(",,,,,,\r\n");
                }
            }

            // Prepend UTF-8 BOM and set charset
            StringBuilder csvWithBom = new StringBuilder();
            csvWithBom.append('\ufeff'); // Prepend UTF-8 BOM for Excel compatibility
            csvWithBom.append(csv);

            // Set HTTP response headers for CSV download
            exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment;filename=workflow_history_" +
                            new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".csv");

            byte[] responseBytes = csvWithBom.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }

        } catch (Exception e) {
            logger.error("Error exporting workflow history", e);
            sendErrorResponse(exchange, 500, "Error exporting workflow history: " + e.getMessage());
        }
    }

    // Helper method: escape a CSV field value
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    @Override
    protected boolean isAllowedColumn(String column) {
        Set<String> allowedColumns = Set.of(
                "WF_INST_ID", "WORKFLOW_ID", "WORKFLOW_NAME",
                "START_DATE", "END_DATE", "STATUS"
        );
        return allowedColumns.contains(column.toUpperCase());
    }
}
