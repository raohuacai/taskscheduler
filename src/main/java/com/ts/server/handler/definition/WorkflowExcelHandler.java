/*
 * Description: HTTP handler for workflow definition Excel import/export
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.definition;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import com.ts.service.DefinitionService;
import com.ts.service.TaskTypeService;
import com.ts.service.impl.WorkflowExcelService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * HTTP handler for Excel-based workflow definition import and export
 * ({@code /api/definition/excel/*}).
 *
 * <p>Delegates to {@link com.ts.service.impl.WorkflowExcelService} for
 * the actual Excel parsing and serialisation logic.
 */
public class WorkflowExcelHandler extends BaseHandler {

    private static final Logger logger = LogManager.getLogger(WorkflowExcelHandler.class);

    private final WorkflowExcelService excelService;

    /**
     * Constructs a new WorkflowExcelHandler instance.
     *
     * @param gson  gson value
     * @param ipRestrictionEnabled  ipRestrictionEnabled value
     * @param allowedIps  allowedIps value
     * @param authService  service dependency
     * @param definitionService  service dependency
     * @param taskTypeService  type discriminator
     */
    public WorkflowExcelHandler(Gson gson,
                                boolean ipRestrictionEnabled,
                                Set<String> allowedIps,
                                AuthService authService,
                                DefinitionService definitionService,
                                TaskTypeService taskTypeService) {
        super(gson, ipRestrictionEnabled, allowedIps, authService);
        this.excelService = new WorkflowExcelService(definitionService, taskTypeService);
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path   = exchange.getRequestURI().getPath();

        try {
            if ("GET".equals(method)) {
                handleExport(exchange);
            } else if ("POST".equals(method) && path.endsWith("/import")) {
                handleImport(exchange);
            } else {
                sendErrorResponse(exchange, 405, "Method not allowed");
            }
        } catch (Exception e) {
            logger.error("Error in WorkflowExcelHandler", e);
            sendErrorResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Export
    // ─────────────────────────────────────────────────────────────────────────

    private void handleExport(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> queryParams = parseQueryParameters(exchange);
            String filterWorkflowId   = queryParams.get("workflowId");
            String filterWorkflowName = queryParams.get("workflowName");
            byte[] excelBytes = excelService.exportToExcel(filterWorkflowId, filterWorkflowName);

            exchange.getResponseHeaders().set("Content-Type",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"workflow_definitions.xlsx\"");
            exchange.getResponseHeaders().set("Access-Control-Expose-Headers", "Content-Disposition");
            exchange.sendResponseHeaders(200, excelBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(excelBytes);
            }
            logger.info("Workflow definitions exported to Excel ({} bytes)", excelBytes.length);

        } catch (Exception e) {
            logger.error("Error exporting workflow definitions", e);
            sendErrorResponse(exchange, 500, "Export failed: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Import  (multipart/form-data)
    // ─────────────────────────────────────────────────────────────────────────

    private void handleImport(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("multipart/form-data")) {
            sendErrorResponse(exchange, 400, "Expected multipart/form-data");
            return;
        }

        // Extract boundary
        String boundary = null;
        for (String part : contentType.split(";")) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                boundary = part.substring("boundary=".length()).trim();
                break;
            }
        }
        if (boundary == null) {
            sendErrorResponse(exchange, 400, "Missing multipart boundary");
            return;
        }

        // Retrieve the raw request body bytes pre-cached by BaseHandler.handle().
        // The InputStream is already closed at this point; reading from the attribute
        // is the only safe way to access the body in any POST handler.
        byte[] bodyBytes = (byte[]) exchange.getAttribute("cachedRequestBody");
        if (bodyBytes == null || bodyBytes.length == 0) {
            sendErrorResponse(exchange, 400, "Empty request body");
            return;
        }

        // Extract the file part from multipart body
        byte[] fileBytes = extractFileFromMultipart(bodyBytes, boundary);
        if (fileBytes == null || fileBytes.length == 0) {
            sendErrorResponse(exchange, 400, "No Excel file found in request");
            return;
        }

        // Operator username from JWT authentication attribute
        String operator = (String) exchange.getAttribute("username");
        if (operator == null || operator.isEmpty()) operator = "system";

        WorkflowExcelService.ImportResult result = excelService.importFromExcel(fileBytes, operator);

        Map<String, Object> response = new HashMap<>();
        response.put("status",           result.hasErrors() && result.getTotalProcessed() == 0 ? "error" : "success");
        response.put("workflowsCreated", result.getWorkflowsCreated());
        response.put("workflowsUpdated", result.getWorkflowsUpdated());
        response.put("tasksCreated",     result.getTasksCreated());
        response.put("tasksUpdated",     result.getTasksUpdated());
        response.put("errors",           result.getErrors());

        int httpStatus = result.hasErrors() && result.getTotalProcessed() == 0 ? 400 : 200;
        sendJsonResponse(exchange, httpStatus, response);

        logger.info("Excel import done – wf+={}, wf~={}, task+={}, task~={}, errors={}",
                result.getWorkflowsCreated(), result.getWorkflowsUpdated(),
                result.getTasksCreated(),     result.getTasksUpdated(),
                result.getErrors().size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multipart parser (no external lib dependency)
    // ─────────────────────────────────────────────────────────────────────────

    private byte[] extractFileFromMultipart(byte[] body, String boundary) {
        byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);

        List<Integer> positions = new ArrayList<>();
        outer:
        for (int i = 0; i <= body.length - boundaryBytes.length; i++) {
            for (int j = 0; j < boundaryBytes.length; j++) {
                if (body[i + j] != boundaryBytes[j]) continue outer;
            }
            positions.add(i);
        }

        for (int pi = 0; pi < positions.size() - 1; pi++) {
            int start = positions.get(pi) + boundaryBytes.length;
            int end   = positions.get(pi + 1);

            if (start < body.length && body[start] == '\r') start++;
            if (start < body.length && body[start] == '\n') start++;

            int headerEnd = indexOf(body, new byte[]{'\r', '\n', '\r', '\n'}, start);
            if (headerEnd < 0) continue;

            String headers = new String(body, start, headerEnd - start, StandardCharsets.ISO_8859_1);
            if (!headers.contains("filename=")) continue;

            int dataStart = headerEnd + 4;
            int dataEnd   = end;
            if (dataEnd >= 2 && body[dataEnd - 2] == '\r' && body[dataEnd - 1] == '\n') dataEnd -= 2;

            if (dataStart < dataEnd) {
                byte[] fileBytes = new byte[dataEnd - dataStart];
                System.arraycopy(body, dataStart, fileBytes, 0, fileBytes.length);
                return fileBytes;
            }
        }
        return null;
    }

    private int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = from; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    @Override
    protected boolean isAllowedColumn(String column) {
        return false;
    }
}