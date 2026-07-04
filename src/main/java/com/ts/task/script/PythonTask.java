/*
 * Description: Task type executing Python scripts using the system interpreter
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.task.script;

import com.ts.exception.TaskExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
/**
 * Task type that executes a Python script using the system Python interpreter.
 *
 * <p>Detects the available interpreter by probing for {@code python3} then
 * {@code python}. The script content is written to a {@code .py} temporary file
 * and executed with any {@code env.*} parameters passed as environment variables.
 *
 * @see ScriptTask
 */
public class PythonTask extends ScriptTask {
    private static final Logger logger = LogManager.getLogger(PythonTask.class);
    private static final String PYTHON_CMD_WINDOWS = "python";
    private static final String PYTHON_CMD_UNIX = "python3";
    private String pythonCommand;

    @Override
    protected void createScriptFile() {
        // Create temporary directory path
        Path tempDirPath = Paths.get(workingDir);

        // Ensure temporary directory exists and is accessible
        if (!Files.exists(tempDirPath) || !Files.isDirectory(tempDirPath) || !Files.isWritable(tempDirPath)) {
            throw new TaskExecutionException("Temporary directory is not accessible: " + workingDir, null, taskId, taskInstanceId);
        }

        // Determine the appropriate Python command
        determinePythonCommand();

        try {
            // Create temporary Python script file with appropriate extension
            Path tempFile = Files.createTempFile(tempDirPath, "workflow_", ".py");
            scriptFile = tempFile.toFile();

            // Prepare script content based on OS
            String scriptHeader;
            if (isWindows()) {
                // Windows-specific script header
                scriptHeader = "# -*- coding: utf-8 -*-\n\n" +
                        "import sys\n" +
                        "import traceback\n\n";
            } else {
                // Unix-specific script header with shebang
                scriptHeader = "#!/usr/bin/env " + pythonCommand + "\n" +
                        "# -*- coding: utf-8 -*-\n\n" +
                        "import sys\n" +
                        "import traceback\n\n";
            }

            String enhancedScript = scriptHeader +
                    "try:\n" +
                    "    # Begin user script\n" +
                    indentPythonCode(scriptContent) + "\n\n" +
                    "    # End user script\n" +
                    "except Exception as e:\n" +
                    "    print(f\"Error in Python script: {str(e)}\")\n" +
                    "    traceback.print_exc()\n" +
                    "    sys.exit(1)\n";

            // Write the script content
            Files.write(tempFile,
                    enhancedScript.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            // Ensure file is executable (important for Unix-like systems)
            scriptFile.setExecutable(true);

            logger.debug("Created Python script file: {}", scriptFile.getAbsolutePath());
        } catch (IOException e) {
            throw new TaskExecutionException("Failed to create Python script file: " + e.getMessage(), e, taskId, taskInstanceId);
        }
    }

    @Override
    protected List<String> buildCommand() {
        List<String> command = new ArrayList<>();

        if (isWindows()) {
            // For Windows, use python directly
            command.add(pythonCommand);
        } else {
            // For Unix-like systems, we could rely on the shebang, but explicitly specifying
            // the interpreter provides better control and consistency
            command.add(pythonCommand);
        }

        // Add script file path
        command.add(scriptFile.getAbsolutePath());

        logger.debug("Python command: {}", command);
        return command;
    }

    /**
     * Validate working directory
     * Handles special cases for long paths on Windows
     */
    @Override
    protected void validateWorkingDirectory(String directory) throws TaskExecutionException {
        super.validateWorkingDirectory(directory);

        // Check for Windows long path issues
        if (isWindows() && directory.length() > 260) {
            try {
                // Convert to canonical path to resolve any relative components
                File dir = new File(directory);
                String canonicalPath = dir.getCanonicalPath();

                // If path is still too long, try using the long path format
                if (canonicalPath.length() > 260 && !canonicalPath.startsWith("\\\\?\\")) {
                    // Create a long path format reference
                    String longPath = "\\\\?\\" + canonicalPath;
                    File longPathDir = new File(longPath);

                    // Verify the long path is accessible
                    if (longPathDir.exists() && longPathDir.isDirectory()) {
                        logger.info("Using long path format for working directory: {}", longPath);
                        // Update the working directory to use long path format
                        workingDir = longPath;
                        return;
                    }
                }

                logger.warn("Working directory path exceeds Windows path length limit and cannot be resolved using long path format.");
            } catch (Exception e) {
                logger.warn("Error while attempting to resolve long path name", e);
            }
        }
    }

    /**
     * Check if running on Windows
     *
     * @return true if OS is Windows
     */
    private boolean isWindows() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win");
    }

    /**
     * Determine the appropriate Python command based on environment and configuration
     */
    private void determinePythonCommand() {
        // First check if a specific python command is specified in environment variables
        String configuredPython = environmentVariables.get("PYTHON_COMMAND");

        if (configuredPython != null && !configuredPython.trim().isEmpty()) {
            pythonCommand = configuredPython.trim();
            logger.debug("Using configured Python command: {}", pythonCommand);
            return;
        }

        // Otherwise, use default based on OS
        if (isWindows()) {
            pythonCommand = PYTHON_CMD_WINDOWS;
        } else {
            pythonCommand = PYTHON_CMD_UNIX;
        }

        logger.debug("Using default Python command for {} environment: {}",
                isWindows() ? "Windows" : "Unix-like", pythonCommand);
    }

    /**
     * Indent the user's Python code to fit within the try block
     *
     * @param code The user's Python code
     * @return The indented code
     */
    private String indentPythonCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return "";
        }

        String[] lines = code.split("\r?\n");
        StringBuilder indented = new StringBuilder();

        for (String line : lines) {
            // Only add indentation to non-empty lines to preserve Python's whitespace significance
            if (!line.trim().isEmpty()) {
                indented.append("    ").append(line).append("\n");
            } else {
                indented.append("\n");
            }
        }

        return indented.toString();
    }
}