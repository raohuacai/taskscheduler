/*
 * Description: HTTP/HTTPS REST API task with configurable auth, retry, SSL, and response-body capture
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.task.http;

import com.ts.common.constants.Constants;
import com.ts.common.utils.JsonUtils;
import com.ts.common.utils.ParamUtils;
import com.ts.event.EventManager;
import com.ts.exception.TaskExecutionException;
import com.ts.exception.TaskTimeoutException;
import com.ts.model.entity.TsEvent;
import com.ts.service.impl.TaskLogService;
import com.ts.task.timeout.TimeoutTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.*;
import java.io.*;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;
/**
 * Task type that performs HTTP/HTTPS REST API calls with configurable auth,
 * retry, and response handling.
 *
 * <p>Supports four authentication schemes selected by {@code httpAuthType}:
 * {@code NONE}, {@code BASIC} (Base64 user:password), {@code BEARER} (JWT token),
 * and {@code API_KEY} (custom header). A {@code LOGIN} mode is also available
 * to obtain a bearer token from a separate login endpoint before the main call.
 *
 * <p>Retry policy: up to {@code httpRetryCount} additional attempts are made on
 * 5xx responses or network errors, with {@code httpRetryInterval} seconds between
 * attempts. Retries are skipped on cancellation.
 *
 * <p>Response handling: the raw response body is stored in the parameter map
 * under the key specified by {@code httpResponseParam} (if set). HTTP status codes
 * are validated against {@code httpExpectedStatus} (comma-separated; default 200).
 *
 * <p>SSL verification can be disabled for development environments via
 * {@code httpSkipSslVerify=true}; this installs a trust-all
 * {@link javax.net.ssl.TrustManager} and disables hostname verification — never
 * use in production.
 *
 * <p>Inherits from {@link com.ts.task.timeout.TimeoutTask} for unified
 * interrupt-based timeout control.
 */
public class HttpTask extends TimeoutTask {
    private static final Logger logger = LogManager.getLogger(HttpTask.class);

    private String method;
    private String urlStr;
    private String body;
    private String authType;
    private String authUser;
    private String authPassword;
    private String authToken;
    private String authHeader;
    private String responseParam;
    private String expectedStatusRaw;
    private boolean skipSsl;
    private int connectTimeout;
    private int readTimeout;
    private int retryCount;
    private int retryInterval;
    private Set<Integer> expectedCodes;
    private Map<String, String> extraHeaders;
    // for login
    private String loginUrl;
    private String loginBody;
    private String tokenPath;
    //for event
    private String eventTaskId;
    private String eventName;
    private String eventKey;
    private String tpwfInstanceId;

    // ── HTTP default values ───────────────────────────────────────────────────

    /** Default TCP connection timeout in seconds (value: 30 s).
     *  Overridden by the {@code httpConnectTimeout} task parameter. */
    public static final int HTTP_DEFAULT_CONNECT_TIMEOUT = 30;

    /** Default socket read timeout in seconds (value: 120 s).
     *  Overridden by the {@code httpReadTimeout} task parameter. */
    public static final int HTTP_DEFAULT_READ_TIMEOUT = 120;

    /** Default number of retry attempts after the initial request (value: 0 = no retry).
     *  Overridden by the {@code httpRetryCount} task parameter (range: 0–N). */
    public static final int HTTP_DEFAULT_RETRY_COUNT = 0;

    /** Default interval between retry attempts in seconds (value: 5 s).
     *  Overridden by the {@code httpRetryInterval} task parameter. */
    public static final int HTTP_DEFAULT_RETRY_INTERVAL = 5;

    /** Default HTTP header name used for API-KEY authentication (value: {@code Authorization}).
     *  Overridden by the {@code httpAuthHeader} task parameter. */
    public static final String HTTP_DEFAULT_AUTH_HEADER = "Authorization";
    public static final String HTTP_DEFAULT_EXPECTED_STATUS = "200";
    public static final String HTTP_DEFAULT_TOKEN_PATH = "token";

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

