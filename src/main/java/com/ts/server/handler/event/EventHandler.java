/*
 * Description: HTTP handler for workflow event trigger and status query
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.event;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.server.AuditContext;
import com.ts.event.EventManager;
import com.ts.model.entity.TsEvent;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * EventHandler – HTTP handler for the event subsystem.
 *
 * Routes (registered in WorkflowHttpServer):
 *   GET  /api/event          ?eventKey={key}   Query event record by key
 *   POST /api/event/trigger                    Fire a trigger for an event
 *
 * Both routes share this single handler class to minimise the number of
 * registered contexts. The HTTP method and path suffix distinguish behaviour.
 *
 * Audit:
 *   GET  is not audited (BaseHandler skips GET by design).
 *   POST /api/event/trigger is automatically audited by BaseHandler's finally
 *   block once AuditRouteConfig registers the route ("TRIGGER_EVENT", "EVENT").
 *   Failures are flagged via AuditContext.markFailure() inside this handler so
 *   the audit record reflects the true outcome when no exception is thrown.
 *
 * HTTP status codes returned by handleTrigger:
 *   200  – TriggerResult.SUCCESS
 *   404  – TriggerResult.NOT_FOUND
 *   409  – TriggerResult.ALREADY_TRIGGERED
 *   400  – Missing required field
 *   500  – Unexpected server error
 *
 * Author: Rock Rao
 * Date: 2025
 * Version: 1.0
 */
public class EventHandler extends BaseHandler {

    private static final Logger logger = LogManager.getLogger(EventHandler.class);

    /**
     * Constructs an EventHandler wired to the given services.
     *
     * @param gson                  Gson instance used for JSON serialisation
     * @param ipRestrictionEnabled  whether IP-whitelist enforcement is active
     */
    public EventHandler(Gson gson, boolean ipRestrictionEnabled,
                        Set<String> allowedIps, AuthService authService) {
        super(gson, ipRestrictionEnabled, allowedIps, authService);
    }

    // ── Dispatch ───────────────────────────────────────────────────────────────

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path   = exchange.getRequestURI().getPath();

        try {
            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange);
            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/trigger")) {
                handleTrigger(exchange);
            } else {
                sendErrorResponse(exchange, 405, "Method not allowed");
            }
        } catch (Exception e) {
            logger.error("Unexpected error in EventHandler", e);
            sendErrorResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }

    // ── GET /api/event?eventKey={key} ─────────────────────────────────────────

    /**
     * Query an event record by its unique eventKey.
     * Returns 404 when the key does not exist in TS_EVENT.
     */
    private void handleGet(HttpExchange exchange) throws IOException {
        Map<String, String> queryParams = parseQueryParameters(exchange);
        String eventKey = queryParams.get("eventKey");

        if (eventKey == null || eventKey.trim().isEmpty()) {
            sendErrorResponse(exchange, 400, "Missing required query parameter: eventKey");
            return;
        }

        TsEvent event = EventManager.getInstance().findByEventKey(eventKey.trim());
        if (event == null) {
            sendErrorResponse(exchange, 404, "Event not found: " + eventKey);
            return;
        }

        sendJsonResponse(exchange, 200, toResponse(event));
    }

    // ── POST /api/event/trigger ────────────────────────────────────────────────

    /**
     * Fire a trigger for the given eventKey.
     *
     * Request body (JSON):
     * {
     *   "eventKey" : "order.approved.12345",   // required
     *   "payload"  : { ... }                   // optional; any JSON value or string
     * }
     *
     * {@code triggeredBy} is taken from the JWT "username" attribute set by
     * BaseHandler's authentication step.
     */
    private void handleTrigger(HttpExchange exchange) throws IOException {
        String requestBody = readRequestBody(exchange);
        Map<String, Object> request = gson.fromJson(requestBody, Map.class);

        // Validate required field
        String eventKey = request.containsKey("eventKey")
                ? (String) request.get("eventKey") : null;

        if (eventKey == null || eventKey.trim().isEmpty()) {
            AuditContext.markFailure("Missing required field: eventKey");
            sendErrorResponse(exchange, 400, "Missing required field: eventKey");
            return;
        }
        eventKey = eventKey.trim();

        // Serialise optional payload to a JSON string.
        // If "payload" is absent or null, pass null to EventManager (stored as empty string).
        String payload = null;
        if (request.containsKey("payload") && request.get("payload") != null) {
            Object rawPayload = request.get("payload");
            payload = (rawPayload instanceof String)
                    ? (String) rawPayload
                    : gson.toJson(rawPayload);
        }

        // Operator comes from JWT attribute injected by BaseHandler authentication
        String triggeredBy = (String) exchange.getAttribute("username");
        if (triggeredBy == null || triggeredBy.isEmpty()) {
            triggeredBy = "UNKNOWN";
        }

        logger.info("EventHandler trigger: eventKey='{}', triggeredBy='{}'", eventKey, triggeredBy);

        EventManager.TriggerResult result =
                EventManager.getInstance().triggerEvent(eventKey, payload, triggeredBy);

        Map<String, Object> response = new HashMap<>();
        response.put("eventKey", eventKey);

        switch (result) {
            case SUCCESS:
                response.put("status", "success");
                response.put("message", "Event triggered successfully");
                sendJsonResponse(exchange, 200, response);
                break;

            case NOT_FOUND:
                response.put("status", "failure");
                response.put("message", "Event not found: " + eventKey);
                AuditContext.markFailure("Event not found: " + eventKey);
                sendJsonResponse(exchange, 404, response);
                break;

            case ALREADY_TRIGGERED:
                //response.put("status", "failure");
                response.put("status", "success");
                response.put("message", "Event already triggered: " + eventKey);
                AuditContext.markFailure("Event already triggered: " + eventKey);
                //sendJsonResponse(exchange, 409, response);
                sendJsonResponse(exchange, 200, response);
                break;

            default:
                response.put("status", "failure");
                response.put("message", "Unexpected trigger result");
                AuditContext.markFailure("Unexpected trigger result for: " + eventKey);
                sendJsonResponse(exchange, 500, response);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Convert TsEvent to a frontend-friendly response map. */
    private Map<String, Object> toResponse(TsEvent event) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",             event.getId());
        map.put("eventKey",       event.getEventKey());
        map.put("tpwfInstanceId", event.getTpwfInstanceId());
        map.put("taskId",         event.getTaskId());
        map.put("status",         event.getStatus());
        map.put("payload",        event.getPayload());
        map.put("triggeredBy",    event.getTriggeredBy());
        map.put("creationDate",   event.getCreationDate());
        map.put("waitDate",       event.getWaitDate());
        map.put("triggeredDate",  event.getTriggeredDate());
        return map;
    }
}
