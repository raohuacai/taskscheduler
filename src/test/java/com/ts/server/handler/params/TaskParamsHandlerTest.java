package com.ts.server.handler.params;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.ts.model.entity.TaskParameter;
import com.ts.service.AuthService;
import com.ts.service.ParamsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TaskParamsHandler}: the task parameter payload returned to
 * the Monitoring &gt; Task Details &gt; Parameters panel must have password
 * values masked, while every other field stays intact.
 */
@ExtendWith(MockitoExtension.class)
class TaskParamsHandlerTest {

    private static final String PATH = "/api/params/task";

    @Mock
    private AuthService authService;
    @Mock
    private ParamsService paramsService;
    @Mock
    private HttpExchange exchange;

    private TaskParamsHandler handler;
    private ByteArrayOutputStream responseBody;

    @BeforeEach
    void setUp() {
        handler = new TaskParamsHandler(new Gson(), false, Collections.emptySet(), authService, paramsService);
        responseBody = new ByteArrayOutputStream();
    }

    /** Stubs the exchange with a GET request carrying the given query string. */
    private void stubRequest(String queryString) {
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(exchange.getRequestURI()).thenReturn(URI.create(PATH + queryString));
        when(exchange.getResponseHeaders()).thenReturn(new Headers());
        when(exchange.getResponseBody()).thenReturn(responseBody);
    }

    private TaskParameter taskParameter(String pvalue) {
        TaskParameter p = new TaskParameter();
        p.setTaskParamId("tp-1");
        p.setTaskInstanceId("ti-1");
        p.setPvalue(pvalue);
        return p;
    }

    private String responseText() {
        return responseBody.toString(StandardCharsets.UTF_8);
    }

    /**
     * Parses the response and returns the (unescaped) pvalue document, so
     * assertions read the parameter JSON rather than the escaped wire form.
     */
    private String responsePvalue() {
        JsonObject response = JsonParser.parseString(responseText()).getAsJsonObject();
        return response.get("pvalue").getAsString();
    }

    // ==================== masking ====================

    @Test
    void taskParams_passwordValue_isMasked() throws Exception {
        stubRequest("?taskInstanceId=ti-1");
        when(paramsService.getTaskParamsByInstanceId("ti-1"))
                .thenReturn(taskParameter("{\"username\":\"SDBMGR\",\"password\":\"SDBMGR\"}"));

        handler.handleRequest(exchange);

        String pvalue = responsePvalue();
        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        assertTrue(pvalue.contains("\"password\":\"******\""), pvalue);
        assertFalse(pvalue.contains("\"password\":\"SDBMGR\""), pvalue);
        // the non-secret sibling field must survive untouched
        assertTrue(pvalue.contains("\"username\":\"SDBMGR\""), pvalue);
    }

    @Test
    void taskParams_sourcePassword_isMasked() throws Exception {
        stubRequest("?taskInstanceId=ti-1");
        when(paramsService.getTaskParamsByInstanceId("ti-1"))
                .thenReturn(taskParameter("{\"sourcePassword\":\"abc\",\"targetPassword\":\"de\"}"));

        handler.handleRequest(exchange);

        String pvalue = responsePvalue();
        assertTrue(pvalue.contains("\"sourcePassword\":\"***\""), pvalue);
        assertTrue(pvalue.contains("\"targetPassword\":\"**\""), pvalue);
        assertFalse(pvalue.contains("\"sourcePassword\":\"abc\""), pvalue);
        assertFalse(pvalue.contains("\"targetPassword\":\"de\""), pvalue);
    }

    @Test
    void taskParams_nullPvalue_returnedUnchanged() throws Exception {
        stubRequest("?taskInstanceId=ti-1");
        when(paramsService.getTaskParamsByInstanceId("ti-1")).thenReturn(taskParameter(null));

        handler.handleRequest(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        assertFalse(responseText().contains("password"), responseText());
    }

    // ==================== regression ====================

    @Test
    void taskParams_missingTaskInstanceId_returns400() throws Exception {
        stubRequest("");

        handler.handleRequest(exchange);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
        verify(paramsService, never()).getTaskParamsByInstanceId(anyString());
    }
}
