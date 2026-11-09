package com.ts.server.handler.execution;

import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.ts.model.entity.WfInstance;
import com.ts.service.AuthService;
import com.ts.service.ExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.OutputStream;
import java.net.URI;
import java.util.Collections;

import static org.mockito.Mockito.*;

/**
 * Tests for the B3 fix in {@link WorkflowInfoHandler}: since
 * {@code ExecutionService.getWfInstance} now returns {@code null} for a missing
 * instance, the handler must respond 404 (instead of the previous 200-with-null
 * body that would have resulted from the null return).
 */
@ExtendWith(MockitoExtension.class)
class WorkflowInfoHandlerTest {

    private static final String CONTEXT_PATH = "/api/workflow/info";

    @Mock
    private AuthService authService;
    @Mock
    private ExecutionService executionService;
    @Mock
    private HttpExchange exchange;
    @Mock
    private HttpContext httpContext;

    private WorkflowInfoHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        handler = new WorkflowInfoHandler(new Gson(), false, Collections.emptySet(), authService, executionService);

        when(exchange.getRequestMethod()).thenReturn("GET");
        when(exchange.getHttpContext()).thenReturn(httpContext);
        when(httpContext.getPath()).thenReturn(CONTEXT_PATH);
        when(exchange.getResponseHeaders()).thenReturn(new Headers());
        when(exchange.getResponseBody()).thenReturn(mock(OutputStream.class));
    }

    private void stubPath(String instanceId) {
        when(exchange.getRequestURI()).thenReturn(URI.create(CONTEXT_PATH + "/" + instanceId));
    }

    // ==================== 404 on missing instance (B3) ====================

    @Test
    void getWorkflowInfo_missingInstance_returns404() throws Exception {
        stubPath("wf-inst-missing");
        when(executionService.getWfInstance("wf-inst-missing")).thenReturn(null);

        handler.handleRequest(exchange);

        verify(exchange).sendResponseHeaders(eq(404), anyLong());
        // and NOT a 200 with a null body
        verify(exchange, never()).sendResponseHeaders(eq(200), anyLong());
    }

    // ==================== 200 on found instance ====================

    @Test
    void getWorkflowInfo_foundInstance_returns200() throws Exception {
        stubPath("wf-inst-1");
        WfInstance instance = new WfInstance();
        instance.setWfInstanceId("wf-inst-1");
        when(executionService.getWfInstance("wf-inst-1")).thenReturn(instance);

        handler.handleRequest(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        verify(exchange, never()).sendResponseHeaders(eq(404), anyLong());
    }

    // ==================== 400 on missing path parameter ====================

    @Test
    void getWorkflowInfo_missingPathParam_returns400() throws Exception {
        // context path == full path -> no parameter segment
        when(exchange.getRequestURI()).thenReturn(URI.create(CONTEXT_PATH));

        handler.handleRequest(exchange);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
        verify(executionService, never()).getWfInstance(anyString());
    }
}
