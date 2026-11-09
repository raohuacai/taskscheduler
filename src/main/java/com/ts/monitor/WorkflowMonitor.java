/*
 * Description: Background monitor sampling and reporting engine, executor, time-wheel, and scheduler runtime statistics
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.monitor;

import com.ts.core.engine.WorkflowEngine;
import com.ts.core.scheduler.CronScheduler;
import com.ts.core.timewheel.NettyTimeWheel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * Background monitor that periodically samples and reports runtime statistics
 * for the workflow engine, task executor, time wheel, and cron scheduler.
 *
 * <p>Runs on a single-thread {@link java.util.concurrent.ScheduledExecutorService}
 * and emits a structured report every hour via a dedicated Log4j logger
 * ({@code com.ts.monitor.Report}), making it easy to redirect monitoring output
 * to a separate file. Also provides {@link #getMonitoringData()} for the REST
 * monitoring endpoint served by {@link com.ts.server.handler.stats.WorkflowStatsHandler}.
 *
 * <p>Exposes increment methods ({@link #recordWorkflowStart}, {@link #recordTaskSubmit},
 * etc.) so that {@link WorkflowEngine} and {@link com.ts.core.engine.WorkflowInstance}
 * can feed events into the monitor without a hard dependency on its internals.
 */
public class WorkflowMonitor {
    private static final Logger logger = LogManager.getLogger(WorkflowMonitor.class);
    // Dedicated logger for periodic report output
    private static final Logger reportLogger = LogManager.getLogger("com.ts.monitor.Report");

    private final WorkflowEngine workflowEngine;
    private final ThreadPoolExecutor executorService;
    private final ScheduledExecutorService scheduler;
    private final NettyTimeWheel timeWheel;
    private final CronScheduler cronScheduler;

    // Statistical counters for tracking engine activity
    private final AtomicInteger totalWorkflowsStarted = new AtomicInteger(0);
    private final AtomicInteger totalWorkflowsCompleted = new AtomicInteger(0);
    private final AtomicInteger totalTasksSubmitted = new AtomicInteger(0);
    private final AtomicInteger totalTasksCompleted = new AtomicInteger(0);
    private final AtomicInteger totalTasksError = new AtomicInteger(0);

    /**
     * Constructs a new instance, wiring together all required dependencies.
     *
     * @param workflowEngine  the workflow engine instance to monitor
     * @param executorService the thread pool used for task execution
     * @param timeWheel       the Netty time wheel instance
     * @param cronScheduler   the cron scheduler instance
     */
    public WorkflowMonitor(WorkflowEngine workflowEngine, ThreadPoolExecutor executorService,
                           NettyTimeWheel timeWheel, CronScheduler cronScheduler) {
        this.workflowEngine = workflowEngine;
        this.executorService = executorService;
        this.timeWheel = timeWheel;
        this.cronScheduler = cronScheduler;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "workflow-stats");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Starts the background monitoring service
     */
    public void start() {
        // Output a monitoring report every 1 hour
        scheduler.scheduleAtFixedRate(
                this::generateReport,
                1,
                1,
                TimeUnit.HOURS
        );
        logger.info("Workflow stats started - reporting every hour");
    }

