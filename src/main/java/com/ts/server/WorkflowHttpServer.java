/*
 * Description: Embedded HTTP server wiring all API handlers to their URL paths
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpServer;
import com.ts.config.HttpServerConfig;
import com.ts.mapper.UserMapper;
import com.ts.mapper.VInstancesStatsMapper;
import com.ts.server.handler.BaseHandler;
import com.ts.server.handler.StaticFileHandler;
import com.ts.server.handler.auth.LoginHandler;
import com.ts.server.handler.event.EventHandler;
import com.ts.server.handler.history.HistoryHandler;
import com.ts.server.handler.stats.WorkflowStatsHandler;
import com.ts.server.handler.user.UserManagementHandler;
import com.ts.server.handler.execution.*;
import com.ts.server.handler.definition.*;
import com.ts.server.handler.params.*;
import com.ts.server.handler.scheduler.*;
import com.ts.server.handler.tasktype.*;
import com.ts.server.handler.lock.*;
import com.ts.mapper.UserMapper;
import com.ts.service.AuditService;
import com.ts.service.AuthService;
import com.ts.service.impl.AuthServiceImpl;
import com.ts.service.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
/**
 * Embedded HTTP server built on {@link com.sun.net.httpserver.HttpServer}.
 *
 * <p>Registers all API endpoint handlers under their respective path prefixes
 * ({@code /api/*}, {@code /static/*}). Authentication and IP restriction are
 * enforced in {@link BaseHandler} before any handler logic runs.
 *
 * <p>Configured via {@link com.ts.config.HttpServerConfig} loaded from the
 * global parameter store at startup.
 */
