/*
 * Description: Application bootstrap, dependency wiring, and static service locator for the TS workflow engine
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts;

import com.ts.common.constants.Constants;
import com.ts.common.utils.Copyright;
import com.ts.common.SeqId.SeqIdGenerator;
import com.ts.core.engine.WorkflowEngine;
import com.ts.event.EventManager;
import com.ts.monitor.WorkflowMonitor;
import com.ts.core.scheduler.CronScheduler;
import com.ts.core.scheduler.Scheduler;
import com.ts.core.timewheel.NettyTimeWheel;
import com.ts.core.timewheel.TimeWheel;
import com.ts.core.transaction.TransactionManagerFactory;
import com.ts.dao.*;
import com.ts.dao.impl.*;
import com.ts.mapper.UserMapper;
import com.ts.mapper.VInstancesStatsMapper;
import com.ts.model.entity.*;
import com.ts.msg.NotificationFactoryImpl;
import com.ts.msg.NotificationFactory;
import com.ts.server.WorkflowHttpServer;
import com.ts.service.*;
import com.ts.service.impl.*;
import com.ts.task.DefaultTaskFactory;
import com.ts.task.Task;
import com.ts.task.TaskFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Application entry point and service locator for the TS workflow engine.
 *
 * <p>Bootstraps all infrastructure components in dependency order:
 * MyBatis {@link org.apache.ibatis.session.SqlSessionFactory}, DAO and mapper instances,
 * service layer, {@link com.ts.core.timewheel.NettyTimeWheel}, {@link com.ts.core.engine.WorkflowEngine},
 * {@link com.ts.core.scheduler.CronScheduler}, the embedded HTTP server, and
 * {@link com.ts.monitor.WorkflowMonitor}. Registers a JVM shutdown hook that
 * gracefully stops the scheduler, time wheel, engine, and HTTP server.
 *
 * <p>Also serves as a static service locator (e.g., {@code Main.getLockService()})
 * for components (such as {@link com.ts.task.core.LockTask}) that cannot receive
 * dependencies through constructor injection.
 *
 * <p>This class is intentionally not a Spring bean — the project uses manual
 * dependency wiring to stay framework-free.
 */
