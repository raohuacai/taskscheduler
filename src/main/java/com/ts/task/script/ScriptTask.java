/*
 * Description: Base class for script-execution tasks: writes temp file, starts process, captures output
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.task.script;

import com.ts.common.constants.Constants;
import com.ts.common.utils.DateUtils;
import com.ts.common.utils.ParamUtils;
import com.ts.exception.TaskExecutionException;
import com.ts.exception.TaskTimeoutException;
import com.ts.service.impl.TaskLogService;
import com.ts.task.AbstractTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Base class for tasks that execute external scripts or processes.
 *
 * <p>Writes the script content to a temporary file, starts the process via
 * {@link java.lang.ProcessBuilder}, captures stdout and stderr on background
 * threads, and waits for exit with a timeout. Non-zero exit codes are treated
 * as task failures; the captured output is written to the task log.
 *
 * <p>Environment variables whose parameter keys are prefixed with {@code env.}
 * are automatically extracted and passed to the child process. The working
 * directory defaults to the system temporary directory if not specified.
 *
 * <p>The {@code deleteScriptFileOnCleanup} flag (default {@code true}) controls
 * whether the temporary script file is deleted after execution. Subclasses such
 * as {@link DataXTask} set this to {@code false} when the script path points to
 * a user-managed file that must not be deleted.
 *
 * @see ShellTask
 * @see CmdTask
 * @see PythonTask
 * @see DataXTask
 */
public abstract class ScriptTask extends AbstractTask {
    private static final Logger logger = LogManager.getLogger(ScriptTask.class);
    /**
     * The child {@link Process} spawned by {@link ProcessBuilder};
     * held so that {@link #cancel()} can call {@link Process#destroyForcibly()}.
     */
    protected Process process;

    /**
     * The temporary script file written to disk in {@link #createScriptFile()};
     * deleted in {@link #cleanupResources()} when {@link #deleteScriptFileOnCleanup} is {@code true}.
     */
    protected File scriptFile;

    /**
     * Raw script content read from the {@code script} task parameter;
     * written verbatim to {@link #scriptFile} (possibly with a preamble added by the subclass).
     */
    protected String scriptContent;

    /**
     * Working directory path passed to {@link ProcessBuilder# directory};
     * defaults to the system temp directory when not specified.
     */
    protected String workingDir;
    //protected String outputFile;
    //protected String errorFile;

    /**
     * Base file name (without path) used when naming the temporary script file;
     * typically derived from the task name and instance ID.
     */
    protected String fileName;
    /**
     * Environment variables collected from task parameters prefixed with {@code env.};
     * passed verbatim to the child process via {@link ProcessBuilder#environment()}.
     */
    protected Map<String, String> environmentVariables = new HashMap<>();
    /**
     * Whether to delete the script file after the task finishes.
     * Defaults to {@code true} (suitable for dynamically generated temporary scripts).
     * Subclasses may override this value to protect user-provided files (e.g. DataX JSON job files).
     */
    protected boolean deleteScriptFileOnCleanup = true;

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

