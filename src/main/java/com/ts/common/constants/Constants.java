/*
 * Description: Application-wide string constants for statuses, flags, parameter names, and type groups
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.common.constants;

import java.sql.Types;
/**
 * Application-wide string constants for statuses, flag values, parameter names,
 * task type groups, JDBC type mappings, and other shared literals.
 *
 * <p>This is a pure constants holder — it has no state and no methods.
 * All fields are {@code public static final}. Grouping comments separate
 * logical sections.
 */
public class Constants {
    // shutdown
    public static final int SHUTDOWN_PORT = 19999;
    public static final String SHUTDOWN_SECRET = "_TS_SHUTDOWN_NOW_";
    // Status constants
    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_ERROR = "ERROR";
    public static final String STATUS_TIMEOUT = "TIMEOUT";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_COMPLETED = "COMPLETED";

    // Flag constants
    public static final String FLAG_YES = "Y";
    public static final String FLAG_NO = "N";

    // Task type groups
    public static final String TYPE_GROUP_TIMEOUT = "TIMEOUT";
    public static final String TYPE_GROUP_WORKFLOW = "WORKFLOW";
    public static final String TYPE_GROUP_SCRIPT = "SCRIPT";
    public static final String TYPE_GROUP_SQL = "SQL";
    public static final String TYPE_GROUP_UTIL = "UTIL";
    public static final String TYPE_TASK_OR = "OR";
    public static final String TYPE_EVENT = "EVENT";

    //Global parameter names
    public static final String WF_GLOBAL_PARAM_NAME = "WfGlobalParams";
    public static final String THREAD_POOL_CONFIG_NAME = "ThreadPoolConfig";
    public static final String EMAIL_CONFIG_NAME = "EmailConfig";
    public static final String EMAIL_TEMPLATE_CONFIG_NAME = "EmailTemplateConfig";
    public static final String HTTP_SERVER_CONFIG_NAME = "HttpServerConfig";

    // workflow or task parameter names
    public static final String PARAM_TASK_OUTPUT = "taskOutput";
    public static final String PARAM_TASK_EXITCODE = "taskExitCode";
    public static final String PARAM_PARENT = "parent";
    public static final String PARAM_WORKFLOW_ID = "workflowId";
    public static final String PARAM_WORKFLOW_NAME = "workflowName";
    public static final String PARAM_WORKFLOW_INSTANCE = "workflowInstanceId";
    public static final String PARAM_WORKFLOW_TRIGGERBY = "triggeredBy";
    public static final String PARAM_TOP_PARENT_WORKFLOW_INSTANCE = "topParentWorkflowInstance";
    public static final String PARAM_TASK_ID = "taskId";
    public static final String PARAM_TASK_NAME = "taskName";
    public static final String PARAM_TASK_INSTANCE = "taskInstanceId";
    public static final String PARAM_TASK_TYPE = "taskType";
    public static final String PARAM_TASK_TIMEOUT = "taskTimeout";
    public static final String PARAM_NOTICE_EMAIL = "noticeFlag";
    public static final String PARAM_ERROR_EMAIL = "errorFlag";
    public static final String PARAM_ERROR_MESSAGE = "errorMessage";
    public static final String PARAM_NOTICE_EMAIL_ADDRESS = "noticeRecipients";
    public static final String PARAM_ERROR_EMAIL_ADDRESS = "errorRecipients";
    public static final String PARAM_TASK_CLASSNAME = "className";
    public static final String PARAM_TASK_METHODNAME = "methodName";
    public static final String PARAM_TASK_SUBWORKFLOWID = "subWorkflowId";
    public static final String PARAM_TASK_SUBWORKFLOW_INSTANCE = "subWorkflowInstanceId";
    // Namespace prefix used in {tasks.<taskId|taskName>.<paramName>} inter-task parameter references
    public static final String PARAM_UPSTREAM_TASK_REF = "tasks";
    public static final String PARAM_TASK_SCRIPT = "script";
    public static final String PARAM_TASK_WORKINGDIR = "workingDir";
    public static final String PARAM_TASK_JDBC_URL = "jdbcUrl";
    public static final String PARAM_TASK_JDBC_USERNAME = "username";
    public static final String PARAM_TASK_JDBC_PASSWORD = "password";
    public static final String PARAM_TASK_JDBC_SQL = "sql";
    public static final String PARAM_TASK_JDBC_PROCEDURENAME = "procedureName";
    public static final String PARAM_TASK_JDBC_CHECKEXITCODE = "checkExitCode";
    public static final String PARAM_TASK_JDBC_MAX_ROWS = "maxRows";
    // Webhook
    public static final String PARAM_NOTICE_WEBHOOK = "noticeWebhook";
    public static final String PARAM_NOTICE_WEBHOOK_URL = "noticeWebhookUrl";
    public static final String PARAM_ERROR_WEBHOOK = "errorWebhook";
    public static final String PARAM_ERROR_WEBHOOK_URL = "errorWebhookUrl";