        // required
        method = ParamUtils.getRequiredParameter(parameters, Constants.HTTP_METHOD,
                taskId, taskInstanceId).trim().toUpperCase();
        urlStr = ParamUtils.getRequiredParameter(parameters, Constants.HTTP_URL,
                taskId, taskInstanceId).trim();

        // optional
        body = ParamUtils.getParameter(parameters, Constants.HTTP_BODY, "");
        authType = ParamUtils.getParameter(parameters, Constants.HTTP_AUTH_TYPE,
                Constants.HTTP_AUTH_NONE).toUpperCase().trim();
        authUser = ParamUtils.getParameter(parameters, Constants.HTTP_AUTH_USER, "");
        authPassword = ParamUtils.getParameter(parameters, Constants.HTTP_AUTH_PASSWORD, "");
        authToken = ParamUtils.getParameter(parameters, Constants.HTTP_AUTH_TOKEN, "");
        authHeader = ParamUtils.getParameter(parameters, Constants.HTTP_AUTH_HEADER,
                HTTP_DEFAULT_AUTH_HEADER);
        responseParam = ParamUtils.getParameter(parameters, Constants.HTTP_RESPONSE_PARAM, null);
        expectedStatusRaw = ParamUtils.getParameter(parameters, Constants.HTTP_EXPECTED_STATUS,
                HTTP_DEFAULT_EXPECTED_STATUS);
        skipSsl = ParamUtils.getBooleanParameter(parameters, Constants.HTTP_SKIP_SSL_VERIFY, false);
        connectTimeout = ParamUtils.getIntParameter(parameters, Constants.HTTP_CONNECT_TIMEOUT,
                HTTP_DEFAULT_CONNECT_TIMEOUT);
        readTimeout = ParamUtils.getIntParameter(parameters, Constants.HTTP_READ_TIMEOUT,
                HTTP_DEFAULT_READ_TIMEOUT);
        retryCount = ParamUtils.getIntParameter(parameters, Constants.HTTP_RETRY_COUNT,
                HTTP_DEFAULT_RETRY_COUNT);
        retryInterval = ParamUtils.getIntParameter(parameters, Constants.HTTP_RETRY_INTERVAL,
                HTTP_DEFAULT_RETRY_INTERVAL);

        // for login
        loginUrl = ParamUtils.getParameter(parameters, Constants.HTTP_LOGIN_URL, "").trim();
        loginBody = ParamUtils.getParameter(parameters, Constants.HTTP_LOGIN_BODY, buildDefaultLoginBody()).trim();
        tokenPath = ParamUtils.getParameter(parameters, Constants.HTTP_TOKEN_PATH, HTTP_DEFAULT_TOKEN_PATH).trim();

        // for event
        eventName = ParamUtils.getParameter(parameters, Constants.PARAM_EVENT_NAME, "").trim();
        eventTaskId = ParamUtils.getParameter(parameters, Constants.PARAM_EVENT_TASK_ID, "").trim();
        tpwfInstanceId = ParamUtils.getParameter(parameters, Constants.PARAM_TOP_PARENT_WORKFLOW_INSTANCE, workflowInstanceId);
        if (!eventName.isEmpty() && !eventTaskId.isEmpty()) {
            eventKey = eventName + "-" + tpwfInstanceId + "-" + eventTaskId;
            // Write eventKey into parameters so {eventKey} in httpBody is resolvable below
            saveParam(parameters, Constants.PARAM_EVENT_KEY, eventKey);
            logger.info("Event key prepared: '{}', Task: {}, {}, {}", eventKey, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,"Event key prepared: " + eventKey);
        }

        // Resolve {key} placeholders in body using current parameters map.
        // Must happen after saveParam above so {eventKey} is already in the map.s
        if (body != null && !body.isEmpty()) {
            body = ParamUtils.replaceVariablesOnePass(body, parameters);
            logger.info("HTTP body after variable resolution: {}, Task: {}, {}, {}",
                    body, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                    "HTTP body after variable resolution: " + body);
        }

