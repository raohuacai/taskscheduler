/*
 * Description: Task type that suspends workflow execution until a target time or offset elapses
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.task.core;

import com.ts.common.constants.Constants;
import com.ts.common.utils.ParamUtils;
import com.ts.exception.TaskExecutionException;
import com.ts.service.impl.TaskLogService;
import com.ts.task.AbstractTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
/**
 * Task type that suspends workflow execution until a specified absolute time
 * or until a configurable time offset elapses.
 *
 * <p>The target time ({@code waitTargetTime}) and offset ({@code waitOffsetSeconds})
 * are combined to compute a deadline. If the deadline is already in the past the
 * task exits immediately. Otherwise it sleeps in short segments
 * ({@code waitCheckInterval} ms, minimum 100 ms) so that cancellation requests
 * are honoured promptly without requiring a full-duration uninterruptible sleep.
 *
 * <p>Flexible time format: the target time string is matched against a list of
 * {@link java.time.format.DateTimeFormatter} patterns in priority order; missing
 * date components default to today, missing time components default to 00:00:00.
 *
 * <p>Clock skew correction: remaining time is recalculated on every wake-up,
 * so NTP adjustments and system sleep/resume events do not cause drift.
 */
public class WaitTask extends AbstractTask {
    private static final Logger logger = LogManager.getLogger(WaitTask.class);

    // List of supported target-time format patterns, ordered by parsing priority
    private static final List<DateTimeFormatter> TARGET_TIME_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH"),
            DateTimeFormatter.ofPattern("HH:mm:ss"),
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("HH"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    );

    private static final int DEFAULT_CHECK_INTERVAL_MS = 1000;  // Default check interval in milliseconds (1000 ms = 1 second)

    // Task parameters (camelCase names matching parameter keys)
    private String waitTargetTime;
    private int waitOffsetSeconds;
    private int waitCheckInterval;

    // The computed deadline timestamp derived from target time and offset
    private LocalDateTime deadline;

    @Override
    /**
      * Prepares resources and reads task parameters before the main action.
      * Called by {@link AbstractTask#execute} before {@link #action}.
      *
      * @param parameters  merged task parameter map supplied by the workflow engine
      * @throws Exception  if a required parameter is missing or resource initialisation fails
      */
    public void preAction(Map<String, Object> parameters) throws Exception {
        super.preAction(parameters);

        // Step 1: read task parameters
        waitTargetTime = ParamUtils.getParameter(parameters, Constants.PARAM_WAIT_TARGET_TIME, null);
        waitOffsetSeconds = ParamUtils.getIntParameter(parameters, Constants.PARAM_WAIT_OFFSET_SECONDS, 0);
        waitCheckInterval = ParamUtils.getIntParameter(parameters, Constants.PARAM_WAIT_CHECK_INTERVAL, DEFAULT_CHECK_INTERVAL_MS);
        if (waitCheckInterval < 100) {
            waitCheckInterval = 100; // Enforce minimum check interval of 100 ms
        }

        // Step 2: determine the base time
        LocalDateTime baseTime;
        if (waitTargetTime != null && !waitTargetTime.trim().isEmpty()) {
            baseTime = parseTargetTime(waitTargetTime.trim());
            logger.debug("Parsed target time '{}' to: {}", waitTargetTime, baseTime);
        } else {
            baseTime = LocalDateTime.now();
            logger.debug("No target time provided, using current time as base: {}", baseTime);
        }

        // Step 3: apply the offset to compute the final deadline
        deadline = baseTime.plusSeconds(waitOffsetSeconds);

        // Step 4: calculate the total wait duration in milliseconds (for logging)
        long nowMillis = System.currentTimeMillis();
        long deadlineMillis = deadline.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long waitMillis = deadlineMillis - nowMillis;

        logger.info("WaitTask initialized: baseTime={}, offset={}s, deadline={}, needWait={}ms. Task: {}, {}, {}",
                baseTime, waitOffsetSeconds, deadline, waitMillis, taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                String.format("Wait until %s (offset=%ds), estimated wait %d ms", deadline, waitOffsetSeconds, waitMillis));
    }