    // Remote Shell
    public static final String PARAM_SSH_HOST = "ssh.host";
    public static final String PARAM_SSH_PORT = "ssh.port";
    public static final String PARAM_SSH_USER = "ssh.user";
    public static final String PARAM_SSH_PASSWORD = "ssh.password";
    public static final String PARAM_SSH_KEYFILE = "ssh.keyfile";
    public static final String PARAM_SSH_KNOWN_HOSTS = "ssh.knownHosts";
    public static final String PARAM_SSH_REMOTE_WORKINGDIR = "ssh.remoteWorkDir";

    // CSV Task Parameters
    public static final String PARAM_TASK_CSV_DIRECTORY = "csvDirectory";
    public static final String PARAM_TASK_CSV_FILENAME = "csvFilename";
    public static final String PARAM_TASK_CSV_DELIMITER = "csvDelimiter";
    public static final String PARAM_TASK_CSV_INCLUDE_HEADER = "includeHeader";
    public static final String PARAM_TASK_CSV_QUOTE_STRINGS = "quoteStrings";

    // Notification type constants
    public static final String TYPE_WORKFLOW_START = "workflowStart";
    public static final String TYPE_WORKFLOW_END = "workflowEnd";
    public static final String TYPE_WORKFLOW_CANCEL = "workflowCancel";
    public static final String TYPE_WORKFLOW_TIMEOUT = "workflowTimeout";
    public static final String TYPE_WORKFLOW_ERROR = "workflowError";
    public static final String TYPE_TASK_ERROR = "taskError";
    public static final String TYPE_TASK_CANCEL = "taskCancel";
    public static final String TYPE_TASK_TIMEOUT = "taskTimeout";

    // Parameter type names
    public static final String TYPE_INTEGER = "INTEGER";
    public static final String TYPE_BIGINT = "BIGINT";
    public static final String TYPE_DECIMAL = "DECIMAL";
    public static final String TYPE_NUMERIC = "NUMERIC";
    public static final String TYPE_FLOAT = "FLOAT";
    public static final String TYPE_DOUBLE = "DOUBLE";
    public static final String TYPE_DATE = "DATE";
    public static final String TYPE_DATETIME = "DATETIME";
    public static final String TYPE_TIMESTAMP = "TIMESTAMP";
    public static final String TYPE_BOOL = "BOOL";
    public static final String TYPE_BOOLEAN = "BOOLEAN";
    public static final String TYPE_BINARY = "BINARY";
    public static final String TYPE_BLOB = "BLOB";
    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_CLOB = "CLOB";
    public static final String TYPE_VARCHAR = "VARCHAR";
    public static final String TYPE_VARCHAR2 = "VARCHAR2";
    public static final String TYPE_STRING = "STRING";

    // SQL Types mapping constants
    public static final int SQL_TYPE_INTEGER = Types.INTEGER;
    public static final int SQL_TYPE_BIGINT = Types.BIGINT;
    public static final int SQL_TYPE_DECIMAL = Types.DECIMAL;
    public static final int SQL_TYPE_NUMERIC = Types.NUMERIC;
    public static final int SQL_TYPE_DOUBLE = Types.DOUBLE;
    public static final int SQL_TYPE_DATE = Types.DATE;
    public static final int SQL_TYPE_TIMESTAMP = Types.TIMESTAMP;
    public static final int SQL_TYPE_BOOLEAN = Types.BOOLEAN;
    public static final int SQL_TYPE_BLOB = Types.BLOB;
    public static final int SQL_TYPE_CLOB = Types.CLOB;
    public static final int SQL_TYPE_VARCHAR = Types.VARCHAR;
    public static final int SQL_TYPE_NULL = Types.NULL;

    // TS Lock
    public static final String LOCK_READ = "R";
    public static final String LOCK_WRITE = "W";
    public static final String LOCK_ACQUIRE = "acquireLock";
    public static final String LOCK_RELEASE = "releaseLock";
    public static final String LOCK_NAME = "lockName";
    public static final String LOCK_OWNER = "lockOwner";
    public static final String LOCK_ACTION = "lockAction";