        // parse + validate
        expectedCodes = parseExpectedStatus(expectedStatusRaw);
        extraHeaders = parseHeaders(ParamUtils.getParameter(parameters, Constants.HTTP_HEADERS, null));

        validateMethod(method);
        validateUrl(urlStr);
        validateAuth(authType);

        // Only perform auto-login when auth=HYBRID and no static token provided
        if (Constants.HTTP_AUTH_HYBRID.equals(authType) && authToken.isEmpty()) {
            authToken = performLogin();
            logger.info("Auto-login succeeded, token obtained, Task: {}, {}, {}",
                    taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                    "Auto-login succeeded via " + loginUrl);
        } else if (Constants.HTTP_AUTH_HYBRID.equals(authType) && !authToken.isEmpty()) {
            logger.info("Using provided static token for HYBRID auth, Task: {}, {}, {}",
                    taskId, taskName, taskInstanceId);
        }

        logger.info("HTTP task preAction completed: method={}, url={}, auth={}, retry={}, Task: {}, {}, {}",
                method, urlStr, authType, retryCount, taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                "HTTP: " + method + " " + urlStr
                        + " auth=" + authType
                        + " retry=" + retryCount + "/" + retryInterval + "s"
                        + " connectTimeout=" + connectTimeout + "s"
                        + " readTimeout=" + readTimeout + "s");
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

        int attempts = 0;
        int maxAttempts = retryCount + 1;   // 1 initial attempt + N retries
        Exception lastException = null;

