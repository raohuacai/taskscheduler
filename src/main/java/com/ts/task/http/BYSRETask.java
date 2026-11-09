/*
 * Description: SRE Job invocation task with token-based authentication and status polling
 * Author: Rock Rao
 * Date: 4/23/2026
 * Version: 1.1
 */
package com.ts.task.http;

import com.ts.common.constants.Constants;
import com.ts.common.utils.JsonUtils;
import com.ts.common.utils.ParamUtils;
import com.ts.exception.TaskExecutionException;
import com.ts.exception.TaskTimeoutException;
import com.ts.service.impl.TaskLogService;
import com.ts.task.timeout.TimeoutTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Task type that triggers an SRE Job, polls its status until completion,
 * and captures the final result.
 * <p>
 * ── Authentication flow ───────────────────────────────────────────────────────
 * <ol>
 *   <li>POST to {@code /shell/rest/authentication/login/1.0?token_type=jwt}
 *       with Basic Auth (username/password) to obtain a JWT token.</li>
 *   <li>POST to {@code /restapi/platform/sre/v1/jobs/jobId?user=<user>}
 *       with the job JSON payload and Bearer token to create a job.
 *       Returns a {@code jobId}.</li>
 *   <li>GET to {@code /restapi/platform/sre/v1/jobs/{jobId}} periodically
 *       until {@code jobState} becomes {@code "6"} (completed) or the task
 *       is interrupted by a timeout/cancel signal from {@link TimeoutTask}.</li>
 * </ol>
 * <p>
 * ── Input flexibility ─────────────────────────────────────────────────────────
 * Job input can be supplied either as a file path ({@code sreJsonFile})
 * or as an inline JSON string ({@code sreJsonContent}). If both are provided,
 * the file content takes precedence.
 * <p>
 * ── HTTP resilience ───────────────────────────────────────────────────────────
 * SSL verification can be disabled via {@code sreSkipSslVerify=true}
 * (development only). HTTP calls are retried up to {@code sreHttpRetryCount}
 * times (default 3) with {@code sreHttpRetryInterval} seconds between attempts.
 * <p>
 * ── Cancel / timeout handling ─────────────────────────────────────────────────
 * Before each network operation and during polling, it checks the {@code canceled}
 * flag and throws {@link TaskExecutionException} to abort immediately.
 * Hard timeout is inherited from {@link TimeoutTask}: when the timeout monitor
 * interrupts the executing thread, {@link Thread#sleep} throws
 * {@link InterruptedException} which is re-thrown directly for the parent
 * {@link TimeoutTask#execute} to classify as timeout or cancel.
 * Blocking HTTP I/O interrupted by the timeout monitor raises {@link InterruptedIOException}
 * (a subclass of {@link IOException}, not {@link InterruptedException}). It is caught
 * before the generic IOException handler and wrapped into a {@link TaskExecutionException}.
 * The parent {@link TimeoutTask#execute} catches all exceptions and checks
 * {@code timeouted.get()} to decide whether to re-throw as {@link TaskTimeoutException}
 * or as a plain {@link TaskExecutionException}, so no special propagation is needed here.
 * <p>
 * Author: Rock Rao
 * Date: 2026
 * Version: 1.1
 */
public class BYSRETask extends TimeoutTask {
    private static final Logger logger = LogManager.getLogger(BYSRETask.class);

    // ── Task parameters ───────────────────────────────────────────────────────
    private String sreUrl;
    private String userName;
    private String password;
    private String jsonFile;
    private String jsonContent;
    private int pollInterval;       // seconds
    private boolean skipSslVerify;
    private int httpRetryCount;
    private int httpRetryInterval;  // seconds

    // ── Derived state ─────────────────────────────────────────────────────────
    private String jsonBody;        // resolved JSON payload for job creation
    private String bearerToken;     // JWT token obtained after login

    // ── API endpoint paths (relative to sreUrl) ───────────────────────────────
    private static final String TOKEN_PATH = "/shell/rest/authentication/login/1.0?token_type=jwt";
    private static final String JOBS_PATH = "/restapi/platform/sre/v1/jobs/";

    // ── Default values (all durations in seconds, consistent with other tasks) ─
    private static final int DEFAULT_POLL_INTERVAL = 5;   // seconds
    private static final int DEFAULT_HTTP_RETRY_COUNT = 3;
    private static final int DEFAULT_HTTP_RETRY_INTERVAL = 5;  // seconds
    private static final int CONNECT_TIMEOUT = 30;  // seconds
    private static final int READ_TIMEOUT = 60;  // seconds

    /**
     * Prepares resources and reads task parameters before the main action.
     * Called by {@link AbstractTask#execute} before {@link #action}.
     *
     * @param parameters merged task parameter map supplied by the workflow engine
     * @throws Exception if a required parameter is missing or resource initialisation fails
     */
    @Override
    public void preAction(Map<String, Object> parameters) throws Exception {
        super.preAction(parameters);

        // Required parameters
        sreUrl = ParamUtils.getRequiredParameter(parameters, Constants.SRE_URL,
                taskId, taskInstanceId).trim();
        userName = ParamUtils.getRequiredParameter(parameters, Constants.SRE_USER,
                taskId, taskInstanceId).trim();
        password = ParamUtils.getRequiredParameter(parameters, Constants.SRE_PASSWORD,
                taskId, taskInstanceId);

        // Optional parameters with defaults
        jsonFile = ParamUtils.getParameter(parameters, Constants.SRE_JSON_FILE, "").trim();
        jsonContent = ParamUtils.getParameter(parameters, Constants.SRE_JSON_CONTENT, "").trim();
        pollInterval = ParamUtils.getIntParameter(parameters, Constants.SRE_POLL_INTERVAL,
                DEFAULT_POLL_INTERVAL);
        skipSslVerify = ParamUtils.getBooleanParameter(parameters, Constants.SRE_SKIP_SSL_VERIFY, false);
        httpRetryCount = ParamUtils.getIntParameter(parameters, Constants.SRE_HTTP_RETRY_COUNT,
                DEFAULT_HTTP_RETRY_COUNT);
        httpRetryInterval = ParamUtils.getIntParameter(parameters, Constants.SRE_HTTP_RETRY_INTERVAL,
                DEFAULT_HTTP_RETRY_INTERVAL);

        // Validate URL format
        if (!sreUrl.startsWith("http://") && !sreUrl.startsWith("https://")) {
            throw new TaskExecutionException(
                    "sreUrl must start with http:// or https://: " + sreUrl,
                    null, taskId, taskInstanceId);
        }
        if (sreUrl.endsWith("/")) {
            sreUrl = sreUrl.substring(0, sreUrl.length() - 1);
        }

        // Load JSON body (file takes precedence over inline content)
        if (!jsonFile.isEmpty()) {
            jsonBody = readJsonFromFile(jsonFile);
            logger.info("Loaded SRE job JSON from file: {}, Task: {}, {}, {}",
                    jsonFile, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                    "SRE Job JSON loaded from file: " + jsonFile);
        } else if (!jsonContent.isEmpty()) {
            jsonBody = normalizeJsonString(jsonContent);
            logger.info("Using inline SRE job JSON content, Task: {}, {}, {}",
                    taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                    "SRE Job JSON loaded from inline content");
        } else {
            throw new TaskExecutionException(
                    "Either sreJsonFile or sreJsonContent must be provided",
                    null, taskId, taskInstanceId);
        }

        // Validate JSON format early
        try {
            JsonUtils.jsonToMap(jsonBody);
        } catch (Exception e) {
            throw new TaskExecutionException(
                    "Invalid SRE job JSON: " + e.getMessage(), e, taskId, taskInstanceId);
        }

        logger.info("BYSRETask preAction completed: url={}, user={}, taskTimeout={}s, " +
                        "pollInterval={}s, httpRetry={}/{}s, Task: {}, {}, {}",
                sreUrl, userName, timeout, pollInterval,
                httpRetryCount, httpRetryInterval,
                taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                "SRE Job: " + sreUrl + " user=" + userName
                        + " timeout=" + timeout + "s"
                        + " pollInterval=" + pollInterval + "s"
                        + " httpRetry=" + httpRetryCount + "/" + httpRetryInterval + "s");
    }

    /**
     * Executes the core task logic: authenticates, creates the SRE job, then
     * polls until completion.
     * Called by {@link AbstractTask#execute} after {@link #preAction}.
     *
     * @param parameters merged task parameter map
     * @throws Exception if the task logic fails
     */
    @Override
    public void action(Map<String, Object> parameters) throws Exception {
        // Step 1: Obtain bearer token
        bearerToken = obtainToken();
        if (bearerToken == null || bearerToken.isEmpty()) {
            throw new TaskExecutionException(
                    "Failed to obtain authentication token from SRE server",
                    null, taskId, taskInstanceId);
        }
        logger.info("Bearer token obtained successfully, Task: {}, {}, {}",
                taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                "SRE authentication successful");

        // Step 2: Create job and get jobId
        String jobId = createJob();
        logger.info("SRE Job created, jobId: {}, Task: {}, {}, {}",
                jobId, taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                "SRE Job created, jobId: " + jobId);
        saveParam(parameters, Constants.SRE_OUTPUT_JOB_ID, jobId);

        // Step 3: Poll until completion or interrupted by timeout/cancel.
        Map<String, Object> finalResult = pollJobStatus(jobId);
        Object rcObj = finalResult.get("returnCode");
        int returnCode = rcObj != null ? Integer.parseInt(rcObj.toString()) : -1;
        String jobState = String.valueOf(finalResult.getOrDefault("jobState", ""));

        logger.info("SRE Job completed: jobId={}, returnCode={}, jobState={}, Task: {}, {}, {}",
                jobId, returnCode, jobState, taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                "SRE Job finished: returnCode=" + returnCode + ", jobState=" + jobState);

        // Store outputs
        Map<String, Object> updates = new HashMap<>();
        updates.put(Constants.SRE_OUTPUT_RETURN_CODE, returnCode);
        updates.put(Constants.SRE_OUTPUT_RESPONSE, JsonUtils.toJson(finalResult));
        saveParams(parameters, updates);

        // Success condition: jobState == "6" and returnCode == 0
        if (!"6".equals(jobState)) {
            throw new TaskExecutionException(
                    "SRE Job did not reach completed state (jobState=" + jobState + ")",
                    null, taskId, taskInstanceId);
        }
        if (returnCode != 0) {
            throw new TaskExecutionException(
                    "SRE Job finished with non-zero return code: " + returnCode,
                    null, taskId, taskInstanceId);
        }
    }

    /**
     * Reads the content of a JSON file into a string and normalizes it.
     * SRE job JSON files are expected to be small; {@code int} is sufficient
     * for the byte array allocation. File existence is verified before reading
     * to produce a clear error message rather than a raw IOException.
     *
     * @param filePath absolute or relative path to the JSON file
     * @return normalized, compact JSON string
     * @throws TaskExecutionException if file not found, unreadable, or invalid JSON
     */
    private String readJsonFromFile(String filePath) throws TaskExecutionException {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            throw new TaskExecutionException(
                    "JSON file not found or not readable: " + filePath,
                    null, taskId, taskInstanceId);
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = fis.read(data);
            if (read != file.length()) {
                throw new IOException("Incomplete file read");
            }
            String content = new String(data, StandardCharsets.UTF_8);
            return normalizeJsonString(content);
        } catch (IOException e) {
            throw new TaskExecutionException(
                    "Failed to read JSON file: " + filePath + " - " + e.getMessage(),
                    e, taskId, taskInstanceId);
        }
    }

    /**
     * Ensures the JSON string is valid and compact (no pretty-print).
     *
     * @param raw raw JSON string
     * @return compact JSON string
     * @throws TaskExecutionException if the input is not valid JSON
     */
    private String normalizeJsonString(String raw) throws TaskExecutionException {
        try {
            Map<String, Object> map = JsonUtils.jsonToMap(raw);
            return JsonUtils.toJson(map);
        } catch (Exception e) {
            throw new TaskExecutionException(
                    "Invalid JSON content: " + e.getMessage(), e, taskId, taskInstanceId);
        }
    }

    /**
     * Obtains a JWT token using Basic Authentication.
     *
     * @return bearer token string (never null)
     * @throws InterruptedException   re-thrown for parent TimeoutTask to classify
     * @throws TaskExecutionException if the authentication fails
     */
    private String obtainToken() throws InterruptedException, TaskExecutionException {
        checkCanceled();

        String tokenUrl = sreUrl + TOKEN_PATH;
        logger.debug("Requesting token from: {}", tokenUrl);

        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(
                (userName + ":" + password).getBytes(StandardCharsets.UTF_8));
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", basicAuth);
        headers.put("Content-Type", "application/json");
        headers.put("Charset", "UTF-8");

        Map<String, Object> response = executeHttpRequest("POST", tokenUrl, headers, null);
        String status = String.valueOf(response.get("Status"));
        if (!"Success".equals(status)) {
            throw new TaskExecutionException(
                    "Token response Status not 'Success': " + response,
                    null, taskId, taskInstanceId);
        }
        Object tokenObj = response.get("accessToken");
        if (tokenObj == null || tokenObj.toString().isEmpty()) {
            throw new TaskExecutionException(
                    "Token not found in response (missing 'accessToken' field)",
                    null, taskId, taskInstanceId);
        }
        return tokenObj.toString();
    }

    /**
     * Creates an SRE Job and returns the job ID.
     * If the server returns HTTP 401 (token expired immediately after login),
     * the token is refreshed once and the request is retried.
     *
     * @return job ID assigned by the server
     * @throws InterruptedException   re-thrown for parent TimeoutTask to classify
     * @throws TaskExecutionException if the job creation fails
     */
    private String createJob() throws InterruptedException, TaskExecutionException {
        checkCanceled();

        String createUrl = sreUrl + JOBS_PATH + "jobId?user=" + userName;
        logger.debug("Creating job at: {}", createUrl);

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + bearerToken);
        headers.put("Content-Type", "application/json");
        headers.put("Charset", "UTF-8");

        try {
            Map<String, Object> response = executeHttpRequest("POST", createUrl, headers, jsonBody);
            Object idObj = response.get("id");
            if (idObj == null || idObj.toString().isEmpty()) {
                throw new TaskExecutionException(
                        "Job ID not found in response (missing 'id' field): " + response,
                        null, taskId, taskInstanceId);
            }
            return idObj.toString();
        } catch (TaskExecutionException e) {
            // Edge case: token expired immediately; refresh once and retry
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                logger.warn("Token expired during job creation, refreshing...");
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                        "Token expired, refreshing and retrying job creation");
                bearerToken = obtainToken();
                headers.put("Authorization", "Bearer " + bearerToken);
                Map<String, Object> response = executeHttpRequest("POST", createUrl, headers, jsonBody);
                Object idObj = response.get("id");
                if (idObj == null || idObj.toString().isEmpty()) {
                    throw new TaskExecutionException(
                            "Job ID not found in response after token refresh: " + response,
                            null, taskId, taskInstanceId);
                }
                return idObj.toString();
            }
            throw e;
        }
    }

    /**
     * Polls the job status until completion or the task is interrupted by timeout/cancel.
     * <p>
     * Polls in a loop with {@link Thread#sleep} between iterations. If the task-level
     * timeout elapses, {@link TimeoutTask}'s monitor interrupts the executing thread;
     * the resulting {@link InterruptedException} is re-thrown directly so that the parent
     * {@link TimeoutTask#execute} can classify it as timeout or cancel via {@code timeouted}.
     * Explicit cancellation is detected by {@link #checkCanceled()} at the top of each
     * iteration before the next network call.
     * <p>
     * Poll progress is logged at DEBUG level; polling can be very frequent so INFO
     * would generate excessive noise in production logs.
     *
     * @param jobId the job identifier
     * @return the final job status map (with jobState="6")
     * @throws InterruptedException   re-thrown for parent TimeoutTask to classify as timeout or cancel
     * @throws TaskExecutionException if cancellation is detected or a status poll fails
     */
    private Map<String, Object> pollJobStatus(String jobId)
            throws InterruptedException, TaskExecutionException {
        int pollCount = 0;
        while (true) {
            checkCanceled();   // Fast-fail on explicit user cancel

            Map<String, Object> status = getJobStatus(jobId);
            String jobState = String.valueOf(status.getOrDefault("jobState", ""));
            Object rcObj = status.get("returnCode");
            int returnCode = rcObj != null ? Integer.parseInt(rcObj.toString()) : -1;
            pollCount++;

            logger.debug("Polling SRE job {}: state={}, returnCode={}, pollCount={}, Task: {}, {}, {}",
                    jobId, jobState, returnCode, pollCount, taskId, taskName, taskInstanceId);

            if ("6".equals(jobState)) {
                return status;
            }

            // Re-throw InterruptedException directly so TimeoutTask.execute() can classify
            // as timeout or cancel via timeouted.get().
            Thread.sleep(pollInterval * 1000L);
        }
    }

    /**
     * Retrieves the current status of a job.
     * If the server returns HTTP 401 (token expired), the token is refreshed
     * once and the request is retried.
     *
     * @param jobId the job identifier
     * @return map representing the job status
     * @throws InterruptedException   re-thrown for parent TimeoutTask to classify
     * @throws TaskExecutionException if the status request fails
     */
    private Map<String, Object> getJobStatus(String jobId)
            throws InterruptedException, TaskExecutionException {
        String statusUrl = sreUrl + JOBS_PATH + jobId;
        logger.debug("Querying job status: {}", statusUrl);

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + bearerToken);

        try {
            return executeHttpRequest("GET", statusUrl, headers, null);
        } catch (TaskExecutionException e) {
            // If token expired (401), refresh once and retry
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                logger.warn("Bearer token expired, refreshing...");
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                        "Token expired during poll, refreshing and retrying");
                bearerToken = obtainToken();
                headers.put("Authorization", "Bearer " + bearerToken);
                return executeHttpRequest("GET", statusUrl, headers, null);
            }
            throw e;
        }
    }

    /**
     * Unified HTTP request executor with retry, SSL skip, and response parsing.
     * <p>
     * Retries are performed on 5xx, 408, 429, and network I/O errors.
     * Cancellation is checked before every attempt.
     * <p>
     * Interrupt handling:
     * <ul>
     *   <li>{@link Thread#sleep} during retry waits throws {@link InterruptedException}
     *       on thread interruption; this is re-thrown directly so that the parent
     *       {@link TimeoutTask#execute} can classify it as timeout or cancel.</li>
     *   <li>Blocking HTTP I/O interrupted by the timeout monitor raises
     *       {@link InterruptedIOException} (a subclass of {@link IOException}, invisible
     *       to the framework's {@link InterruptedException} path). It is caught BEFORE
     *       the generic {@link IOException} handler and wrapped into a
     *       {@link TaskExecutionException}. The parent {@link TimeoutTask#execute}
     *       then checks {@code timeouted.get()} to classify it as timeout or cancel.</li>
     * </ul>
     *
     * @param method      HTTP method (GET, POST, etc.)
     * @param urlStr      full URL
     * @param headers     request headers (may be null)
     * @param requestBody request body as JSON string (may be null for GET)
     * @return parsed response body as a Map
     * @throws InterruptedException   re-thrown from retry sleep for parent TimeoutTask to classify
     * @throws TaskExecutionException on non-2xx status, network error, or malformed JSON
     */
    private Map<String, Object> executeHttpRequest(String method, String urlStr,
                                                   Map<String, String> headers,
                                                   String requestBody)
            throws InterruptedException, TaskExecutionException {
        TaskExecutionException lastException = null;
        int maxAttempts = httpRetryCount + 1;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            checkCanceled();

            if (attempt > 1) {
                logger.debug("HTTP retry {}/{} after {}s, Task: {}, {}, {}",
                        attempt - 1, httpRetryCount, httpRetryInterval,
                        taskId, taskName, taskInstanceId);
                // Re-throw directly so TimeoutTask.execute() can classify as timeout or cancel.
                Thread.sleep(httpRetryInterval * 1000L);
            }

            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();

                if (skipSslVerify && conn instanceof HttpsURLConnection) {
                    disableSslVerification((HttpsURLConnection) conn);
                }

                conn.setRequestMethod(method);
                conn.setConnectTimeout(CONNECT_TIMEOUT * 1000);
                conn.setReadTimeout(READ_TIMEOUT * 1000);
                conn.setDoInput(true);

                if (headers != null) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        conn.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }

                if (requestBody != null && !requestBody.isEmpty()
                        && ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
                    conn.setDoOutput(true);
                    byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
                    conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(bodyBytes);
                        os.flush();
                    }
                }

                int statusCode = conn.getResponseCode();
                String responseBody = readResponseBody(conn, statusCode);

                logger.debug("HTTP {} {} -> {} (attempt {}/{}), Task: {}, {}, {}",
                        method, urlStr, statusCode, attempt, maxAttempts,
                        taskId, taskName, taskInstanceId);

                if (statusCode >= 200 && statusCode < 300) {
                    try {
                        return JsonUtils.jsonToMap(responseBody);
                    } catch (Exception e) {
                        throw new TaskExecutionException(
                                "Response is not valid JSON: " + responseBody, e, taskId, taskInstanceId);
                    }
                } else if (statusCode >= 500 || statusCode == 408 || statusCode == 429) {
                    lastException = new TaskExecutionException(
                            "HTTP " + statusCode + " (retryable): " + responseBody,
                            null, taskId, taskInstanceId);
                    if (attempt >= maxAttempts) {
                        throw lastException;
                    }
                } else {
                    if (statusCode == 401) {
                        throw new TaskExecutionException(
                                "Authentication failure (HTTP 401): " + responseBody,
                                null, taskId, taskInstanceId);
                    } else if (statusCode == 400) {
                        throw new TaskExecutionException(
                                "Bad request (HTTP 400): " + responseBody,
                                null, taskId, taskInstanceId);
                    } else {
                        throw new TaskExecutionException(
                                "HTTP request failed with status " + statusCode + ": " + responseBody,
                                null, taskId, taskInstanceId);
                    }
                }

            } catch (TaskExecutionException e) {
                lastException = e;
                if (!isRetryableException(e)) {
                    throw e;
                }
                logger.warn("HTTP attempt {}/{} failed: {}, Task: {}, {}, {}",
                        attempt, maxAttempts, e.getMessage(), taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                        "HTTP attempt " + attempt + "/" + maxAttempts + " failed: " + e.getMessage());
                if (attempt == maxAttempts) {
                    throw new TaskExecutionException(
                            "HTTP request failed after " + maxAttempts + " attempts: " + e.getMessage(),
                            e, taskId, taskInstanceId);
                }
            } catch (InterruptedIOException e) {
                // HTTP I/O interrupted by the timeout monitor. Wrapped into TaskExecutionException
                // so it propagates through the generic Exception handler in TimeoutTask.execute(),
                // which checks timeouted.get() to classify it as timeout or cancel.
                // Must be caught BEFORE the generic IOException handler below.
                throw new TaskExecutionException(
                        "HTTP request interrupted: " + method + " " + urlStr,
                        e, taskId, taskInstanceId);
            } catch (IOException e) {
                lastException = new TaskExecutionException(
                        "IOException: " + e.getMessage(), e, taskId, taskInstanceId);
                logger.warn("HTTP attempt {}/{} network error: {}, Task: {}, {}, {}",
                        attempt, maxAttempts, e.getMessage(), taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                        "HTTP attempt " + attempt + "/" + maxAttempts + " network error: " + e.getMessage());
                if (attempt == maxAttempts) {
                    throw new TaskExecutionException(
                            "Network error after " + maxAttempts + " attempts: " + e.getMessage(),
                            e, taskId, taskInstanceId);
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        throw new TaskExecutionException(
                "HTTP request failed after all retries", lastException, taskId, taskInstanceId);
    }

    /**
     * Determines if a TaskExecutionException should trigger a retry.
     */
    private boolean isRetryableException(TaskExecutionException e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("HTTP 5") || msg.contains("HTTP 408") || msg.contains("HTTP 429"));
    }

    /**
     * Reads response body from input stream or error stream.
     */
    private String readResponseBody(HttpURLConnection conn, int statusCode) throws IOException {
        InputStream stream;
        if (statusCode >= 400) {
            stream = conn.getErrorStream();
            if (stream == null) {
                return "(no error body)";
            }
        } else {
            stream = conn.getInputStream();
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.length() > 0 ? sb.substring(0, sb.length() - 1) : "";
        }
    }

    /**
     * Checks if the task has been cancelled and throws an exception if so.
     */
    private void checkCanceled() throws TaskExecutionException {
        if (canceled.get()) {
            throw new TaskExecutionException("Task cancelled", null, taskId, taskInstanceId);
        }
    }

    /**
     * Disables SSL certificate verification (for development only).
     */
    private void disableSslVerification(HttpsURLConnection conn) throws TaskExecutionException {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(X509Certificate[] c, String a) {
                        }

                        public void checkServerTrusted(X509Certificate[] c, String a) {
                        }
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            conn.setSSLSocketFactory(sc.getSocketFactory());
            conn.setHostnameVerifier((host, session) -> true);
            logger.warn("SSL verification disabled for SRE connection: {}", sreUrl);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                    "SSL verification disabled for: " + sreUrl);
        } catch (Exception e) {
            throw new TaskExecutionException(
                    "Failed to disable SSL verification: " + e.getMessage(), e, taskId, taskInstanceId);
        }
    }
}
