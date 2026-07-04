/*
 * Description: HTTP handler for GET /api/monitor returning real-time engine statistics
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.stats;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.monitor.WorkflowMonitor;
import com.ts.Main;
import com.ts.mapper.VInstancesStatsMapper;
import com.ts.model.vo.VInstancesStatsVO;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
/**
 * HTTP handler for {@code GET /api/monitor}.
 *
 * <p>Returns a JSON snapshot of real-time engine statistics collected by
 * {@link com.ts.monitor.WorkflowMonitor}, including active workflow count,
 * task throughput counters, thread-pool utilisation, and scheduler metrics.
 */
public class WorkflowStatsHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(WorkflowStatsHandler.class);
    private final VInstancesStatsMapper vIStatsMapper;

    /**
     * Constructs a new WorkflowStatsHandler instance.
     *
     * @param gson  gson value
     * @param ipRestrictionEnabled  ipRestrictionEnabled value
     * @param allowedIps  allowedIps value
     * @param authService  service dependency
     * @param vIStatsMapper  vIStatsMapper value
     */
    public WorkflowStatsHandler(Gson gson, boolean ipRestrictionEnabled, Set<String> allowedIps, AuthService authService, VInstancesStatsMapper vIStatsMapper) {
        super(gson, ipRestrictionEnabled, allowedIps, authService);
        this.vIStatsMapper = vIStatsMapper;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        try {
            switch (method) {
                case "GET":
                    handleGet(exchange);
                    break;
                default:
                    sendErrorResponse(exchange, 405, "Method not allowed");
            }
        } catch (Exception e) {
            logger.error("Error in stats handler", e);
            sendErrorResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }

    /**
     * Handles {@code GET /api/monitor} by retrieving the current monitoring
     * snapshot from {@link com.ts.monitor.WorkflowMonitor#getMonitoringData()}
     * and returning it as a JSON object.
     *
     * @param exchange  the HTTP exchange
     * @throws IOException  if writing the response fails
     */
    protected void handleGet(HttpExchange exchange) throws IOException {
        try {
            // Retrieve the WorkflowMonitor singleton from the Main application context
            WorkflowMonitor monitor = Main.getWorkflowMonitor();

            if (monitor == null) {
                sendErrorResponse(exchange, 500, "Workflow stats instance not available");
                return;
            }

            // get monitor data
            Map<String, Object> wfStats = monitor.getMonitorData();
            // get history workflow/task stats from database
            List<VInstancesStatsVO> allStats = vIStatsMapper.findAll();
            Map<String, List<VInstancesStatsVO>> statsByType = allStats.stream()
                    .collect(Collectors.groupingBy(VInstancesStatsVO::getDtype));
            wfStats.put("statsByType", statsByType);

            String monitorData = gson.toJson(wfStats);
            logger.debug("Monitor data: " + monitorData);

            // Send the JSON response back to the client
            sendJsonResponse(exchange, 200, monitorData);
            logger.debug("Monitor data returned successfully");
        } catch (Exception e) {
            logger.error("Error processing stats data request", e);
            sendErrorResponse(exchange, 400, e.getMessage());
        }
    }

}