public class WorkflowHttpServer {
    private static final Logger logger = LogManager.getLogger(WorkflowHttpServer.class);
    private static final String CONTEXT_PATH = "/api";
    private static final String AUTH_PATH = CONTEXT_PATH + "/auth";
    private static final String WORKFLOW_PATH = CONTEXT_PATH + "/workflow";
    private static final String DEFINITION_PATH = CONTEXT_PATH + "/definition";
    private static final String PARAMS_PATH = CONTEXT_PATH + "/params";
    private static final String SCHEDULER_PATH = CONTEXT_PATH + "/scheduler";
    private static final String TASK_TYPE_PATH = CONTEXT_PATH + "/tasktype";
    private static final String LOCK_PATH = CONTEXT_PATH + "/lock";
    private static final String USERS_PATH = CONTEXT_PATH + "/users";
    private static final String HISTORY_PATH = CONTEXT_PATH + "/history";
    private static final String STATS_PATH = CONTEXT_PATH + "/stats";
    private static final String EVENT_PATH = CONTEXT_PATH + "/event";
    private static final String STATIC_PATH = "/static";
    private final HttpServerConfig httpServerConfig;
    private final HttpServer server;
    private final ExecutionService executionService;
    private final DefinitionService definitionService;
    private final ParamsService paramsService;
    private final SchedulerService schedulerService;
    private final TaskTypeService taskTypeService;
    private final LockService lockService;
    private final UserMapper userMapper;
    private final HistoryService historyService;
    private final AuthService authService;
    private final VInstancesStatsMapper vIStatsMapper;
    private final Gson gson;
    private final AuditService auditService;
    private boolean ipRestrictionEnabled = false;
    private Set<String> allowedIps = new HashSet<>();
    /**
     * Constructor creates and configures the HTTP server with all services
     */
    public WorkflowHttpServer(UserMapper userMapper,
                              VInstancesStatsMapper vIStatsMapper,
                              ExecutionService executionService,
                              DefinitionService definitionService,
                              ParamsService paramsService,
                              SchedulerService schedulerService,
                              TaskTypeService taskTypeService,
                              LockService lockService,
                              HistoryService historyService,
                              AuditService auditService) throws IOException {
        this.executionService = executionService;
        this.definitionService = definitionService;
        this.paramsService = paramsService;
        this.schedulerService = schedulerService;
        this.taskTypeService = taskTypeService;
        this.lockService = lockService;
        this.userMapper = userMapper;
        this.historyService = historyService;
        this.authService = new AuthServiceImpl(userMapper);
        this.vIStatsMapper = vIStatsMapper;
        this.gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").create();
        this.auditService = auditService;
        this.httpServerConfig = paramsService.getHttpServerConfig();
        // Register AuditService into BaseHandler so all handlers can write audit logs
        // without changing any individual handler constructor
        BaseHandler.setAuditService(auditService);
        // Create HTTP server with thread pool
        server = HttpServer.create(new InetSocketAddress(httpServerConfig.getHttpServerPort()), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        // Register all handlers
        registerHandlers();
        logger.info("Workflow HTTP server initialized on port {} with JWT authentication", httpServerConfig.getHttpServerPort());
    }
    /**
     * Register all handlers with the HTTP server
     */
    private void registerHandlers() {
        // Register authentication handler - login doesn't require authentication
        server.createContext(AUTH_PATH + "/login", new LoginHandler(gson, ipRestrictionEnabled, allowedIps, authService, auditService, false));
        // Register execution service handlers
        server.createContext(WORKFLOW_PATH + "/start", new StartWorkflowHandler(gson, ipRestrictionEnabled, allowedIps, authService, executionService));
        server.createContext(WORKFLOW_PATH + "/recover", new RecoverWorkflowHandler(gson, ipRestrictionEnabled, allowedIps, authService, executionService));
        server.createContext(WORKFLOW_PATH + "/cancel", new CancelWorkflowHandler(gson, ipRestrictionEnabled, allowedIps, authService, executionService));
        server.createContext(WORKFLOW_PATH + "/retry", new RetryTaskHandler(gson, ipRestrictionEnabled, allowedIps, authService, executionService));
        server.createContext(WORKFLOW_PATH + "/skip", new SkipTaskHandler(gson, ipRestrictionEnabled, allowedIps, authService, executionService));
        server.createContext(WORKFLOW_PATH + "/canceltask", new CancelTaskHandler(gson, ipRestrictionEnabled, allowedIps, authService, executionService));
        server.createContext(WORKFLOW_PATH + "/info", new WorkflowInfoHandler(gson, ipRestrictionEnabled, allowedIps, authService, executionService));
        server.createContext(WORKFLOW_PATH + "/workflows", new WorkflowsInfoHandler(gson, ipRestrictionEnabled, allowedIps, authService, executionService));
        server.createContext(WORKFLOW_PATH + "/tasks", new TasksListHandler(gson, ipRestrictionEnabled, allowedIps, authService, executionService));
        server.createContext(WORKFLOW_PATH + "/tasklog", new TaskLogHandler(gson, ipRestrictionEnabled, allowedIps, authService, executionService));
        // Register definition service handlers
        server.createContext(DEFINITION_PATH + "/workflow", new WorkflowDefinitionHandler(gson, ipRestrictionEnabled, allowedIps, authService, definitionService));
        server.createContext(DEFINITION_PATH + "/task", new TaskDefinitionHandler(gson, ipRestrictionEnabled, allowedIps, authService, definitionService));
        server.createContext(DEFINITION_PATH + "/dag", new DefinitionDagHandler(gson, ipRestrictionEnabled, allowedIps, authService, definitionService));
        server.createContext(DEFINITION_PATH + "/excel", new WorkflowExcelHandler(gson, ipRestrictionEnabled, allowedIps, authService, definitionService, taskTypeService));
        server.createContext(DEFINITION_PATH + "/excel/import", new WorkflowExcelHandler(gson, ipRestrictionEnabled, allowedIps, authService, definitionService, taskTypeService));
        // Register params service handlers
        server.createContext(PARAMS_PATH + "/global", new GlobalParamsHandler(gson, ipRestrictionEnabled, allowedIps, authService, paramsService));
        server.createContext(PARAMS_PATH + "/workflow", new WorkflowParamsHandler(gson, ipRestrictionEnabled, allowedIps, authService, paramsService));
        server.createContext(PARAMS_PATH + "/task", new TaskParamsHandler(gson, ipRestrictionEnabled, allowedIps, authService, paramsService));
        server.createContext(PARAMS_PATH + "/dag", new ParamsDagHandler(gson, ipRestrictionEnabled, allowedIps, authService, paramsService));
        // Register scheduler service handlers
        server.createContext(SCHEDULER_PATH + "/schedule", new ScheduleHandler(gson, ipRestrictionEnabled, allowedIps, authService, schedulerService));
        // Register task type service handlers
        server.createContext(TASK_TYPE_PATH, new TaskTypeHandler(gson, ipRestrictionEnabled, allowedIps, authService, taskTypeService));
        // Register lock service handlers
        server.createContext(LOCK_PATH + "/release", new ReleaseLockHandler(gson, ipRestrictionEnabled, allowedIps, authService, lockService));
        server.createContext(LOCK_PATH + "/releaseAll", new ReleaseAllLocksHandler(gson, ipRestrictionEnabled, allowedIps, authService, lockService));
        server.createContext(LOCK_PATH + "/releaseByOwner", new ReleaseByOwnerHandler(gson, ipRestrictionEnabled, allowedIps, authService, lockService));
        server.createContext(LOCK_PATH + "/list", new ListLocksHandler(gson, ipRestrictionEnabled, allowedIps, authService, lockService));
        // Register user management handler
        server.createContext(USERS_PATH, new UserManagementHandler(gson, ipRestrictionEnabled, allowedIps, authService, userMapper));
        server.createContext(USERS_PATH + "/resetpwd", new UserManagementHandler(gson, ipRestrictionEnabled, allowedIps, authService, userMapper));
        server.createContext(USERS_PATH + "/changepwd", new UserManagementHandler(gson, ipRestrictionEnabled, allowedIps, authService, userMapper));
        // Register history service handlers
        server.createContext(HISTORY_PATH, new HistoryHandler(gson, ipRestrictionEnabled, allowedIps, authService, historyService));
        // Register stats service handler
        server.createContext(STATS_PATH, new WorkflowStatsHandler(gson, ipRestrictionEnabled, allowedIps, authService, vIStatsMapper));
        // Register event service handlers
        // GET  /api/event?eventKey={key}  – query event record by key
        // POST /api/event/trigger         – fire a trigger for an event
        EventHandler eventHandler = new EventHandler(gson, ipRestrictionEnabled, allowedIps, authService);
        server.createContext(EVENT_PATH, eventHandler);
        server.createContext(EVENT_PATH + "/trigger", eventHandler);
        // Register static file handler
        if(httpServerConfig.isEnableStaticResource()) {
            logger.info("Http server enable static resource");
            registerStaticFileHandler();
        }
    }
    /**
     * Register static file handler
     */
    private void registerStaticFileHandler() {
        // Check if the static files directory exists
        File staticDir = new File(httpServerConfig.getHttpServerPath());
        if (!staticDir.exists()) {
            logger.warn("Static files directory '{}' does not exist. Creating directory.", httpServerConfig.getHttpServerPath());
            boolean created = staticDir.mkdirs();
            if (!created) {
                logger.error("Failed to create static files directory: {}", httpServerConfig.getHttpServerPath());
                return;
            }
        }
        // Register handler for the /static path
        server.createContext(STATIC_PATH, new StaticFileHandler(httpServerConfig.getHttpServerPath()));
        // Also register root path to static file handler for convenience
        server.createContext("/", new StaticFileHandler(httpServerConfig.getHttpServerPath()));
        logger.info("Static file handler registered for path '{}' serving from '{}'", STATIC_PATH, httpServerConfig.getHttpServerPath());
    }
    /**
     * Start the HTTP server
     */
    public void start() {
        server.start();
        logger.info("Workflow HTTP server started");
    }
    /**
     * Stop the HTTP server
     */
    public void stop() {
        server.stop(0);
        auditService.shutdown();   // Flush pending audit writes before exit
        logger.info("Workflow HTTP server stopped");
    }
    /**
     * Enable IP restriction for localhost only
     */
    public void enableLocalhostOnly() {
        ipRestrictionEnabled = true;
        allowedIps.clear();
        try {
            allowedIps.add("127.0.0.1");
            allowedIps.add("0:0:0:0:0:0:0:1");
            InetAddress localHost = InetAddress.getLocalHost();
            allowedIps.add(localHost.getHostAddress());
            logger.info("IP restriction enabled. Allowed IPs: {}", allowedIps);
        } catch (Exception e) {
            logger.error("Error configuring localhost IP restriction", e);
        }
    }
    /**
     * Create server with default port
     */
    public static WorkflowHttpServer createDefault(
            UserMapper userMapper,
            VInstancesStatsMapper vIStatsMapper,
            ExecutionService executionService,
            DefinitionService definitionService,
            ParamsService paramsService,
            SchedulerService schedulerService,
            TaskTypeService taskTypeService,
            LockService lockService,
            HistoryService historyService,
            AuditService auditService) throws IOException {
        return new WorkflowHttpServer(
                userMapper,
                vIStatsMapper,
                executionService,
                definitionService,
                paramsService,
                schedulerService,
                taskTypeService,
                lockService,
                historyService,
                auditService);
    }
    /**
     * Create server with default port and localhost restriction option
     */
    public static WorkflowHttpServer createDefault(
            UserMapper userMapper,
            VInstancesStatsMapper vIStatsMapper,
            ExecutionService executionService,
            DefinitionService definitionService,
            ParamsService paramsService,
            SchedulerService schedulerService,
            TaskTypeService taskTypeService,
            LockService lockService,
            HistoryService historyService,
            AuditService auditService,
            boolean localhostOnly) throws IOException {
        WorkflowHttpServer server = createDefault(
                userMapper,
                vIStatsMapper,
                executionService,
                definitionService,
                paramsService,
                schedulerService,
                taskTypeService,
                lockService,
                historyService,
                auditService);
        if (localhostOnly) {
            server.enableLocalhostOnly();
        }
        return server;
    }
}