        // Retrieve the script content from task parameters
        scriptContent = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TASK_SCRIPT, taskId, taskInstanceId);
        // Retrieve the working directory from task parameters
        workingDir = ParamUtils.getParameter(parameters, Constants.PARAM_TASK_WORKINGDIR, System.getProperty("java.io.tmpdir"));
        validateWorkingDirectory(workingDir);
        // get file name
        fileName = ParamUtils.getParameter(parameters, Constants.PARAM_TASK_NAME, null) + "-"
                + DateUtils.getCurrentDateTimeString() + "-" + ParamUtils.getParameter(parameters, Constants.PARAM_TASK_INSTANCE, null);
        // Process environment variable parameters
        processEnvironmentVariables(parameters);
        // Create the temporary script file on disk
        createScriptFile();
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
        // Prepare the command-line arguments for execution
        List<String> command = buildCommand();
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File(workingDir));

        // Set environment variables for the process
        if (!environmentVariables.isEmpty()) {
            Map<String, String> env = processBuilder.environment();
            env.putAll(environmentVariables);
        }

        // Start the script process
        try {
            logger.info("Starting script process: {}, Task: {}, {}, {}", String.join(" ", command), taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Starting script process: " + String.join(" ", command));
            process = processBuilder.start();
            writeScriptToProcess(process);
        } catch (IOException e) {
            throw new TaskExecutionException("Failed to start script process: " + e.getMessage(), e, taskId, taskInstanceId);
        }

        // Capture stdout and stderr streams
        List<String> outputLines = new ArrayList<>();
        List<String> errorLines = new ArrayList<>();

        Thread outputThread = startStreamReader(process.getInputStream(), outputLines, "OUTPUT");
        Thread errorThread = startStreamReader(process.getErrorStream(), errorLines, "ERROR");

        // Wait for the process to complete with timeout
        boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);

        // Wait for stream reader threads to finish
        outputThread.join(500);
        errorThread.join(500);

        // Timeout branch: exit early without reading the exit code
        if (!completed) {
            logger.warn("Script execution timed out after {} seconds, Task: {}, {}, {}", timeout, taskId, taskName, taskInstanceId);
            process.destroyForcibly();
            // Wait briefly for the process to actually terminate
            boolean reallyExited = process.waitFor(FORCE_TERMINATE_TIMEOUT, TimeUnit.SECONDS);
            if (!reallyExited) {
                logger.warn("Process did not terminate within {} seconds after destroyForcibly, Task: {}, {}, {}",
                        FORCE_TERMINATE_TIMEOUT, taskId, taskName, taskInstanceId);
            }
            throw new TaskTimeoutException("Script execution timed out after " + timeout + " seconds", null, taskId, taskInstanceId);
        }

        // Only read the exit code when the process completed normally
        int exitCode = process.exitValue();
        logger.debug("Script process exited with code: {}", exitCode);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Script process exited with code: " + exitCode);

        saveParam(parameters, Constants.PARAM_TASK_EXITCODE, exitCode);

        // Check for non-zero exit code indicating script failure
        if (exitCode != 0) {
            String errorDetails = buildErrorDetails(exitCode, outputLines, errorLines);
            throw new TaskExecutionException("Script execution failed with exit code: " + exitCode + "\n" + errorDetails, null, taskId, taskInstanceId);
        }
    }

    @Override
    /**
     * Post the action.
     *
     * @param parameters  parameter map
     * @throws Exception  if an error occurs
     */
    public void postAction(Map<String, Object> parameters) throws Exception {
        super.postAction(parameters);
    }

    @Override
    /**
     * Cancels.
     *
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean cancel() {
        try {
            return super.cancel();
        } finally {
            // If the process is still running, force-terminate it
            if (process != null) {
                try {
                    process.destroyForcibly();
                    // Wait briefly for the process to actually terminate
                    process.waitFor(FORCE_TERMINATE_TIMEOUT, TimeUnit.SECONDS);
                    logger.info("Script process forcibly terminated, Task:  {}, {}, {}", taskId, taskName, taskInstanceId);
                } catch (InterruptedException e) {
                    //Thread.currentThread().interrupt();
                    logger.warn("Interrupted while waiting for process termination", e);
                } catch (Exception e) {
                    logger.error("Error terminating script process, Task:  {}, {}, {}", taskId, taskName, taskInstanceId, e);
                }
            }
            // Delete the temporary script file
            //cleanupScriptFile();
        }
    }

    @Override
    /**
     * Cleanup the resources.
     */
    public void cleanupResources() {
        cleanupScriptFile();
        super.cleanupResources();
    }

    /**
     * Creates a temporary script file on disk and writes the script content to it
     *
     * @throws Exception if the file cannot be created or written
     */
    protected abstract void createScriptFile() throws Exception;

    /**
     * Builds the command-line argument list used to execute the script
     *
     * @return a list of command-line arguments (interpreter + script path)
     */
    protected abstract List<String> buildCommand();

    /**
     * Validates that the specified working directory exists and is accessible
     *
     * @param directory the directory path string to validate
     * @throws TaskExecutionException if the directory does not exist or is not accessible
     */
    protected void validateWorkingDirectory(String directory) throws TaskExecutionException {
        File dir = new File(directory);
        if (!dir.exists()) {
            throw new TaskExecutionException("Working directory does not exist: " + directory, null, taskId, taskInstanceId);
        }
        if (!dir.isDirectory()) {
            throw new TaskExecutionException("Working directory path is not a directory: " + directory, null, taskId, taskInstanceId);
        }
        if (!dir.canRead() || !dir.canWrite()) {
            throw new TaskExecutionException("Insufficient permissions for working directory: " + directory, null, taskId, taskInstanceId);
        }
    }

    /**
     * Hook for subclasses to write content to the process's standard input
     * before the stream reader threads start. The default implementation does nothing.
     *
     * @param process the newly started process
     * @throws IOException if an I/O error occurs
     */
    protected void writeScriptToProcess(Process process) throws IOException {
        // Default: no-op
    }

    /**
     * Starts a background thread that reads lines from the given input stream
     *
     * @param inputStream the stream to read from
     * @param lines       the list to which each read line will be appended
     * @param type        a label for the stream type (e.g. "OUTPUT" or "ERROR")
     * @return the started Thread instance
     */
    private Thread startStreamReader(InputStream inputStream, List<String> lines, String type) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);

                    // Record each output line as it is read
                    if ("OUTPUT".equals(type)) {
                        logger.debug("[SCRIPT_OUTPUT] {}", line);
                        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "[SCRIPT_OUTPUT] " + line);
                    } else {
                        logger.warn("[SCRIPT_ERROR] {}", line);
                        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "[SCRIPT_ERROR] " + line);
                    }

                    // Periodically check whether the task has been cancelled
                    if (isCanceled()) {
                        logger.debug("Script task canceled, stopping stream reader");
                        break;
                    }
                }
            } catch (IOException e) {
                if (!isCanceled()) {
                    logger.error("Error reading process {} stream", type, e);
                }
            }
        });

        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * Builds a detailed error message from the script exit code and captured output
     *
     * @param exitCode    the process exit code
     * @param outputLines captured stdout lines
     * @param errorLines  captured stderr lines
     * @return a formatted error detail string
     */
    private String buildErrorDetails(int exitCode, List<String> outputLines, List<String> errorLines) {
        StringBuilder details = new StringBuilder();
        details.append("Exit Code: ").append(exitCode).append("\n");

        if (!errorLines.isEmpty()) {
            details.append("=== Error Output ===\n");
            int lineCount = Math.min(20, errorLines.size());
            for (int i = 0; i < lineCount; i++) {
                details.append(errorLines.get(i)).append("\n");
            }
            if (errorLines.size() > lineCount) {
                details.append("... (").append(errorLines.size() - lineCount).append(" more lines)\n");
            }
        }

        if (!outputLines.isEmpty()) {
            details.append("=== Standard Output ===\n");
            int lineCount = Math.min(10, outputLines.size());
            for (int i = 0; i < lineCount; i++) {
                details.append(outputLines.get(i)).append("\n");
            }
            if (outputLines.size() > lineCount) {
                details.append("... (").append(outputLines.size() - lineCount).append(" more lines)\n");
            }
        }

        return details.toString();
    }

    /**
     * Processes environment variable parameters from the task parameter map
     *
     * @param parameters the parameter map supplying substitution values
     */
    private void processEnvironmentVariables(Map<String, Object> parameters) {
        // Collect parameters prefixed with "env." and add them to the process environment
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getKey().startsWith("env.") && entry.getValue() != null) {
                String envName = entry.getKey().substring(4); // Strip the "env." prefix
                if (!envName.isEmpty()) {
                    environmentVariables.put(envName, entry.getValue().toString());
                }
            }
        }

        logger.debug("Setting {} environment variables for script execution", environmentVariables.size());
    }

    /**
     * Cleans up the temporary script file created during task execution
     */
    private void cleanupScriptFile() {
        if (!deleteScriptFileOnCleanup) {
            logger.debug("Skipping script file deletion as per task configuration: {}",
                    scriptFile != null ? scriptFile.getAbsolutePath() : "null");
            return;
        }
        if (scriptFile != null && scriptFile.exists()) {
            try {
                boolean deleted = scriptFile.delete();
                if (deleted) {
                    logger.debug("Deleted temporary script file: {}", scriptFile.getAbsolutePath());
                } else {
                    logger.warn("Failed to delete temporary script file: {}", scriptFile.getAbsolutePath());
                    // Mark the file for deletion when the JVM exits
                    scriptFile.deleteOnExit();
                }
            } catch (Exception e) {
                logger.warn("Error deleting temporary script file: {}", scriptFile.getAbsolutePath(), e);
                // Mark the file for deletion when the JVM exits
                scriptFile.deleteOnExit();
            }
        }
    }
}