public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);

    private static WorkflowEngine workflowEngine;
    private static Scheduler scheduler;
    private static TimeWheel timeWheel;
    private static TaskFactory taskFactory;
    private static Map<String, Object> daos;
    private static ParamsService paramsService;
    private static DefinitionService definitionService;
    private static ExecutionService executionService;
    private static SchedulerService schedulerService;
    private static TaskTypeService taskTypeService;
    private static LockService lockService;
    private static TaskLogService taskLogService;
    private static AuditService auditService;
    private static WorkflowMonitor monitor;
    private static WorkflowHttpServer httpServer;
    private static AuthService authService;
    private static HistoryService historyService;
    private static EventService eventService;

    /**
     * Main.
     *
     * @param args args value
     */
    public static void main(String[] args) {
        // Don't remove this line
        Copyright.printCopyright();

        logger.info("Starting workflow engine application");
        try {
            // Initialize database
            SqlSessionFactory sqlSessionFactory = initializeDatabase();

            // Initialize sequence IdGenerator
            logger.info("Initializing sequence generator based on database type");
            SeqIdGenerator.initialize(sqlSessionFactory);

            // Initialize Transaction Manager
            TransactionManagerFactory.initialize(sqlSessionFactory);

            // Initialize DAOs
            daos = initializeDAOs(sqlSessionFactory);

            // Initialize services
            initializeServices(daos);

            // Initialize EventManager singleton
            initializeEventManager();

            // Initialize msg services
            NotificationFactory notificationFactory = initializeNotificationServices();

            // Initialize task factory
            taskFactory = initializeTaskFactory((TaskTypeDao) daos.get("TaskTypeDao"), notificationFactory);
            taskTypeService.setTaskFactory(taskFactory);

            // Initialize workflow engine
            workflowEngine = new WorkflowEngine(definitionService, executionService, paramsService, taskFactory, notificationFactory);
            workflowEngine.initialize();
            executionService.setWorkflowEngine(workflowEngine);

            // Initialize Netty TimeWheel
            timeWheel = new NettyTimeWheel();
            timeWheel.start();

            // Initialize scheduler
            scheduler = new CronScheduler((WfSchedulerDao) daos.get("WfSchedulerDao"), workflowEngine, timeWheel);
            scheduler.initialize();
            schedulerService.setScheduler(scheduler);

            // Initialize stats
            monitor = new WorkflowMonitor(workflowEngine, workflowEngine.getExecutorService(), (NettyTimeWheel) timeWheel, (CronScheduler) scheduler);
            workflowEngine.setMonitor(monitor);
            monitor.start();
            logger.info("Workflow monitoring service started");

            // Initialize HTTP server for local command access
            initializeWfHttpServer();

            logger.info("Workflow engine application started successfully");
            //workflowEngine.start("MAIN-WF", null);

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Main.shutdown();
            }));

            // Start local shutdown listener
            Thread shutdownListenerThread = new Thread(new ShutdownListener());
            shutdownListenerThread.setDaemon(true);
            shutdownListenerThread.start();

            // Keep application running
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.error("Error starting workflow engine application", e);
            System.exit(1);
        }
    }

    private static SqlSessionFactory initializeDatabase() throws Exception {
        logger.info("Initializing database connection");
        try (InputStream inputStream = Resources.getResourceAsStream("mybatis-config.xml")) {
            return new SqlSessionFactoryBuilder().build(inputStream);
        }
    }

    private static Map<String, Object> initializeDAOs(SqlSessionFactory sqlSessionFactory) {
        logger.info("Initializing DAOs");
        Map<String, Object> daos = new HashMap<>();
        // Add TaskTypeDao to the map of DAOs
        daos.put("GlobalParamsDao", new GlobalParamsDaoImpl(sqlSessionFactory));
        daos.put("WfParameterDao", new WfParameterDaoImpl(sqlSessionFactory));
        daos.put("TaskParameterDao", new TaskParameterDaoImpl(sqlSessionFactory));
        daos.put("WfSchedulerDao", new WfSchedulerDaoImpl(sqlSessionFactory));
        daos.put("TaskDefinitionDao", new TaskDefinitionDaoImpl(sqlSessionFactory));
        daos.put("TaskInstanceDao", new TaskInstanceDaoImpl(sqlSessionFactory));
        daos.put("TaskTypeDao", new TaskTypeDaoImpl(sqlSessionFactory));
        daos.put("WfDefinitionDao", new WfDefinitionDaoImpl(sqlSessionFactory));
        daos.put("WfInstanceDao", new WfInstanceDaoImpl(sqlSessionFactory));
        daos.put("TSLockDao", new TSLockDaoImpl(sqlSessionFactory));
        daos.put("TaskLogDao", new TaskLogDaoImpl(sqlSessionFactory));
        daos.put("UserMapper", new UserMapperImpl(sqlSessionFactory));
        daos.put("VInstancesStatsMapper", new VInstancesStatsMapperImpl(sqlSessionFactory));
        daos.put("HistoryDao", new HistoryDaoImpl(sqlSessionFactory));
        daos.put("AuditLogDao", new AuditLogDaoImpl(sqlSessionFactory));
        daos.put("TsEventDao", new TsEventDaoImpl(sqlSessionFactory));
        return daos;
    }

    private static void initializeServices(Map<String, Object> daos) {
        logger.info("Initializing Services");
        paramsService = new ParamsServiceImpl((GlobalParamsDao) daos.get("GlobalParamsDao"), (WfDefinitionDao) daos.get("WfDefinitionDao"), (TaskDefinitionDao) daos.get("TaskDefinitionDao"), (WfParameterDao) daos.get("WfParameterDao"), (TaskParameterDao) daos.get("TaskParameterDao"), (WfInstanceDao) daos.get("WfInstanceDao"), (TaskInstanceDao) daos.get("TaskInstanceDao"));
        definitionService = new DefinitionServiceImpl((WfDefinitionDao) daos.get("WfDefinitionDao"), (TaskDefinitionDao) daos.get("TaskDefinitionDao"), (TaskTypeDao) daos.get("TaskTypeDao"));
        executionService = new ExecutionServiceImpl((WfInstanceDao) daos.get("WfInstanceDao"), (TaskInstanceDao) daos.get("TaskInstanceDao"));
        schedulerService = new SchedulerServiceImpl((WfSchedulerDao) daos.get("WfSchedulerDao"), (WfDefinitionDao) daos.get("WfDefinitionDao"));
        taskTypeService = new TaskTypeServiceImpl((TaskTypeDao) daos.get("TaskTypeDao"));
        lockService = new LockServiceImpl((TSLockDao) daos.get("TSLockDao"));
        taskLogService = new TaskLogService((TaskLogDao) daos.get("TaskLogDao"));
        authService = new AuthServiceImpl((UserMapper) daos.get("UserMapper"));
        historyService = new HistoryServiceImpl((HistoryDao) daos.get("HistoryDao"));
        auditService = new AuditServiceImpl((AuditLogDao) daos.get("AuditLogDao"));
        eventService = new EventServiceImpl((TsEventDao) daos.get("TsEventDao"));
    }

    /**
     * Initialize the EventManager singleton.
     * Must be called after initializeServices() (EventService depends on TsEventDao)
     * and before WorkflowEngine starts (EventTask may be submitted during engine startup).
     */
    private static void initializeEventManager() {
        logger.info("Initializing EventManager");
        EventManager.initialize(eventService);
        logger.info("EventManager initialized");
    }

    private static NotificationFactory initializeNotificationServices() {
        logger.info("Initializing msg services");
        // Create msg factory
        NotificationFactory factory = new NotificationFactoryImpl(paramsService);
        return factory;
    }

    private static TaskFactory initializeTaskFactory(TaskTypeDao taskTypeDao, NotificationFactory notificationFactory) {
        logger.info("Initializing task factory from database");

        ConcurrentMap<String, Class<? extends Task>> taskTypes = new ConcurrentHashMap<>();

        try {
            // Fetch all task types from the database
            List<TaskType> dbTaskTypes = taskTypeDao.getAllTaskTypes();

            if (dbTaskTypes == null || dbTaskTypes.isEmpty()) {
                logger.warn("No task types found in database. Task factory will be initialized with empty task types.");
                return new DefaultTaskFactory(taskTypes);
            }

            logger.info("Found {} task types in database", dbTaskTypes.size());

            // Register each task type from database
            for (TaskType taskType : dbTaskTypes) {
                try {
                    String typeName = taskType.getTypeName();
                    String className = taskType.getClassName();

                    if (typeName == null || className == null || typeName.trim().isEmpty() || className.trim().isEmpty()) {
                        logger.warn("Invalid task type configuration: typeName={}, className={}", typeName, className);
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    Class<? extends Task> taskClass = (Class<? extends Task>) Class.forName(className);

                    // Register the task type
                    taskTypes.put(typeName, taskClass);
                    logger.info("Registered task type: {} -> {}", typeName, className);

                } catch (ClassNotFoundException e) {
                    logger.error("Class not found for task type {}: {}", taskType.getTypeName(), taskType.getClassName(), e);
                } catch (ClassCastException e) {
                    logger.error("Class {} does not implement Task interface", taskType.getClassName(), e);
                } catch (Exception e) {
                    logger.error("Error registering task type {}", taskType.getTypeName(), e);
                }
            }

            logger.info("Successfully registered {} task types", taskTypes.size());

        } catch (Exception e) {
            logger.error("Error loading task types from database", e);
        }

        return new DefaultTaskFactory(taskTypes);
    }

    private static void initializeWfHttpServer() {
        try {
            logger.debug("Initializing workflow HTTP server");
            httpServer = WorkflowHttpServer.createDefault(
                    (UserMapper) daos.get("UserMapper"),
                    (VInstancesStatsMapper) daos.get("VInstancesStatsMapper"),
                    executionService,
                    definitionService,
                    paramsService,
                    schedulerService,
                    taskTypeService,
                    lockService,
                    historyService,
                    auditService,
                    false);
            httpServer.start();
            logger.debug("Workflow HTTP server started");
        } catch (Exception e) {
            logger.error("Failed to start workflow HTTP server", e);
            // Continue without HTTP server, don't exit application
        }
    }

    /**
     * Returns the workflow engine.
     *
     * @return the requested workflowengine object, or null if not found
     */
    public static WorkflowEngine getWorkflowEngine() {
        return workflowEngine;
    }

    /**
     * Returns the workflow monitor.
     *
     * @return the requested workflowmonitor object, or null if not found
     */
    public static WorkflowMonitor getWorkflowMonitor() {
        return monitor;
    }

    /**
     * Returns the scheduler.
     *
     * @return the requested scheduler object, or null if not found
     */
    public static Scheduler getScheduler() {
        return scheduler;
    }

    /**
     * Returns the lock service.
     *
     * @return the requested lockservice object, or null if not found
     */
    public static LockService getLockService() {
        return lockService;
    }

    /**
     * Returns the event manager.
     *
     * @return the requested eventmanager object, or null if not found
     */
    public static EventManager getEventManager() {
        return EventManager.getInstance();
    }

    /**
     * Local shutdown listener that binds to localhost only.
     * Listens for a shutdown command and triggers graceful exit.
     */
    private static class ShutdownListener implements Runnable {
        private static final Logger logger = LogManager.getLogger(ShutdownListener.class);

        @Override
        public void run() {
            try (ServerSocket serverSocket = new ServerSocket(Constants.SHUTDOWN_PORT, 1,
                    InetAddress.getByName("127.0.0.1"))) {
                logger.info("Shutdown listener started on port {} (localhost only)", Constants.SHUTDOWN_PORT);
                while (true) {
                    try (Socket socket = serverSocket.accept()) {
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                        String line = reader.readLine();
                        if (Constants.SHUTDOWN_SECRET.equals(line)) {
                            logger.info("Received valid shutdown command via local socket. Exiting...");
                            Main.shutdown();
                            System.exit(0);
                            break;
                        } else {
                            logger.warn("Received invalid shutdown attempt: {}", line);
                        }
                    } catch (Exception e) {
                        logger.warn("Error handling shutdown socket connection", e);
                    }
                }
            } catch (Exception e) {
                logger.error("Shutdown listener failed to start on port {}", Constants.SHUTDOWN_PORT, e);
            }
        }
    }

    public static void shutdown() {
        logger.info("Shutting down workflow engine application");

        if (httpServer != null) {
            logger.info("Stopping workflow HTTP server");
            httpServer.stop();
        }

        if (scheduler != null) {
            scheduler.shutdown();
        }

        if (workflowEngine != null) {
            workflowEngine.shutdown();
        }

        if (timeWheel != null) {
            timeWheel.stop();
        }

        logger.info("Workflow engine application shutdown complete");
        LogManager.shutdown();
    }
}
