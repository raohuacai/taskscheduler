/*
 * Description: Task type running DataX data-synchronisation jobs from inline JSON or file path
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.task.script;

import com.ts.exception.TaskExecutionException;
import com.ts.service.impl.TaskLogService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/**
 * Task type that runs a DataX data-synchronisation job.
 *
 * <p>Supports two modes:
 * <ul>
 *   <li><b>Inline JSON</b> ({@code script} starts with {@code {}) — writes the
 *       JSON content to a temporary file and passes it to {@code datax.py}.</li>
 *   <li><b>File path</b> — treats {@code script} as the path to an existing
 *       DataX job JSON file and passes it directly; the file is not deleted
 *       after execution ({@code deleteScriptFileOnCleanup = false}).</li>
 * </ul>
 *
 * <p>The DataX Python command is resolved from {@code env.PYTHON_COMMAND}
 * (if set) or auto-detected per OS. The working directory must be the DataX
 * installation root so that {@code bin/datax.py} is resolvable.
 *
 * @see ScriptTask
 */
public class DataXTask extends ScriptTask {
    private static final Logger logger = LogManager.getLogger(DataXTask.class);

    private String pythonCommand;

    @Override
    /**
      * Prepares resources and reads task parameters before the main action.
      * Called by {@link AbstractTask#execute} before {@link #action}.
      *
      * @param parameters  merged task parameter map supplied by the workflow engine
      * @throws Exception  if a required parameter is missing or resource initialisation fails
      */
    public void preAction(Map<String, Object> parameters) throws Exception {
        // The value of deleteScriptFileOnCleanup is determined inside createScriptFile() based on the mode;
        // set it to false here first; createScriptFile() will override it as needed
        this.deleteScriptFileOnCleanup = false;
        super.preAction(parameters);
    }