        while (attempts < maxAttempts) {
            attempts++;
            if (attempts > 1) {
                logger.warn("HTTP retry {}/{}, waiting {}s, Task: {}, {}, {}",
                        attempts - 1, retryCount, retryInterval, taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                        "HTTP retry " + (attempts - 1) + "/" + retryCount
                                + ", waiting " + retryInterval + "s");
                try {
                    Thread.sleep(retryInterval * 1000L);
                } catch (InterruptedException ie) {
                    // Clear the interrupt flag before any DB operations.
                    // Consistent with AbstractTask.execute() / TimeoutTask.execute() which also
                    // do NOT restore the interrupt flag after catching InterruptedException.
                    // Oracle JDBC throws InterruptedIOException when thread is in interrupted state.
                    // Thread.interrupted();   // clear, do NOT restore
                    throw new TaskExecutionException(
                            "HTTP task interrupted during retry wait", ie, taskId, taskInstanceId);
                }
            }

            // check cancel before each attempt
            if (canceled.get()) {
                logger.warn("Task canceled before HTTP attempt {}, Task: {}, {}, {}",
                        attempts, taskId, taskName, taskInstanceId);
                // Throw instead of return so WorkflowInstance.executeTask() detects
                // task.isCanceled()==true and keeps status as CANCELED (not COMPLETED).
                throw new TaskExecutionException(
                        "HTTP task canceled before attempt " + attempts, null, taskId, taskInstanceId);
            }

            try {
                executeRequest(parameters, attempts, maxAttempts);
                return;   // success — exit retry loop
            } catch (TaskTimeoutException e) {
                // timeout is not retryable — propagate immediately
                throw e;
            } catch (RetryableException e) {
                lastException = e.getCause() != null ? (Exception) e.getCause() : e;
                logger.warn("HTTP attempt {}/{} failed (retryable): {}, Task: {}, {}, {}",
                        attempts, maxAttempts, e.getMessage(), taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                        "HTTP attempt " + attempts + "/" + maxAttempts + " failed: " + e.getMessage());
                if (attempts >= maxAttempts) {
                    // exhausted all retries
                    throw new TaskExecutionException(
                            "HTTP request failed after " + maxAttempts + " attempt(s): " + e.getMessage(),
                            lastException, taskId, taskInstanceId);
                }
            } catch (TaskExecutionException e) {
                // non-retryable (4xx, bad config, etc.) — propagate immediately
                throw e;
            }
        }
    }

    // HttpTask.postAction example: register an event with EventManager after a successful HTTP call
    @Override
    /**
     * Post the action.
     *
     * @param parameters  parameter map
     * @throws Exception  if an error occurs
     */
    public void postAction(Map<String, Object> parameters) throws Exception {
        //super.postAction(parameters);

        // Check whether event creation is required (controlled by task parameter)
        if (eventKey != null && !eventKey.isEmpty()) {
            TsEvent created = EventManager.getInstance().createEvent(eventKey, tpwfInstanceId, eventTaskId);
            if (created != null) {
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Event created for downstream EventTask: " + eventKey);
            }
        }
    }

    /**
     * Performs one HTTP request attempt and processes the response.
     *
     * <p>Opens an {@link java.net.HttpURLConnection}, optionally disables SSL
     * verification, sets timeouts, applies authentication headers via
     * {@link #applyAuth}, writes the request body (for POST/PUT/PATCH), reads
     * the response via {@link #readResponse}, validates the HTTP status against
     * the expected-codes set, and stores the response body in the parameter map
     * under {@code httpResponseParam}. Throws {@link RetryableException} on 5xx
     * or network errors so the caller's retry loop can schedule another attempt.
     *
     * @param parameters  the merged task parameter map; the response body is
     *                    written back into this map on success
     * @param attempt     the current attempt number (1-based), used for logging
     * @param maxAttempts the total number of attempts allowed (1 + retryCount)
     * @throws RetryableException     if the response status is 5xx or a network
     *                                error occurs and further retries remain
     * @throws TaskExecutionException if the response status is unexpected and
     *                                no further retries are allowed
     * @throws Exception              for any other unrecoverable error
     */
    protected void executeRequest(Map<String, Object> parameters,
                                  int attempt, int maxAttempts) throws Exception {

        logger.info("HTTP {} {} (attempt {}/{}), Task: {}, {}, {}",
                method, urlStr, attempt, maxAttempts, taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                "HTTP " + method + " " + urlStr + " attempt " + attempt + "/" + maxAttempts);

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        if (skipSsl && conn instanceof HttpsURLConnection) {
            disableSslVerification((HttpsURLConnection) conn);
        }

        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(connectTimeout * 1000);
            conn.setReadTimeout(readTimeout * 1000);
            conn.setDoInput(true);

            // auth
            applyAuth(conn);

            // custom headers
            extraHeaders.forEach(conn::setRequestProperty);

            // request body
            if (body != null && !body.isEmpty()
                    && (method.equals("POST") || method.equals("PUT") || method.equals("PATCH"))) {
                conn.setDoOutput(true);
                if (!extraHeaders.containsKey("Content-Type")) {
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                }
                byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bodyBytes);
                    os.flush();
                }
                logger.debug("Request body: {} bytes, Task: {}, {}, {}",
                        bodyBytes.length, taskId, taskName, taskInstanceId);
            }

            // read response — InterruptedIOException means thread was interrupted
            int statusCode;
            String responseBody;
            try {
                statusCode = conn.getResponseCode();
                responseBody = readResponse(conn, statusCode);
            } catch (InterruptedIOException e) {
                // TimeoutTask interrupted the executing thread via executingThread.interrupt()
                if (timeouted.get()) {
                    throw new TaskTimeoutException(
                            "HTTP request timed out after " + timeout + " seconds: "
                                    + method + " " + urlStr,
                            e, taskId, taskInstanceId);
                }
                throw new TaskExecutionException(
                        "HTTP request interrupted: " + e.getMessage(), e, taskId, taskInstanceId);
            } catch (IOException e) {
                // network error — retryable
                throw new RetryableException("Network error: " + e.getMessage(), e);
            }

            logger.info("HTTP response: {} {} (attempt {}/{}), body: {} chars, Task: {}, {}, {}",
                    statusCode, conn.getResponseMessage(),
                    attempt, maxAttempts, responseBody.length(),
                    taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                    "HTTP response: " + statusCode + " " + conn.getResponseMessage()
                            + " (attempt " + attempt + "/" + maxAttempts + ")");

            // store response (memory + DB)
            if (responseParam != null && !responseParam.isEmpty()) {
                Map<String, Object> updates = new HashMap<>();
                updates.put(Constants.PARAM_TASK_EXITCODE, statusCode);
                updates.put(Constants.PARAM_TASK_OUTPUT, responseBody);
                updates.put(responseParam, responseBody);
                saveParams(parameters, updates);
                logger.debug("Response stored in parameters['{}'], Task: {}, {}, {}",
                        responseParam, taskId, taskName, taskInstanceId);
            } else {
                Map<String, Object> updates = new HashMap<>();
                updates.put(Constants.PARAM_TASK_EXITCODE, statusCode);
                updates.put(Constants.PARAM_TASK_OUTPUT, responseBody);
                saveParams(parameters, updates);
            }

            // 5xx → retryable; 4xx → non-retryable; unexpected → check config
            if (!expectedCodes.contains(statusCode)) {
                String preview = responseBody.length() > 500
                        ? responseBody.substring(0, 500) + "..."
                        : responseBody;
                String msg = "HTTP " + method + " " + urlStr
                        + " returned status " + statusCode
                        + " (expected: " + expectedStatusRaw + ")\nResponse: " + preview;

                if (statusCode >= 500) {
                    // server error — retryable
                    throw new RetryableException(msg, null);
                } else {
                    // client error or unexpected — non-retryable
                    throw new TaskExecutionException(msg, null, taskId, taskInstanceId);
                }
            }

        } finally {
            conn.disconnect();
        }
    }

    /**
     * Applies the configured authentication scheme to the given connection by
     * setting the appropriate HTTP request headers.
     *
     * <p>Supported schemes (selected by the {@code httpAuthType} task parameter):
     * <ul>
     *   <li>{@code BASIC}  — Base64-encodes {@code user:password} into an
     *       {@code Authorization: Basic …} header.</li>
     *   <li>{@code BEARER} / {@code HYBRID} — sets {@code Authorization: Bearer <token>}.</li>
     *   <li>{@code API_KEY} — sets a custom header named by {@code httpAuthHeader}
     *       to the value of {@code httpAuthToken}.</li>
     *   <li>{@code NONE} — no authentication headers are added.</li>
     * </ul>
     *
     * @param conn  the open {@link java.net.HttpURLConnection} to authenticate
     */
    protected void applyAuth(HttpURLConnection conn) {
        switch (authType) {
            case Constants.HTTP_AUTH_BASIC:
                if (!authUser.isEmpty()) {
                    String credentials = Base64.getEncoder().encodeToString(
                            (authUser + ":" + authPassword).getBytes(StandardCharsets.UTF_8));
                    conn.setRequestProperty("Authorization", "Basic " + credentials);
                    logger.debug("Applied BASIC auth, user: {}, Task: {}, {}, {}",
                            authUser, taskId, taskName, taskInstanceId);
                }
                break;
            case Constants.HTTP_AUTH_BEARER:
                if (!authToken.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + authToken);
                    logger.debug("Applied BEARER token auth, Task: {}, {}, {}",
                            taskId, taskName, taskInstanceId);
                }
                break;
            case Constants.HTTP_AUTH_API_KEY:
                if (!authToken.isEmpty()) {
                    conn.setRequestProperty(authHeader, authToken);
                    logger.debug("Applied API_KEY auth on header: {}, Task: {}, {}, {}",
                            authHeader, taskId, taskName, taskInstanceId);
                }
                break;
            case Constants.HTTP_AUTH_HYBRID:
                if (!authToken.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + authToken);
                    logger.debug("Applied BEARER token auth (HYBRID), Task: {}, {}, {}",
                            taskId, taskName, taskInstanceId);
                }
                break;
            case Constants.HTTP_AUTH_NONE:
            default:
                break;
        }
    }

    /**
     * Reads and returns the full response body from the connection as a UTF-8 string.
     *
     * <p>For 4xx/5xx responses, reads from {@link java.net.HttpURLConnection#getErrorStream()}
     * rather than {@link java.net.HttpURLConnection#getInputStream()}. Returns the literal
     * string {@code "(no error body)"} when the error stream is unavailable.
     *
     * @param conn        the connection whose body is to be read
     * @param statusCode  the HTTP response status code; drives stream selection
     * @return the full response body as a string; never {@code null}
     * @throws IOException  if the stream cannot be read
     */
    protected String readResponse(HttpURLConnection conn, int statusCode) throws IOException {
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
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * Validates that the supplied HTTP method string is one of the five
     * supported verbs: {@code GET}, {@code POST}, {@code PUT},
     * {@code DELETE}, {@code PATCH}.
     *
     * @param m  the HTTP method string to validate (case-sensitive)
     * @throws TaskExecutionException  if {@code m} is not in the allowed set
     */
    protected void validateMethod(String m) throws TaskExecutionException {
        Set<String> allowed = new HashSet<>(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH"));
        if (!allowed.contains(m)) {
            throw new TaskExecutionException(
                    "Invalid http_method: '" + m + "'. Allowed: GET, POST, PUT, DELETE, PATCH",
                    null, taskId, taskInstanceId);
        }
    }

    /**
     * Validates that the supplied URL is non-empty and starts with
     * {@code http://} or {@code https://}.
     *
     * @param u  the URL string to validate
     * @throws TaskExecutionException  if {@code u} is null, empty, or uses an unsupported scheme
     */
    protected void validateUrl(String u) throws TaskExecutionException {
        if (u == null || u.isEmpty()) {
            throw new TaskExecutionException(
                    "http_url is required", null, taskId, taskInstanceId);
        }
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            throw new TaskExecutionException(
                    "http_url must start with http:// or https://: " + u,
                    null, taskId, taskInstanceId);
        }
    }

    /**
     * Validates the authentication type string and checks that all credentials
     * required by that type are present.
     *
     * <p>Rules enforced:
     * <ul>
     *   <li>{@code BASIC} requires {@code httpAuthUser} to be non-empty.</li>
     *   <li>{@code BEARER} and {@code API_KEY} require {@code httpAuthToken}.</li>
     *   <li>{@code HYBRID} requires both {@code httpLoginUrl} and {@code httpLoginBody}.</li>
     * </ul>
     *
     * @param at  the authentication type string (case-insensitive)
     * @throws TaskExecutionException  if the type is unrecognised or required credentials are missing
     */
    protected void validateAuth(String at) throws TaskExecutionException {
        Set<String> allowed = new HashSet<>(Arrays.asList(
                Constants.HTTP_AUTH_NONE, Constants.HTTP_AUTH_BASIC,
                Constants.HTTP_AUTH_BEARER, Constants.HTTP_AUTH_HYBRID, Constants.HTTP_AUTH_API_KEY));
        if (!allowed.contains(at)) {
            throw new TaskExecutionException(
                    "Invalid http_auth_type: '" + at
                            + "'. Allowed: NONE, BASIC, BEARER, API_KEY, HYBRID",
                    null, taskId, taskInstanceId);
        }
        if (Constants.HTTP_AUTH_BASIC.equals(at) && authUser.isEmpty()) {
            throw new TaskExecutionException(
                    "http_auth_user is required when http_auth_type=BASIC",
                    null, taskId, taskInstanceId);
        }
        if ((Constants.HTTP_AUTH_BEARER.equals(at) || Constants.HTTP_AUTH_API_KEY.equals(at)) && authToken.isEmpty()) {
            throw new TaskExecutionException(
                    "http_auth_token is required when http_auth_type=" + at,
                    null, taskId, taskInstanceId);
        }
        if (Constants.HTTP_AUTH_HYBRID.equals(at) && (loginUrl.isEmpty() || loginBody.isEmpty())) {
            throw new TaskExecutionException(
                    "http_login_url and http_login_body are required when http_auth_type=HYBRID",
                    null, taskId, taskInstanceId);
        }
    }

    /**
     * Parses the {@code httpHeaders} task parameter from a JSON object string into
     * an ordered map of header name → value pairs.
     *
     * <p>Returns an empty map when {@code headersJson} is null or blank.
     * All values are coerced to {@link String}; {@code null} values become empty strings.
     *
     * @param headersJson  JSON object string mapping header names to values, e.g.
     *                     {@code {"X-Trace-Id":"abc","Accept":"application/json"}}
     * @return an ordered map of HTTP header entries; never {@code null}
     * @throws TaskExecutionException  if {@code headersJson} is not valid JSON
     */
    protected Map<String, String> parseHeaders(String headersJson) throws TaskExecutionException {
        if (headersJson == null || headersJson.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = JsonUtils.jsonToMap(headersJson);
            Map<String, String> result = new LinkedHashMap<>();
            if (raw != null) {
                raw.forEach((k, v) -> result.put(k, v == null ? "" : v.toString()));
            }
            return result;
        } catch (Exception e) {
            throw new TaskExecutionException(
                    "Failed to parse http_headers JSON: " + e.getMessage(), e, taskId, taskInstanceId);
        }
    }

    /**
     * Parses the {@code httpExpectedStatus} task parameter from a comma-separated
     * list of HTTP status codes into a {@link Set}.
     *
     * <p>Invalid tokens (non-integer) are silently ignored with a {@code WARN} log
     * entry. If the resulting set is empty (all tokens invalid or input blank),
     * falls back to the singleton set {@code {200}}.
     *
     * @param raw  comma-separated status code string, e.g. {@code "200,201,204"}
     * @return a set of acceptable HTTP status codes; never empty
     */
    protected Set<Integer> parseExpectedStatus(String raw) {
        Set<Integer> codes = new HashSet<>();
        for (String s : raw.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                try {
                    codes.add(Integer.parseInt(t));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid expected status code '{}', ignored, Task: {}, {}, {}",
                            t, taskId, taskName, taskInstanceId);
                }
            }
        }
        if (codes.isEmpty()) {
            codes.add(200);
        }
        return codes;
    }

    /**
     * Installs a trust-all {@link javax.net.ssl.TrustManager} and disables hostname
     * verification on the given HTTPS connection.
     *
     * <p><strong>WARNING:</strong> This method completely disables SSL/TLS certificate
     * validation and must never be used in production. It is intended solely for
     * development or testing against self-signed certificates.
     * Enable via {@code httpSkipSslVerify=true}.
     *
     * @param conn  the {@link javax.net.ssl.HttpsURLConnection} on which to disable SSL verification
     * @throws TaskExecutionException  if the SSL context cannot be initialised
     */
    protected void disableSslVerification(HttpsURLConnection conn) throws TaskExecutionException {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        /**
                         * Returns the accepted issuers.
                         *
                         * @return the requested acceptedissuers object, or null if not found
                         */
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        /**
                         * Checks the client trusted.
                         *
                         * @param c  c value
                         * @param a  a value
                         */
                        public void checkClientTrusted(X509Certificate[] c, String a) {
                        }

                        /**
                         * Checks the server trusted.
                         *
                         * @param c  c value
                         * @param a  a value
                         */
                        public void checkServerTrusted(X509Certificate[] c, String a) {
                        }
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            conn.setSSLSocketFactory(sc.getSocketFactory());
            conn.setHostnameVerifier((host, session) -> true);
            logger.warn("SSL verification disabled for: {}, Task: {}, {}, {}",
                    urlStr, taskId, taskName, taskInstanceId);
        } catch (Exception e) {
            throw new TaskExecutionException(
                    "Failed to disable SSL verification: " + e.getMessage(), e, taskId, taskInstanceId);
        }
    }

    /**
     * Signals that the HTTP request failed with a condition that warrants a retry
     * (e.g. a 5xx server error or a network-level {@link java.io.IOException}).
     *
     * <p>Thrown internally by the request-execution helper and caught by the
     * retry loop in {@link #action}. Not propagated outside {@code HttpTask}.
     */
    protected static class RetryableException extends Exception {
        RetryableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // perform login: POST loginUrl → extract token
    // ─────────────────────────────────────────────────────────────────────────
    protected String performLogin() throws TaskExecutionException {
        logger.info("Performing auto-login: POST {}, user: {}, Task: {}, {}, {}",
                loginUrl, authUser, taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                "Auto-login: POST " + loginUrl + " user=" + authUser);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(loginUrl);
            conn = (HttpURLConnection) url.openConnection();

            if (skipSsl && conn instanceof HttpsURLConnection) {
                disableSslVerification((HttpsURLConnection) conn);
            }

            conn.setRequestMethod("POST");
            conn.setConnectTimeout(connectTimeout * 1000);
            // Use a generous read timeout for login (at least 60s)
            int loginReadTimeout = Math.max(connectTimeout, 60);
            conn.setReadTimeout(loginReadTimeout * 1000);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            byte[] bodyBytes = loginBody.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }

            int statusCode = conn.getResponseCode();
            String loginResp = readResponse(conn, statusCode);

            logger.info("Login response: {} {}, Task: {}, {}, {}",
                    statusCode, conn.getResponseMessage(), taskId, taskName, taskInstanceId);

            if (statusCode != 200) {
                throw new TaskExecutionException(
                        "Auto-login failed with status " + statusCode
                                + " from " + loginUrl + ": " + loginResp,
                        null, taskId, taskInstanceId);
            }

            // extract token from JSON response
            String token = extractToken(loginResp, tokenPath);
            if (token == null || token.isEmpty()) {
                throw new TaskExecutionException(
                        "Auto-login succeeded but token not found at path '"
                                + tokenPath + "' in response: " + loginResp,
                        null, taskId, taskInstanceId);
            }
            return token;

        } catch (TaskExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new TaskExecutionException(
                    "Auto-login request failed: " + e.getMessage(), e, taskId, taskInstanceId);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Build default login body from auth_user / auth_password.
     * Matches the Task Scheduler login API:
     * {"username":"admin","password":"admin123"}
     */
    protected String buildDefaultLoginBody() {
        Map<String, String> loginMap = new LinkedHashMap<>();
        loginMap.put("username", authUser);
        loginMap.put("password", authPassword);
        return JsonUtils.toJson(loginMap);
    }

    /**
     * Extract a value from a flat JSON response by key path.
     * Supports simple dot-notation for one level of nesting, e.g. "data.token".
     */
    protected String extractToken(String json, String path) {
        try {
            Map<String, Object> map = JsonUtils.jsonToMap(json);
            if (map == null) return null;

            String[] parts = path.split("\\.", 2);
            if (parts.length == 1) {
                Object v = map.get(parts[0]);
                return v == null ? null : v.toString();
            } else {
                // one level of nesting
                Object nested = map.get(parts[0]);
                if (nested instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Object v = ((Map<String, Object>) nested).get(parts[1]);
                    return v == null ? null : v.toString();
                }
                return null;
            }
        } catch (Exception e) {
            logger.warn("Failed to extract token from login response: {}, Task: {}, {}, {}",
                    e.getMessage(), taskId, taskName, taskInstanceId);
            return null;
        }
    }

}