    /**
     * Stops the background monitoring service
     */
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Workflow stats stopped");
    }

    /**
     * Records the start of a workflow execution
     *
     * @param workflowId   the workflow definition ID
     * @param wfInstanceId workflow instanceID
     */
    public void recordWorkflowStart(String workflowId, String wfInstanceId) {
        totalWorkflowsStarted.incrementAndGet();
    }

    /**
     * Records the completion of a workflow execution
     *
     * @param wfInstanceId workflow instanceID
     * @param successful   {@code true} if the workflow completed successfully
     */
    public void recordWorkflowCompletion(String wfInstanceId, boolean successful) {
        totalWorkflowsCompleted.incrementAndGet();
    }

    /**
     * Records the submission of a task for execution
     *
     * @param wfInstanceId   workflow instanceID
     * @param taskInstanceId task instanceID
     */
    public void recordTaskSubmission(String wfInstanceId, String taskInstanceId) {
        totalTasksSubmitted.incrementAndGet();
    }

    /**
     * Records the completion of a task execution
     *
     * @param wfInstanceId   workflow instanceID
     * @param taskInstanceId task instanceID
     * @param successful     {@code true} if the task completed successfully
     */
    public void recordTaskCompletion(String wfInstanceId, String taskInstanceId, boolean successful) {
        if (successful) {
            totalTasksCompleted.incrementAndGet();
        } else {
            totalTasksError.incrementAndGet();
        }
    }

    /**
     * Collects monitoring data and serialises it as a JSON object
     *
     * @return a Map containing all current monitoring metrics
     */
    public Map<String, Object> getMonitorData() {
        Map<String, Object> monitorData = new HashMap<>();

        // Add current timestamp to the monitoring snapshot
        monitorData.put("timestamp", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // Thread pool statistics
        Map<String, Object> threadPoolStats = new HashMap<>();
        threadPoolStats.put("corePoolSize", executorService.getCorePoolSize());
        threadPoolStats.put("currentPoolSize", executorService.getPoolSize());
        threadPoolStats.put("activeThreads", executorService.getActiveCount());
        threadPoolStats.put("idleThreads", executorService.getPoolSize() - executorService.getActiveCount());
        threadPoolStats.put("maximumPoolSize", executorService.getMaximumPoolSize());
        threadPoolStats.put("queueSize", executorService.getQueue().size());
        threadPoolStats.put("tasksCompleted", executorService.getCompletedTaskCount());
        threadPoolStats.put("utilization", (float) executorService.getActiveCount() / executorService.getMaximumPoolSize() * 100);
        monitorData.put("threadPool", threadPoolStats);

        // Global task statistics
        Map<String, Object> globalStats = new HashMap<>();
        Map<String, Object> runningWorkflows = workflowEngine.getRunningWorkflowsStats();
        globalStats.put("totalWorkflowsStarted", totalWorkflowsStarted.get());
        globalStats.put("runningWorkflowsCount", runningWorkflows.size());
        globalStats.put("totalWorkflowsCompleted", totalWorkflowsCompleted.get());
        int runningTasksCount = runningWorkflows.values().stream().mapToInt(stats -> (int) ((Map<String, Object>) stats).get("runningTasks")).sum();
        globalStats.put("runningTasksCount", runningTasksCount);
        globalStats.put("totalTasksSubmitted", totalTasksSubmitted.get());
        globalStats.put("totalTasksCompleted", totalTasksCompleted.get());
        globalStats.put("totalTasksError", totalTasksError.get());
        monitorData.put("globalStats", globalStats);

        // Active workflow statistics
        monitorData.put("activeWorkflows", runningWorkflows);

        // Time-wheel statistics
        monitorData.put("timeWheel", timeWheel.getStats());

        // Cron scheduler statistics
        monitorData.put("scheduler", cronScheduler.getStats());

        return monitorData;
    }

    /**
     * Generates a formatted plain-text monitoring report
     */
    private void generateReport() {
        try {
            StringBuilder report = new StringBuilder();
            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            report.append("\n============================================================\n");
            report.append(String.format("=== WORKFLOW EXECUTION REPORT (%s) ===\n", timestamp));
            report.append("============================================================\n");

            // Thread pool statistics
            appendThreadPoolStats(report);

            // Global task statistics
            appendGlobalTaskStats(report);

            // Active workflow statistics
            appendActiveWorkflowStats(report);

            // Time-wheel statistics
            appendTimeWheelStats(report);

            // Cron scheduler statistics
            appendSchedulerStats(report);

            report.append("\n============================================================\n");
            report.append("=== END OF REPORT ===\n");
            report.append("============================================================\n");

            // Use the dedicated report logger to output the monitoring report
            reportLogger.info(report.toString());

            // Display a brief summary on the console
            logger.info("Workflow stats report generated. See monitor.log for details.");
        } catch (Exception e) {
            logger.error("Error generating workflow stats report", e);
        }
    }

    /**
     * Appends thread-pool statistics to the monitoring data map
     */
    private void appendThreadPoolStats(StringBuilder report) {
        report.append("\n== Thread Pool Statistics ==\n");
        report.append(String.format("Core Pool Size: %d\n", executorService.getCorePoolSize()));
        report.append(String.format("Current Pool Size: %d\n", executorService.getPoolSize()));
        report.append(String.format("Active Threads: %d\n", executorService.getActiveCount()));
        report.append(String.format("Idle Threads: %d\n", executorService.getPoolSize() - executorService.getActiveCount()));
        report.append(String.format("Maximum Pool Size: %d\n", executorService.getMaximumPoolSize()));
        report.append(String.format("Queue Size: %d\n", executorService.getQueue().size()));
        report.append(String.format("Tasks Completed: %d\n", executorService.getCompletedTaskCount()));
        report.append(String.format("Thread Pool Utilization: %.2f%%\n",
                (float) executorService.getActiveCount() / executorService.getMaximumPoolSize() * 100));
    }

    /**
     * Appends global task statistics to the monitoring data map
     */
    private void appendGlobalTaskStats(StringBuilder report) {
        Map<String, Object> runningWorkflows = workflowEngine.getRunningWorkflowsStats();
        int currentlyRunningTasks = runningWorkflows.values().stream().mapToInt(stats -> (int) ((Map<String, Object>) stats).get("runningTasks")).sum();
        report.append("\n== Global Execution Statistics ==\n");
        report.append(String.format("Total Workflows Started: %d\n", totalWorkflowsStarted.get()));
        report.append(String.format("Currently Running Workflows: %d\n", runningWorkflows.size()));
        report.append(String.format("Currently Running Tasks: %d\n", currentlyRunningTasks));
        report.append(String.format("Total Tasks Submitted: %d\n", totalTasksSubmitted.get()));
        report.append(String.format("Total Tasks Completed: %d\n", totalTasksCompleted.get()));
        report.append(String.format("Total Tasks Error: %d\n", totalTasksError.get()));
    }

    /**
     * Appends active-workflow statistics to the monitoring data map
     */
    private void appendActiveWorkflowStats(StringBuilder report) {
        Map<String, Object> runningWorkflows = workflowEngine.getRunningWorkflowsStats();

        report.append("\n== Active Workflow Details ==\n");
        if (runningWorkflows.isEmpty()) {
            report.append("No active workflows\n");
            return;
        }

        report.append(String.format("%-36s | %-20s | %-15s | %-15s | %-15s | %-15s | %-15s\n",
                "Workflow Instance ID", "Workflow ID", "Total Tasks", "Running Tasks", "Completed", "Errors", "Running Days"));
        report.append("------------------------------------------------------------------------------------------------\n");

        for (Map.Entry<String, Object> entry : runningWorkflows.entrySet()) {
            String wfInstanceId = entry.getKey();
            Map<String, Object> stats = (Map<String, Object>) entry.getValue();

            report.append(String.format("%-36s | %-20s | %-15d | %-15d | %-15d | %-15d | %-15d\n",
                    wfInstanceId,
                    stats.get("workflowId"),
                    (int) stats.get("totalTasks"),
                    (int) stats.get("runningTasks"),
                    (int) stats.get("completedTasks"),
                    (int) stats.get("errorTasks"),
                    stats.containsKey("runningDays") ? stats.get("runningDays") : 0
            ));
        }
    }

    /**
     * Appends time-wheel statistics to the monitoring data map
     */
    private void appendTimeWheelStats(StringBuilder report) {
        Map<String, Object> timeWheelStats = timeWheel.getStats();

        report.append("\n== NettyTimeWheel Statistics ==\n");
        report.append(String.format("Running Status: %s\n", timeWheelStats.get("running")));
        report.append(String.format("Total Active Tasks: %d\n", timeWheelStats.get("totalActiveTasks")));
        report.append(String.format("One-time Tasks: %d\n", timeWheelStats.get("oneTimeTasks")));
        report.append(String.format("Periodic Tasks: %d\n", timeWheelStats.get("periodicTasks")));
        report.append(String.format("Tasks Added (since start): %d\n", timeWheelStats.get("tasksAdded")));
        report.append(String.format("Tasks Executed: %d\n", timeWheelStats.get("tasksExecuted")));
        report.append(String.format("Tasks Failed: %d\n", timeWheelStats.get("tasksFailed")));
        report.append(String.format("Tasks Cancelled: %d\n", timeWheelStats.get("tasksCancelled")));
        report.append(String.format("Tasks Expired: %d\n", timeWheelStats.get("tasksExpired")));

        if (timeWheelStats.containsKey("nextExecutionTasks") && !((Map<String, Long>) timeWheelStats.get("nextExecutionTasks")).isEmpty()) {
            report.append("\nUpcoming Task Executions:\n");
            report.append(String.format("%-36s | %-20s | %-30s\n", "Task ID", "Type", "Execution Time"));
            report.append("-------------------------------------------------------------------------\n");

            Map<String, Long> nextExecutions = (Map<String, Long>) timeWheelStats.get("nextExecutionTasks");
            nextExecutions.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .limit(5)  // Show only the 5 most recent tasks
                    .forEach(entry -> {
                        String taskId = entry.getKey();
                        long executionTime = entry.getValue();
                        String taskType = (Boolean) ((Map<String, Object>) timeWheelStats.get("taskDetails")).get(taskId + ".periodic") ?
                                "Periodic" : "One-time";

                        report.append(String.format("%-36s | %-20s | %-30s\n",
                                taskId, taskType, new Date(executionTime)));
                    });
        }
    }

    /**
     * Appends cron-scheduler statistics to the monitoring data map
     */
    private void appendSchedulerStats(StringBuilder report) {
        Map<String, Object> schedulerStats = cronScheduler.getStats();

        report.append("\n== CronScheduler Statistics ==\n");
        report.append(String.format("Total Scheduled Workflows: %d\n", schedulerStats.get("totalScheduledWorkflows")));
        report.append(String.format("Workflows Executed (since start): %d\n", schedulerStats.get("workflowsExecuted")));
        report.append(String.format("Failed Executions: %d\n", schedulerStats.get("failedExecutions")));
        report.append(String.format("Maintenance Cycles: %d\n", schedulerStats.get("maintenanceCycles")));
        report.append(String.format("Stale Workflows Removed: %d\n", schedulerStats.get("staleWorkflowsRemoved")));

        if (schedulerStats.containsKey("scheduledWorkflows") && !((Map<String, Object>) schedulerStats.get("scheduledWorkflows")).isEmpty()) {
            report.append("\nScheduled Workflows:\n");
            report.append(String.format("%-36s | %-25s | %-30s\n", "Workflow ID", "Cron Expression", "Next Run Time"));
            report.append("-------------------------------------------------------------------------\n");

            Map<String, Object> workflows = (Map<String, Object>) schedulerStats.get("scheduledWorkflows");
            workflows.entrySet().stream()
                    .sorted((e1, e2) -> {
                        Date d1 = (Date) ((Map<String, Object>) e1.getValue()).get("nextRunTime");
                        Date d2 = (Date) ((Map<String, Object>) e2.getValue()).get("nextRunTime");
                        return d1.compareTo(d2);
                    })
                    .limit(10)  // Show only the 10 most recent workflows
                    .forEach(entry -> {
                        String workflowId = entry.getKey();
                        Map<String, Object> details = (Map<String, Object>) entry.getValue();
                        String cronExpression = (String) details.get("cronExpression");
                        Date nextRunTime = (Date) details.get("nextRunTime");

                        report.append(String.format("%-36s | %-25s | %-30s\n",
                                workflowId, cronExpression, nextRunTime));
                    });
        }
    }
}