    @Override
    protected void createScriptFile() throws Exception {
        String trimmed = scriptContent.trim();

        if (trimmed.isEmpty()) {
            throw new TaskExecutionException(
                    "script parameter is required: provide a DataX job file path or JSON content",
                    null, taskId, taskInstanceId);
        }

        if (isJsonContent(trimmed)) {
            // ── Mode A: inline JSON job content
            this.deleteScriptFileOnCleanup = true;

            Path tempDir = Paths.get(workingDir);
            // Follow the same naming convention as CmdTask/PythonTask: taskName-timestamp-taskInstanceId
            String jobFileName = "datax_job_" + fileName + ".json";
            Path tempFile = tempDir.resolve(jobFileName);

            Files.write(tempFile, trimmed.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

            scriptFile = tempFile.toFile();
            logger.info("DataX job JSON written to temp file: {}, Task: {}, {}, {}",
                    scriptFile.getAbsolutePath(), taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                    "DataX job written to temp file: " + scriptFile.getAbsolutePath());
        } else {
            // ── Mode B:script is file
            this.deleteScriptFileOnCleanup = false;  // User-provided file; do not delete on cleanup

            Path jobPath = Paths.get(trimmed);
            if (!Files.exists(jobPath)) {
                throw new TaskExecutionException(
                        "DataX job file does not exist: " + trimmed,
                        null, taskId, taskInstanceId);
            }
            if (!Files.isRegularFile(jobPath)) {
                throw new TaskExecutionException(
                        "DataX job path is not a regular file: " + trimmed,
                        null, taskId, taskInstanceId);
            }
            scriptFile = jobPath.toFile();
            logger.info("Using DataX job file: {}, Task: {}, {}, {}",
                    scriptFile.getAbsolutePath(), taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                    "DataX job file: " + scriptFile.getAbsolutePath());
        }
    }

    @Override
    protected List<String> buildCommand() {
        // Determine the Python interpreter to use
        resolvePythonCommand();
        List<String> command = new ArrayList<>();
        command.add(pythonCommand);
        command.add(Paths.get(workingDir, "bin", "datax.py").toString());

        // Inject dynamic parameters (e.g. env.TIMESTAMP=...) as DataX "-p -Dkey=value" arguments,
        // so the job JSON can reference them via ${TIMESTAMP} etc., regardless of whether
        // the job was supplied as inline JSON or as a file path
        String dynamicParams = buildDynamicParamArgs();
        if (dynamicParams != null) {
            command.add("-p");
            command.add(dynamicParams);
        }

        command.add(scriptFile.getAbsolutePath());
        logger.debug("DataX command: {}, Task: {}, {}, {}",
                String.join(" ", command), taskId, taskName, taskInstanceId);
        return command;
    }

    @Override
    protected void validateWorkingDirectory(String directory) throws TaskExecutionException {
        // Invoke the parent class validation first (directory exists and is readable/writable)
        super.validateWorkingDirectory(directory);
        // DataX-specific validation: verify that bin/datax.py exists in the working directory
        Path dataxPy = Paths.get(directory, "bin", "datax.py");
        if (!Files.exists(dataxPy)) {
            throw new TaskExecutionException(
                    "DataX script does not exist: " + dataxPy.toAbsolutePath(),
                    null, taskId, taskInstanceId);
        }
        if (!Files.isRegularFile(dataxPy)) {
            throw new TaskExecutionException(
                    "DataX script is not a regular file: " + dataxPy.toAbsolutePath(),
                    null, taskId, taskInstanceId);
        }
        if (!Files.isExecutable(dataxPy)) {
            logger.warn("DataX script may not be executable: {}, Task: {}, {}, {}",
                    dataxPy.toAbsolutePath(), taskId, taskName, taskInstanceId);
        }
    }

    /**
     * Builds the DataX {@code -p} dynamic-parameter argument from {@code env.*} task parameters,
     * e.g. {@code env.TIMESTAMP=20240323120000} becomes {@code -DTIMESTAMP=20240323120000},
     * allowing the job JSON to reference it as {@code ${TIMESTAMP}} in SQL or other fields.
     *
     * <p>This works uniformly for both inline-JSON mode and file-path mode, since the
     * parameter is passed on the command line rather than by rewriting the job file.
     *
     * <p>{@code PYTHON_COMMAND} is reserved for interpreter selection (see
     * {@link #resolvePythonCommand()}) and is excluded from injection. Keys must match a
     * simple identifier pattern to prevent a crafted value from injecting extra {@code -D} flags.
     *
     * @return the joined {@code -Dkey=value} argument string, or {@code null} if there is nothing to inject
     */
    private String buildDynamicParamArgs() {
        StringBuilder params = new StringBuilder();
        for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
            String key = entry.getKey();
            if ("PYTHON_COMMAND".equals(key)) {
                continue;
            }
            if (!key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                logger.warn("Skipping invalid DataX dynamic parameter name: {}, Task: {}, {}, {}",
                        key, taskId, taskName, taskInstanceId);
                continue;
            }
            if (params.length() > 0) {
                params.append(" ");
            }
            params.append("-D").append(key).append("=").append(entry.getValue());
        }
        return params.length() > 0 ? params.toString() : null;
    }

    @Override
    /**
     * Cancels.
     *
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean cancel() {
        // Collect child processes first; super.cancel() will force-destroy the Python process
        List<ProcessHandle> children = new ArrayList<>();
        if (process != null) {
            try {
                ProcessHandle.of(process.pid()).ifPresent(ph ->
                        ph.descendants().forEach(children::add));
            } catch (Exception e) {
                logger.warn("Failed to collect DataX child processes, Task: {}, {}, {}",
                        taskId, taskName, taskInstanceId, e);
            }
        }
        try {
            return super.cancel();
        } finally {
            children.forEach(child -> {
                logger.info("Killing DataX child process: pid={}, Task: {}, {}, {}",
                        child.pid(), taskId, taskName, taskInstanceId);
                child.destroyForcibly();
            });
        }
    }

    /**
     * Determines whether the input is inline JSON content (as opposed to a file path).
     * Simple heuristic: returns true if the trimmed string starts with '{'.
     */
    private boolean isJsonContent(String s) {
        return s.startsWith("{");
    }

    private void resolvePythonCommand() {
        String cmd = environmentVariables.get("PYTHON_COMMAND");
        if (cmd != null && !cmd.trim().isEmpty()) {
            pythonCommand = cmd.trim();
            return;
        }
        pythonCommand = isWindows() ? "python" : "python3";
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}