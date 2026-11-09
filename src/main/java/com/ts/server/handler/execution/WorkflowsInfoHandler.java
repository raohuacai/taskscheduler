/*
 * Description: HTTP handler for paginated workflow instance list with filtering and sorting
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.execution;

import com.github.pagehelper.PageInfo;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.model.entity.WfDefinition;
import com.ts.model.entity.WfInstance;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import com.ts.service.ExecutionService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
/**
 * HTTP handler for {@code GET /api/workflow/list} — paginated workflow instance list.
 *
 * <p>Supports filtering by workflow ID, name, instance ID, status, and date range.
 * Results are paginated and optionally sorted by the caller-supplied {@code orderBy}
 * column (validated against an allowlist to prevent SQL injection).
 */
public class WorkflowsInfoHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(WorkflowsInfoHandler.class);
    private final ExecutionService executionService;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * Constructor
     */
    public WorkflowsInfoHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, ExecutionService executionService) {
        super(gson, ipRestrictionEnabled, allowedIps, authService);
        this.executionService = executionService;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendErrorResponse(exchange, 405, "Method not allowed");
            return;
        }

        Map<String, String> queryParams = parseQueryParameters(exchange);

        // Read filter parameters from the query string
        String workflowId = queryParams.get("workflowId");
        String workflowName = queryParams.get("workflowName");
        String wfInstanceId = queryParams.get("wfInstanceId");
        String parentWfInstanceId = queryParams.get("parentWfInstanceId");
        String topParentInstanceId = queryParams.get("topParentInstanceId");
        String status = queryParams.get("status");

        // Parse date-range filter parameters from the query string
        Date startDateFrom = null;
        Date startDateTo = null;
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
            PageInfo<WfInstance> pageInfo = executionService.search(workflowId, workflowName, wfInstanceId, parentWfInstanceId, topParentInstanceId, status,
                    startDateFrom, startDateTo, pageNum, pageSize, orderBy);

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
        Set<String> allowedColumns = Set.of("WF_INSTANCE_ID", "WORKFLOW_NAME", "START_DATE", "WORKFLOW_ID", "END_DATE", "STATUS", "PWF_INSTANCE_ID", "TPWF_INSTANCE_ID");
        return allowedColumns.contains(column.toUpperCase());
    }
}
