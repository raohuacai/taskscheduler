/*
 * Description: Command-line REST client for scripted workflow engine interaction
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.ts.common.utils.Copyright;
import com.ts.model.entity.TSLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
/**
 * Standalone command-line client for invoking the TS workflow engine REST API.
 *
 * <p>Accepts a JSON command file specifying the operation (start, cancel, retry,
 * skip, etc.) and target workflow/task instance IDs, then calls the corresponding
 * HTTP endpoint and prints the result. Intended for scripting and CI/CD pipeline
 * integration.
 */
public class WorkflowCommandClient {
    private static final Logger logger = LogManager.getLogger(WorkflowCommandClient.class);

    // Flag to enable/disable colorized output and special characters
    private static boolean simpleMode = false;

    // ANSI colors for terminal output
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_CYAN = "\u001B[36m";

    // Default server settings
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8888;
    private static final String API_BASE_PATH = "/api";
    private static final String AUTH_PATH = API_BASE_PATH + "/auth";
    private static final String WORKFLOW_PATH = API_BASE_PATH + "/workflow";
    private static final String LOCK_PATH = API_BASE_PATH + "/lock";

    // HTTP client settings
    private static final int CONNECT_TIMEOUT = 5000; // 5 seconds
    private static final int READ_TIMEOUT = 30000;   // 30 seconds

    private final String serverUrl;
    private final String authUrl;
    private final String workflowUrl;
    private final String lockUrl;
    private final Gson gson;

    // Authentication token
    private String authToken = null;
    private String username = null;

    /**
     * Constructor with server host and port
     */
    public WorkflowCommandClient(String host, int port) {
        this.serverUrl = String.format("http://%s:%d", host, port);
        this.authUrl = this.serverUrl + AUTH_PATH;
        this.workflowUrl = this.serverUrl + WORKFLOW_PATH;
        this.lockUrl = this.serverUrl + LOCK_PATH;
        this.gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").create();

        logger.debug("Workflow command client initialized with server URL: {}", serverUrl);
    }