    // Wait Task
    public static final String PARAM_WAIT_TARGET_TIME = "waitTargetTime";
    public static final String PARAM_WAIT_OFFSET_SECONDS = "waitOffsetSeconds";
    public static final String PARAM_WAIT_CHECK_INTERVAL = "waitCheckInterval";

    // HTTP Task
    // ── HTTP auth type values
    public static final String HTTP_AUTH_NONE = "NONE";
    public static final String HTTP_AUTH_BASIC = "BASIC";
    public static final String HTTP_AUTH_BEARER = "BEARER";
    public static final String HTTP_AUTH_HYBRID = "HYBRID";
    public static final String HTTP_AUTH_API_KEY = "API_KEY";
    /**
     * HTTP method: GET / POST / PUT / DELETE / PATCH
     */
    public static final String HTTP_METHOD = "httpMethod";
    /**
     * Target URL, e.g. https://api.example.com/v1/resource
     */
    public static final String HTTP_URL = "httpUrl";
    /**
     * Extra request headers as a JSON object string, e.g. {"X-Trace":"123"}
     */
    public static final String HTTP_HEADERS = "httpHeaders";
    /**
     * Request body string (used for POST / PUT / PATCH)
     */
    public static final String HTTP_BODY = "httpBody";
    /**
     * Authentication type.
     * Allowed values: NONE | BASIC | BEARER | API_KEY | HYBRID
     * Default: NONE
     */
    public static final String HTTP_AUTH_TYPE = "httpAuthType";
    /**
     * Username for BASIC auth
     */
    public static final String HTTP_AUTH_USER = "httpAuthUser";
    /**
     * Password for BASIC auth
     */
    public static final String HTTP_AUTH_PASSWORD = "httpAuthPassword";
    /**
     * Token value for BEARER auth or API_KEY auth.
     * BEARER → sent as "Authorization: Bearer <token>"
     * API_KEY → sent as "<http_auth_header>: <token>"
     */
    public static final String HTTP_AUTH_TOKEN = "httpAuthToken";
    /**
     * Header name for API_KEY auth.
     * Default: "Authorization"
     */
    public static final String HTTP_AUTH_HEADER = "httpAuthHeader";
    /**
     * Connection timeout in seconds. Default: 30
     */
    public static final String HTTP_CONNECT_TIMEOUT = "httpConnectTimeout";
    /**
     * Read (socket) timeout in seconds. Default: task_timeout value
     */
    public static final String HTTP_READ_TIMEOUT = "httpReadTimeout";
    /**
     * Comma-separated list of acceptable HTTP response status codes.
     * Default: "200"
     * Example: "200,201,204"
     */
    public static final String HTTP_EXPECTED_STATUS = "httpExpectedStatus";
    /**
     * Key name under which the HTTP response body is stored in parameters
     * so downstream tasks can read it.
     * Optional — if empty the response is only stored in PARAM_TASK_OUTPUT.
     */
    public static final String HTTP_RESPONSE_PARAM = "httpResponseParam";
    /**
     * Skip SSL certificate verification.
     * Default: false  — set to "true" only in non-production environments.
     */
    public static final String HTTP_SKIP_SSL_VERIFY = "httpSkipSslVerify";
    /**
     * Number of retry attempts on 5xx or network error.
     * Default: 0 (no retry).
     */
    public static final String HTTP_RETRY_COUNT = "httpRetryCount";
    /**
     * Wait interval in seconds between retry attempts.
     * Default: 5
     */
    public static final String HTTP_RETRY_INTERVAL = "httpRetryInterval";
    // ── HttpBearerTask auto-login parameters ─────────────────────────────────
    /**
     * Login endpoint URL.
     * When http_auth_type=BEARER and this is set, HttpBearerTask first calls this URL
     * with BASIC auth (username + password) to obtain a Bearer token,
     * then uses that token for the real API call.
     * Example: "http://rhc.com:8888/api/auth/login"
     */
    public static final String HTTP_LOGIN_URL = "httpLoginUrl";
    /**
     * Optional custom login request body (JSON string).
     * If empty, defaults to {"username":"<http_auth_user>","password":"<http_auth_password>"}.
     */
    public static final String HTTP_LOGIN_BODY = "httpLoginBody";
    /**
     * Dot-notation path to extract the token from the login JSON response.
     * Default: "token"  (matches Task Scheduler's /api/auth/login response: {"token":"..."})
     * Example for nested: "data.accessToken"
     */
    public static final String HTTP_TOKEN_PATH = "httpTokenPath";