    @Override
    /**
      * Executes the core task logic.
      * Called by {@link AbstractTask#execute} after {@link #preAction}.
      *
      * @param parameters  merged task parameter map
      * @throws Exception  if the task logic fails
      */
    public void action(Map<String, Object> parameters) throws Exception {
        long deadlineMillis = deadline.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long remain = deadlineMillis - System.currentTimeMillis();

        // If the deadline has already passed, return immediately without waiting
        if (remain <= 0) {
            logger.info("WaitTask deadline already passed, skipping wait. Task: {}, {}, {}",
                    taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Wait skipped (deadline already reached)");
            return;
        }

        logger.info("Start waiting for {} ms until {}. Task: {}, {}, {}",
                remain, deadline, taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                "Start waiting for " + remain + " ms");

        long waited = 0;
        int logCounter = 0;
        final int LOG_INTERVAL_CYCLES = 60; // Log progress approximately every 60 cycles (every ~60 s at a 1-second interval)

        // Segmented wait loop: each sleep is capped at waitCheckInterval to respond promptly to cancellation and clock adjustments
        while (remain > 0 && !canceled.get()) {
            long sleepMs = Math.min(remain, waitCheckInterval);
            try {
                TimeUnit.MILLISECONDS.sleep(sleepMs);
            } catch (InterruptedException e) {
                // Do NOT restore the interrupt flag — consistent with AbstractTask.execute().
                // Oracle JDBC throws InterruptedIOException when the thread is in interrupted state.
                if (canceled.get()) {
                    logger.warn("WaitTask interrupted by cancel request. Task: {}, {}, {}",
                            taskId, taskName, taskInstanceId);
                    TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Wait canceled");
                    // Throw instead of return so WorkflowInstance.executeTask() detects
                    // task.isCanceled()==true and keeps status as CANCELED (not COMPLETED).
                    throw new TaskExecutionException(
                            "WaitTask canceled while waiting", e, taskId, taskInstanceId);
                }
                // Unexpected non-cancellation interrupt (rare); wrap and rethrow
                throw new TaskExecutionException("WaitTask interrupted unexpectedly", e, taskId, taskInstanceId);
            }

            // Recalculate remaining time to account for NTP clock skew or system time adjustments
            long now = System.currentTimeMillis();
            remain = deadlineMillis - now;
            waited += sleepMs;

            // Log wait progress periodically (every LOG_INTERVAL_CYCLES iterations)
            if (++logCounter % LOG_INTERVAL_CYCLES == 0 && remain > 0) {
                logger.debug("WaitTask in progress: remaining {} ms, waited {} ms. Task: {}, {}, {}",
                        remain, waited, taskId, taskName, taskInstanceId);
            }
        }

        // Loop exited via canceled.get()==true (cancel arrived between sleep cycles,
        // without triggering InterruptedException).
        // Throw instead of return so status stays CANCELED, not COMPLETED.
        if (canceled.get()) {
            logger.info("WaitTask finished due to cancellation. Task: {}, {}, {}",
                    taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Wait canceled");
            throw new TaskExecutionException(
                    "WaitTask canceled while waiting", null, taskId, taskInstanceId);
        }

        logger.info("WaitTask completed, actual waited approximately {} ms. Task: {}, {}, {}",
                waited, taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                "Wait finished, actual waited " + waited + " ms");

        // Write the actual wait outcome back into the parameters so downstream tasks can observe it
        Map<String, Object> updates = new HashMap<>();
        updates.put("waitActualMs", waited);
        updates.put("waitUntilEpoch", deadlineMillis);
        saveParams(parameters, updates);
    }

    @Override
    /**
     * Cleanup the resources.
     */
    public void cleanupResources() {
        // Clear the interrupt flag so the thread pool can safely reuse this thread
        Thread.interrupted();
        super.cleanupResources();
    }

    /**
     * Parses a flexible-format target time string.
     * Missing parts are auto-completed: date defaults to today; time defaults to 00:00:00.
     *
     * @param timeStr the time string to parse
     * @return the parsed LocalDateTime value
     * @throws TaskExecutionException if the string cannot be parsed with any supported format
     */
    private LocalDateTime parseTargetTime(String timeStr) throws TaskExecutionException {
        // Step 1: attempt to parse as a full datetime (date + time)
        for (DateTimeFormatter formatter : TARGET_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(timeStr, formatter);
            } catch (DateTimeParseException ignored) {
                // Continue to the next format
            }
        }

        // Step 2: attempt to parse as a date-only string (time defaults to 00:00:00)
        for (DateTimeFormatter formatter : TARGET_TIME_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(timeStr, formatter);
                return date.atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // Continue to the next format
            }
        }

        // Step 3: attempt to parse as a time-only string (date defaults to today)
        for (DateTimeFormatter formatter : TARGET_TIME_FORMATTERS) {
            try {
                LocalTime time = LocalTime.parse(timeStr, formatter);
                return LocalDateTime.of(LocalDate.now(), time);
            } catch (DateTimeParseException ignored) {
                // Continue to the next format
            }
        }

        throw new TaskExecutionException(
                "Cannot parse waitTargetTime: '" + timeStr + "'. Supported formats: yyyy-MM-dd HH:mm:ss, HH:mm, HH, etc.",
                null, taskId, taskInstanceId);
    }
}