    /**
     * Constructor with default host and port
     */
    public WorkflowCommandClient() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }

    /**
     * Main method to start the command-line client
     */
    public static void main(String[] args) {
        try {
            // Don't remove this line
            Copyright.printCopyright();

            // Parse command-line arguments for host and port
            String host = DEFAULT_HOST;
            int port = DEFAULT_PORT;
            String username = null;
            String password = null;

            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("--host") && i + 1 < args.length) {
                    host = args[i + 1];
                    i++;
                } else if (args[i].equals("--port") && i + 1 < args.length) {
                    try {
                        port = Integer.parseInt(args[i + 1]);
                        i++;
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid port number: " + args[i + 1]);
                        System.exit(1);
                    }
                } else if (args[i].equals("--username") && i + 1 < args.length) {
                    username = args[i + 1];
                    i++;
                } else if (args[i].equals("--password") && i + 1 < args.length) {
                    password = args[i + 1];
                    i++;
                } else if (args[i].equals("--simple") || args[i].equals("--no-color")) {
                    simpleMode = true;
                }
            }

            // Create client
            WorkflowCommandClient client = new WorkflowCommandClient(host, port);

            // Authenticate if username and password provided
            if (username != null && password != null) {
                boolean authenticated = client.authenticate(username, password);
                if (!authenticated) {
                    System.err.println("Authentication failed. Exiting.");
                    System.exit(1);
                }
            }

            // Start command loop
            client.startCommandLoop();

        } catch (Exception e) {
            // Don't print stack trace to console
            System.err.println("Error: " + e.getMessage());
            // But log it for debugging
            LogManager.getLogger(WorkflowCommandClient.class).error("Error in workflow command client", e);
            System.exit(1);
        }
    }

    /**
     * Authenticate with the server
     *
     * @param username The username
     * @param password The password
     * @return true if authentication was successful, false otherwise
     */
    public boolean authenticate(String username, String password) {
        try {
            // Prepare request body
            Map<String, Object> credentials = new HashMap<>();
            credentials.put("username", username);
            credentials.put("password", password);

            // Send login request
            Map<String, Object> response = sendPostRequest(authUrl + "/login", credentials, false);

            if (response.containsKey("token")) {
                // Authentication successful
                this.authToken = (String) response.get("token");
                this.username = username;
                printSuccess("Authentication successful. Welcome, " + username + "!");
                return true;
            } else {
                // Authentication failed
                String errorMsg = response.containsKey("error") ?
                        (String) response.get("error") : "Unknown authentication error";
                printError("Authentication failed: " + errorMsg);
                return false;
            }
        } catch (Exception e) {
            printError("Authentication error: " + extractErrorMessage(e));
            logger.error("Authentication error", e);
            return false;
        }
    }

    /**
     * Start the interactive command loop
     */
    public void startCommandLoop() {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        printWelcomeMessage();

        // If not authenticated yet, prompt for credentials
        if (authToken == null) {
            boolean authenticated = false;
            while (!authenticated) {
                System.out.print("Username: ");
                String username = scanner.nextLine().trim();
                System.out.print("Password: ");
                String password = scanner.nextLine().trim();

                authenticated = authenticate(username, password);
                if (!authenticated) {
                    System.out.println("Try again or press Ctrl+C to exit.");
                }
            }

            // Print welcome message again after authentication
            printWelcomeMessage();
        }

        while (running) {
            try {
                printPrompt();
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) {
                    continue;
                }

                String[] command = input.split("\\s+", 2);
                String cmd = command[0].toLowerCase();

                switch (cmd) {
                    case "help":
                        printHelp();
                        break;
                    case "start":
                        handleStartWorkflow(command);
                        break;
                    case "recover":
                        handleRecoverWorkflow(command);
                        break;
                    case "cancel":
                        handleCancelWorkflow(command);
                        break;
                    case "retry":
                        handleRetryTask(command);
                        break;
                    case "skip":
                        handleSkipTask(command);
                        break;
                    case "canceltask":
                        handleCancelTask(command);
                        break;
                    case "info":
                        handleWorkflowInfo(command);
                        break;
                    case "tasks":
                        handleTasksList(command);
                        break;
                    case "lock":
                        handleLockCommands(command);
                        break;
                    case "logout":
                        authToken = null;
                        username = null;
                        printSuccess("Logged out successfully.");
                        // Prompt for new credentials
                        boolean authenticated = false;
                        while (!authenticated) {
                            System.out.print("Username: ");
                            String newUsername = scanner.nextLine().trim();
                            System.out.print("Password: ");
                            String newPassword = scanner.nextLine().trim();

                            authenticated = authenticate(newUsername, newPassword);
                            if (!authenticated) {
                                System.out.println("Try again or press Ctrl+C to exit.");
                            }
                        }
                        break;
                    case "exit":
                    case "quit":
                        running = false;
                        printMessage(ANSI_YELLOW, "Exiting workflow command client...");
                        break;
                    default:
                        printError("Unknown command. Type 'help' to see available commands.");
                }
            } catch (Exception e) {
                // Just show a simplified error message
                printError("Error processing command: " + extractErrorMessage(e));
                // Log the full stack trace
                logger.error("Command processing error", e);
            }
        }

        scanner.close();
    }

    /**
     * Extract a user-friendly error message from an exception
     */
    private String extractErrorMessage(Exception e) {
        String message = e.getMessage();

        if (message != null) {
            // Check if contains JSON error response
            if (message.contains("{\"error\":\"")) {
                try {
                    // Try to extract error message from JSON
                    int start = message.indexOf("{\"error\":\"") + 10;
                    int end = message.indexOf("\"}", start);
                    if (end > start) {
                        return message.substring(start, end);
                    }
                } catch (Exception ex) {
                    // If parsing fails, return original message
                }
            }

            // If message contains HTTP status, return only error part
            if (message.contains("HTTP ") && message.contains(": ")) {
                return message.substring(message.indexOf(": ") + 2);
            }

            return message;
        }

        return e.getClass().getSimpleName();
    }

    /**
     * Print welcome message
     */
    private void printWelcomeMessage() {
        if (simpleMode) {
            System.out.println("====================================================");
            System.out.println("        WORKFLOW ENGINE COMMAND LINE CLIENT        ");
            System.out.println("====================================================");
            System.out.println("Connected to server: " + serverUrl);
            if (username != null) {
                System.out.println("Logged in as: " + username);
            }
            System.out.println("Type 'help' to see available commands.");
            System.out.println();
        } else {
            System.out.println(ANSI_CYAN + "====================================================" + ANSI_RESET);
            System.out.println(ANSI_CYAN + "        WORKFLOW ENGINE COMMAND LINE CLIENT        " + ANSI_RESET);
            System.out.println(ANSI_CYAN + "====================================================" + ANSI_RESET);
            System.out.println("Connected to server: " + serverUrl);
            if (username != null) {
                System.out.println("Logged in as: " + ANSI_GREEN + username + ANSI_RESET);
            }
            System.out.println("Type 'help' to see available commands.");
            System.out.println();
        }
    }

    /**
     * Print command prompt
     */
    private void printPrompt() {
        if (simpleMode) {
            System.out.print(username + "@workflow> ");
        } else {
            System.out.print(ANSI_GREEN + username + ANSI_RESET + "@" + ANSI_BLUE + "workflow> " + ANSI_RESET);
        }
    }

    /**
     * Print help information
     */
    private void printHelp() {
        if (simpleMode) {
            System.out.println("Available commands:");
        } else {
            System.out.println(ANSI_CYAN + "Available commands:" + ANSI_RESET);
        }

        System.out.println("  help                          - Show this help message");
        System.out.println("  start <workflowId> [params]   - Start a new workflow");
        System.out.println("                                  params format: key1=value1,key2=value2");
        System.out.println("  recover <wfInstanceId>        - Recover a workflow instance");
        System.out.println("  cancel <wfInstanceId>         - Cancel a workflow instance");
        System.out.println("  retry <taskInstanceId>        - Retry a failed task");
        System.out.println("  skip <taskInstanceId>         - Skip a task");
        System.out.println("  canceltask <taskInstanceId>   - Cancel a task");
        System.out.println("  info <wfInstanceId>           - Show workflow instance information");
        System.out.println("  tasks <wfInstanceId>          - List tasks for a workflow instance");
        System.out.println("  lock release <lockName>       - Release a lock by name");
        System.out.println("  lock releasebyowner <owner>   - Release all locks by owner");
        System.out.println("  lock releaseall               - Release all locks");
        System.out.println("  lock list [owner|name <value>] - List all locks, optionally filtered by owner or name");
        System.out.println("  logout                        - Log out and login as different user");
        System.out.println("  exit/quit                     - Exit the program");
    }

    /**
     * Handle lock-related commands
     */
    private void handleLockCommands(String[] command) {
        if (command.length < 2) {
            printError("Missing lock command. Usage: lock <command> [parameters]");
            return;
        }

        String[] subCommand = command[1].split("\\s+", 2);
        String cmd = subCommand[0].toLowerCase();

        switch (cmd) {
            case "release":
                handleReleaseLock(subCommand);
                break;
            case "releaseall":
                handleReleaseAllLocks();
                break;
            case "releasebyowner":
                handleReleaseAllLocksByOwner(subCommand);
                break;
            case "list":
                handleListLocks(subCommand);
                break;
            default:
                printError("Unknown lock command. Valid commands: release, releaseall, releasebyowner, list");
                break;
        }
    }

    /**
     * Handle release lock command
     */
    private void handleReleaseLock(String[] command) {
        if (command.length < 2) {
            printError("Missing lock name. Usage: lock release <lockName>");
            return;
        }

        String lockName = command[1].trim();

        try {
            // Prepare request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("lockName", lockName);

            // Send request
            Map<String, Object> response = sendPostRequest(lockUrl + "/release", requestBody, true);

            // Process response
            String status = (String) response.get("status");

            if ("success".equals(status)) {
                printSuccess("Lock released: " + lockName);
            } else {
                printWarning("Failed to release lock: " + lockName);
            }
        } catch (Exception e) {
            printError("Error releasing lock: " + extractErrorMessage(e));
            logger.error("Error releasing lock", e);
        }
    }

    /**
     * Handle release all locks command
     */
    private void handleReleaseAllLocks() {
        try {
            // Send request with empty payload
            Map<String, Object> response = sendPostRequest(lockUrl + "/releaseAll", new HashMap<>(), true);

            // Process response
            String status = (String) response.get("status");

            if ("success".equals(status)) {
                int releasedCount = ((Double) response.get("releasedCount")).intValue();
                printSuccess("Released " + releasedCount + " locks.");
            } else {
                printWarning("Failed to release all locks.");
            }
        } catch (Exception e) {
            printError("Error releasing all locks: " + extractErrorMessage(e));
            logger.error("Error releasing all locks", e);
        }
    }

    /**
     * Handle release all locks by owner command
     */
    private void handleReleaseAllLocksByOwner(String[] command) {
        if (command.length < 2) {
            printError("Missing lock owner. Usage: lock releasebyowner <lockOwner>");
            return;
        }

        String lockOwner = command[1].trim();

        try {
            // Prepare request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("lockOwner", lockOwner);

            // Send request
            Map<String, Object> response = sendPostRequest(lockUrl + "/releaseByOwner", requestBody, true);

            // Process response
            String status = (String) response.get("status");

            if ("success".equals(status)) {
                int releasedCount = ((Double) response.get("releasedCount")).intValue();
                printSuccess("Released " + releasedCount + " locks for owner: " + lockOwner);
            } else {
                printWarning("Failed to release locks for owner: " + lockOwner);
            }
        } catch (Exception e) {
            printError("Error releasing locks by owner: " + extractErrorMessage(e));
            logger.error("Error releasing locks by owner", e);
        }
    }

    /**
     * Handle list locks command
     */
    private void handleListLocks(String[] command) {
        try {
            String endpoint = lockUrl + "/list";

            // Check if filtering is requested
            if (command.length > 1) {
                String[] filterArgs = command[1].split("\\s+", 2);
                String filterType = filterArgs[0].toLowerCase();

                if (filterArgs.length < 2) {
                    printError("Missing filter value. Usage: lock list [owner|name <value>]");
                    return;
                }

                String filterValue = filterArgs[1].trim();

                if ("owner".equals(filterType)) {
                    endpoint += "?owner=" + filterValue;
                } else if ("name".equals(filterType)) {
                    endpoint += "?name=" + filterValue;
                } else {
                    printError("Invalid filter type. Use 'owner' or 'name'.");
                    return;
                }
            }

            // Send request
            Object response = sendGetRequest(endpoint);

            // The response should be a List of Maps, each representing a lock
            if (response instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> locks = (List<Map<String, Object>>) response;

                // Format and print locks
                if (simpleMode) {
                    System.out.println("Locks in the system:");
                } else {
                    System.out.println(ANSI_CYAN + "Locks in the system:" + ANSI_RESET);
                }

                if (locks.isEmpty()) {
                    System.out.println("No locks found.");
                    return;
                }

                System.out.println(String.format("%-20s %-30s %-10s %-25s",
                        "Lock Name", "Lock Owner", "Lock Type", "Lock Date"));
                System.out.println("-------------------------------------------------------------------------------------");

                for (Map<String, Object> lock : locks) {
                    String lockDate = lock.get("lockDate") != null ? lock.get("lockDate").toString() : "N/A";

                    System.out.println(String.format("%-20s %-30s %-10s %-25s",
                            lock.get("lockName"),
                            lock.get("lockOwner"),
                            lock.get("lockType"),
                            lockDate));
                }
            } else if (response instanceof Map) {
                // This is likely an error response
                @SuppressWarnings("unchecked")
                Map<String, Object> errorResponse = (Map<String, Object>) response;
                if (errorResponse.containsKey("error")) {
                    printError("Failed to list locks: " + errorResponse.get("error"));
                } else {
                    printError("Failed to list locks: Unknown error");
                }
            } else {
                printError("Unexpected response format when listing locks");
            }
        } catch (Exception e) {
            printError("Error listing locks: " + extractErrorMessage(e));
            logger.error("Error listing locks", e);
        }
    }

    /**
     * Handle start workflow command
     */
    private void handleStartWorkflow(String[] command) {
        if (command.length < 2) {
            printError("Missing workflow ID. Usage: start <workflowId> [params]");
            return;
        }

        String[] args = command[1].split("\\s+", 2);
        String workflowId = args[0];
        Map<String, Object> parameters = new HashMap<>();

        // Parse parameters if provided
        if (args.length > 1 && !args[1].trim().isEmpty()) {
            String[] params = args[1].split(",");
            for (String param : params) {
                String[] keyValue = param.split("=", 2);
                if (keyValue.length == 2) {
                    parameters.put(keyValue[0].trim(), keyValue[1].trim());
                }
            }
        }

        try {
            // Prepare request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("workflowId", workflowId);
            requestBody.put("parameters", parameters);

            // Send request
            Map<String, Object> response = sendPostRequest(workflowUrl + "/start", requestBody, true);

            // Process response based on status field
            String status = (String) response.get("status");

            if ("success".equals(status)) {
                // Success case
                String instanceId = (String) response.get("instanceId");
                printSuccess("Workflow started. Instance ID: " + instanceId);
            } else {
                // Failure case - show error message
                String errorMessage = response.containsKey("error") ?
                        (String) response.get("error") :
                        "Unknown error occurred";
                printWarning("Failed to start workflow: " + errorMessage);
            }
        } catch (Exception e) {
            // Network or parsing errors
            printError("Connection error: " + extractErrorMessage(e));
            logger.error("Failed to start workflow", e);
        }
    }

    /**
     * Handle recover workflow command
     */
    private void handleRecoverWorkflow(String[] command) {
        if (command.length < 2) {
            printError("Missing workflow instance ID. Usage: recover <wfInstanceId>");
            return;
        }

        String wfInstanceId = command[1].trim();

        try {
            // Prepare request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("instanceId", wfInstanceId);

            // Send request
            Map<String, Object> response = sendPostRequest(workflowUrl + "/recover/" + wfInstanceId, requestBody, true);

            // Process response
            String status = (String) response.get("status");

            if ("success".equals(status)) {
                printSuccess("Workflow instance recovered: " + wfInstanceId);
            } else {
                printWarning("Failed to recover workflow instance: " + wfInstanceId);
            }
        } catch (Exception e) {
            printError("Error recovering workflow: " + extractErrorMessage(e));
            logger.error("Error recovering workflow", e);
        }
    }

    /**
     * Handle cancel workflow command
     */
    private void handleCancelWorkflow(String[] command) {
        if (command.length < 2) {
            printError("Missing workflow instance ID. Usage: cancel <wfInstanceId>");
            return;
        }

        String wfInstanceId = command[1].trim();

        try {
            // Prepare request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("instanceId", wfInstanceId);

            // Send request
            Map<String, Object> response = sendPostRequest(workflowUrl + "/cancel/" + wfInstanceId, requestBody, true);

            // Process response
            String status = (String) response.get("status");

            if ("success".equals(status)) {
                printSuccess("Workflow instance cancelled: " + wfInstanceId);
            } else {
                printWarning("Failed to cancel workflow instance: " + wfInstanceId);
            }
        } catch (Exception e) {
            printError("Error cancelling workflow: " + extractErrorMessage(e));
            logger.error("Error cancelling workflow", e);
        }
    }

    /**
     * Handle retry task command
     */
    private void handleRetryTask(String[] command) {
        if (command.length < 2) {
            printError("Missing task instance ID. Usage: retry <taskInstanceId>");
            return;
        }

        String taskInstanceId = command[1].trim();

        try {
            // Prepare request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("taskInstanceId", taskInstanceId);

            // Send request
            Map<String, Object> response = sendPostRequest(workflowUrl + "/retry/" + taskInstanceId, requestBody, true);

            // Process response
            String status = (String) response.get("status");

            if ("success".equals(status)) {
                printSuccess("Task retry initiated: " + taskInstanceId);
            } else {
                printWarning("Failed to retry task: " + taskInstanceId);
            }
        } catch (Exception e) {
            printError("Error retrying task: " + extractErrorMessage(e));
            logger.error("Error retrying task", e);
        }
    }

    /**
     * Handle skip task command
     */
    private void handleSkipTask(String[] command) {
        if (command.length < 2) {
            printError("Missing task instance ID. Usage: skip <taskInstanceId>");
            return;
        }

        String taskInstanceId = command[1].trim();

        try {
            // Prepare request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("taskInstanceId", taskInstanceId);

            // Send request
            Map<String, Object> response = sendPostRequest(workflowUrl + "/skip/" + taskInstanceId, requestBody, true);

            // Process response
            String status = (String) response.get("status");

            if ("success".equals(status)) {
                printSuccess("Task skipped: " + taskInstanceId);
            } else {
                printWarning("Failed to skip task: " + taskInstanceId);
            }
        } catch (Exception e) {
            printError("Error skipping task: " + extractErrorMessage(e));
            logger.error("Error skipping task", e);
        }
    }

    /**
     * Handle cancel task command
     */
    private void handleCancelTask(String[] command) {
        if (command.length < 2) {
            printError("Missing task instance ID. Usage: canceltask <taskInstanceId>");
            return;
        }

        String taskInstanceId = command[1].trim();

        try {
            // Prepare request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("taskInstanceId", taskInstanceId);

            // Send request
            Map<String, Object> response = sendPostRequest(workflowUrl + "/canceltask/" + taskInstanceId, requestBody, true);

            // Process response
            String status = (String) response.get("status");

            if ("success".equals(status)) {
                printSuccess("Task cancelled: " + taskInstanceId);
            } else {
                printWarning("Failed to cancel task: " + taskInstanceId);
            }
        } catch (Exception e) {
            printError("Error cancelling task: " + extractErrorMessage(e));
            logger.error("Error cancelling task", e);
        }
    }

    /**
     * Handle workflow info command
     */
    private void handleWorkflowInfo(String[] command) {
        if (command.length < 2) {
            printError("Missing workflow instance ID. Usage: info <wfInstanceId>");
            return;
        }

        String wfInstanceId = command[1].trim();

        try {
            // Send request
            Object response = sendGetRequest(workflowUrl + "/info/" + wfInstanceId);

            // The response should be a Map representing the workflow instance
            @SuppressWarnings("unchecked")
            Map<String, Object> wfInstance = (Map<String, Object>) response;

            // Format and print workflow information
            if (simpleMode) {
                System.out.println("Workflow Instance Information:");
            } else {
                System.out.println(ANSI_CYAN + "Workflow Instance Information:" + ANSI_RESET);
            }

            System.out.println("  Instance ID:  " + wfInstance.get("wfInstanceId"));
            System.out.println("  Workflow ID:  " + wfInstance.get("workflowId"));
            System.out.println("  Status:       " + wfInstance.get("status"));

            // Format dates for display
            if (wfInstance.get("startDate") != null) {
                String startDate = wfInstance.get("startDate").toString();
                System.out.println("  Start Date:   " + startDate);
            } else {
                System.out.println("  Start Date:   N/A");
            }

            if (wfInstance.get("endDate") != null) {
                String endDate = wfInstance.get("endDate").toString();
                System.out.println("  End Date:     " + endDate);
            } else {
                System.out.println("  End Date:     N/A");
            }

            System.out.println("  Error Flag:   " + wfInstance.get("errorFlag"));

            if ("Y".equals(wfInstance.get("errorFlag"))) {
                System.out.println("  Error Msg:    " + wfInstance.get("errorMessage"));
            }
        } catch (Exception e) {
            printError("Error retrieving workflow info: " + extractErrorMessage(e));
            logger.error("Error retrieving workflow info", e);
        }
    }

    /**
     * Handle tasks list command
     */
    private void handleTasksList(String[] command) {
        if (command.length < 2) {
            printError("Missing workflow instance ID. Usage: tasks <wfInstanceId>");
            return;
        }

        String wfInstanceId = command[1].trim();

        try {
            // Send request
            Object response = sendGetRequest(workflowUrl + "/tasks/" + wfInstanceId);

            // The response should be a List of Maps, each representing a task instance
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) response;

            // Format and print tasks
            if (simpleMode) {
                System.out.println("Tasks for Workflow Instance " + wfInstanceId + ":");
            } else {
                System.out.println(ANSI_CYAN + "Tasks for Workflow Instance " + wfInstanceId + ":" + ANSI_RESET);
            }

            System.out.println(String.format("%-30s %-15s %-15s %-25s %-25s",
                    "Task Instance ID", "Task ID", "Status", "Start Date", "End Date"));
            System.out.println("-------------------------------------------------------------------------------------------------------");

            for (Map<String, Object> task : tasks) {
                String startDate = task.get("startDate") != null ? task.get("startDate").toString() : "N/A";
                String endDate = task.get("endDate") != null ? task.get("endDate").toString() : "N/A";

                System.out.println(String.format("%-30s %-15s %-15s %-25s %-25s",
                        task.get("taskInstanceId"),
                        task.get("taskId"),
                        task.get("status"),
                        startDate,
                        endDate));

                if ("Y".equals(task.get("errorFlag"))) {
                    if (simpleMode) {
                        System.out.println("   Error: " + task.get("errorMessage"));
                    } else {
                        System.out.println(ANSI_RED + "   Error: " + task.get("errorMessage") + ANSI_RESET);
                    }
                }
            }
        } catch (Exception e) {
            printError("Error retrieving tasks: " + extractErrorMessage(e));
            logger.error("Error retrieving tasks", e);
        }
    }

    /**
     * Send a POST request to the server
     *
     * @param fullUrl     The full URL to send the request to
     * @param requestBody The request body to send
     * @param useAuth     Whether to include the authentication token in the request
     * @return The response as a Map
     */
    private Map<String, Object> sendPostRequest(String fullUrl, Map<String, Object> requestBody, boolean useAuth) {
        Map<String, Object> response = new HashMap<>();
        HttpURLConnection connection = null;

        try {
            // Create connection
            URL url = new URL(fullUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");

            // Add auth token if required and available
            if (useAuth && authToken != null) {
                connection.setRequestProperty("Authorization", "Bearer " + authToken);
            }

            connection.setDoOutput(true);

            // Send request body
            String jsonBody = gson.toJson(requestBody);
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Read response
            int responseCode = connection.getResponseCode();

            if (responseCode >= 200 && responseCode < 300) {
                // Read successful response
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder responseStr = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        responseStr.append(responseLine.trim());
                    }

                    // Parse JSON response
                    Type type = new TypeToken<Map<String, Object>>() {
                    }.getType();
                    return gson.fromJson(responseStr.toString(), type);
                }
            } else {
                // Read error response
                StringBuilder errorBuilder = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream() != null ?
                                connection.getErrorStream() : new java.io.ByteArrayInputStream(new byte[0]),
                                StandardCharsets.UTF_8))) {
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        errorBuilder.append(responseLine.trim());
                    }
                } catch (Exception e) {
                    // Ignore error stream reading errors
                }

                String errorBody = errorBuilder.toString();
                String errorMessage;

                if (errorBody != null && !errorBody.isEmpty()) {
                    try {
                        // Try to parse JSON error
                        Type type = new TypeToken<Map<String, Object>>() {
                        }.getType();
                        Map<String, Object> errorResponse = gson.fromJson(errorBody, type);
                        errorMessage = (String) errorResponse.get("error");
                    } catch (Exception jsonEx) {
                        // Not valid JSON or other parsing error, use status code
                        errorMessage = "HTTP " + responseCode + (errorBody.isEmpty() ? "" : ": " + errorBody);
                    }
                } else {
                    errorMessage = "HTTP " + responseCode;
                }

                // Return response object with error info instead of throwing exception
                response.put("status", "failure");
                response.put("error", errorMessage);

                // Log error, but don't throw
                logger.error("Server returned error: {}", errorMessage);
                return response;
            }
        } catch (Exception e) {
            // Handle connection, network errors
            String errorMessage = "Connection error: " + e.getMessage();
            response.put("status", "failure");
            response.put("error", errorMessage);

            // Log error, but don't throw
            logger.error("Request failed", e);
            return response;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Send GET request to server
     * Returns response object with error info instead of throwing exception
     *
     * @param fullUrl The full URL to send the request to
     * @return The response as an Object (usually a Map or List)
     */
    private Object sendGetRequest(String fullUrl) {
        HttpURLConnection connection = null;

        try {
            // Create connection
            URL url = new URL(fullUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("Accept", "application/json");

            // Add auth token if available
            if (authToken != null) {
                connection.setRequestProperty("Authorization", "Bearer " + authToken);
            }

            // Read response
            int responseCode = connection.getResponseCode();

            if (responseCode >= 200 && responseCode < 300) {
                // Read successful response
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }

                    // Parse JSON response
                    return gson.fromJson(response.toString(), Object.class);
                }
            } else {
                // Read error response
                StringBuilder errorBuilder = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream() != null ?
                                connection.getErrorStream() : new java.io.ByteArrayInputStream(new byte[0]),
                                StandardCharsets.UTF_8))) {
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        errorBuilder.append(responseLine.trim());
                    }
                } catch (Exception e) {
                    // Ignore error stream reading errors
                }

                String errorBody = errorBuilder.toString();
                String errorMessage;

                if (errorBody != null && !errorBody.isEmpty()) {
                    try {
                        // Try to parse JSON error
                        Type type = new TypeToken<Map<String, Object>>() {
                        }.getType();
                        Map<String, Object> errorResponse = gson.fromJson(errorBody, type);
                        errorMessage = (String) errorResponse.get("error");
                    } catch (Exception jsonEx) {
                        // Not valid JSON or other parsing error, use status code
                        errorMessage = "HTTP " + responseCode + (errorBody.isEmpty() ? "" : ": " + errorBody);
                    }
                } else {
                    errorMessage = "HTTP " + responseCode;
                }

                // Return response object with error info instead of throwing exception
                Map<String, Object> response = new HashMap<>();
                response.put("status", "failure");
                response.put("error", errorMessage);

                // Log error, but don't throw
                logger.error("Server returned error: {}", errorMessage);
                return response;
            }
        } catch (Exception e) {
            // Handle connection, network errors
            String errorMessage = "Connection error: " + e.getMessage();
            Map<String, Object> response = new HashMap<>();
            response.put("status", "failure");
            response.put("error", errorMessage);

            // Log error, but don't throw
            logger.error("Request failed", e);
            return response;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Print success message
     */
    private void printSuccess(String message) {
        printMessage(ANSI_GREEN, message);
    }

    /**
     * Print warning message
     */
    private void printWarning(String message) {
        printMessage(ANSI_YELLOW, message);
    }

    /**
     * Print error message
     */
    private void printError(String message) {
        printMessage(ANSI_RED, "ERROR: " + message);
    }

    /**
     * Print colored message
     */
    private void printMessage(String color, String message) {
        if (simpleMode) {
            System.out.println(message);
        } else {
            System.out.println(color + message + ANSI_RESET);
        }
    }
}