    // ─── Export Excel Task
    // Add these constants to Constants.java alongside the existing CSV/JDBC constants.
    /** Required: output directory path, e.g. "/data/export/excel" */
    public static final String PARAM_TASK_EXCEL_DIRECTORY          = "excelDirectory";
    /** Optional: base filename (timestamp + .xlsx appended automatically); defaults to task name */
    public static final String PARAM_TASK_EXCEL_FILENAME           = "excelFilename";
    /** Optional: sheet name; defaults to "Sheet1" */
    public static final String PARAM_TASK_EXCEL_SHEET_NAME         = "excelSheetName";
    /** Optional: write column headers; "true" (default) or "false" */
    public static final String PARAM_TASK_EXCEL_INCLUDE_HEADER     = "excelIncludeHeader";


    /**
     * The globally unique business event key (e.g. "order.approved.ORDER-001").
     * Set by the upstream task before the workflow reaches EventTask.
     * EventTask also reads this key to locate its TS_EVENT row (Priority 1 lookup).
     */
    public static final String PARAM_EVENT_KEY = "eventKey";
    public static final String PARAM_EVENT_NAME = "eventName";
    /**
     * The task definition ID of the EventTask node.
     * Set by the upstream task so EventManager.createEvent() can associate the
     * TS_EVENT row with the correct node in the workflow DAG.
     */
    public static final String PARAM_EVENT_TASK_ID = "eventTaskId";
    /**
     * Output parameter written by EventTask after the event is triggered.
     * Downstream tasks can reference the trigger payload via ${eventPayload}.
     */
    public static final String PARAM_EVENT_PAYLOAD = "eventPayload";

    // FileXTask
    public static final String PARAM_SOURCE_URI = "sourceUri";
    public static final String PARAM_TARGET_URI = "targetUri";
    public static final String PARAM_OPERATION = "operation";
    public static final String PARAM_RECURSIVE = "recursive";
    public static final String PARAM_OVERWRITE = "overwrite";
    public static final String PARAM_DELETE_SOURCE_AFTER_COPY = "deleteSourceAfterCopy";
    public static final String PARAM_PRESERVE_TIMESTAMP = "preserveTimestamp";
    public static final String PARAM_INCLUDE_PATTERNS = "includePatterns";
    public static final String PARAM_EXCLUDE_PATTERNS = "excludePatterns";
    public static final String PARAM_CHECKSUM_ALGORITHM = "checksumAlgorithm";
    public static final String PARAM_VERIFY_CHECKSUM = "verifyChecksum";
    public static final String PARAM_COMPRESS_TRANSFER = "compressTransfer";
    public static final String PARAM_COMPRESSION_FORMAT = "compressionFormat";
    public static final String PARAM_CONNECTION_TIMEOUT = "connectionTimeout";
    public static final String PARAM_DATA_TIMEOUT = "dataTimeout";
    public static final String PARAM_USERNAME = "username";
    public static final String PARAM_PASSWORD = "password";
    public static final String PARAM_PRIVATE_KEY_PATH = "privateKeyPath";
    public static final String PARAM_PRIVATE_KEY_PASSPHRASE = "privateKeyPassphrase";
    public static final String PARAM_PASSIVE_MODE = "passiveMode";

    // SRE Job Task Parameters
    public static final String SRE_URL = "sreUrl";
    public static final String SRE_USER = "sreUser";
    public static final String SRE_PASSWORD = "srePassword";
    public static final String SRE_JSON_FILE = "sreJsonFile";
    public static final String SRE_JSON_CONTENT = "sreJsonContent";
    public static final String SRE_POLL_INTERVAL = "srePollInterval";
    public static final String SRE_SKIP_SSL_VERIFY = "sreSkipSslVerify";
    public static final String SRE_HTTP_RETRY_COUNT = "sreHttpRetryCount";
    public static final String SRE_HTTP_RETRY_INTERVAL = "sreHttpRetryInterval";
    public static final String SRE_OUTPUT_JOB_ID = "sreJobId";
    public static final String SRE_OUTPUT_RETURN_CODE = "sreReturnCode";
    public static final String SRE_OUTPUT_RESPONSE = "sreResponse";
    }