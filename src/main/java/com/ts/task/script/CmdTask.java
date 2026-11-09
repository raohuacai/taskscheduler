/*
 * Description: Task type executing Windows batch scripts via cmd.exe
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
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
/**
 * Task type that executes a Windows {@code .cmd} / {@code .bat} script via
 * {@code cmd.exe /c}.
 *
 * <p>Prepends {@code @echo off}, code-page configuration ({@code chcp 65001}),
 * and error-handling directives to the script body. Detects the system default
 * charset rather than assuming UTF-8, for compatibility with older Windows hosts.
 *
 * @see ScriptTask
 */
public class CmdTask extends ScriptTask {
    private static final Logger logger = LogManager.getLogger(CmdTask.class);

    @Override
    protected void createScriptFile()  {
        // Build the temporary directory path for the script file
        Path tempDirPath = Paths.get(workingDir);

        // Ensure the temporary directory exists and is accessible
        if (!Files.exists(tempDirPath) || !Files.isDirectory(tempDirPath) || !Files.isWritable(tempDirPath)) {
            throw new TaskExecutionException("Temporary directory is not accessible: " + workingDir, null, taskId, taskInstanceId);
        }

        try {
            // Create the temporary script file in the temp directory
            Path tempFile = Files.createTempFile(tempDirPath, "workflow_", ".bat");
            scriptFile = tempFile.toFile();

            // Prepend error-handling and encoding setup directives to the script
            // More robust batch script template
            String enhancedScript =
                    "@echo off\r\n" +
                            "setlocal EnableDelayedExpansion\r\n" +
                            "set \"WORKFLOW_ORIGINAL_ERRORLEVEL=%ERRORLEVEL%\"\r\n" +
                            "chcp 65001 >nul 2>&1 || (\r\n" +
                            "    echo Warning: UTF-8 codepage not supported. Using system default.\r\n" +
                            ")\r\n\r\n" +
                            ":: Begin user script\r\n" +
                            scriptContent + "\r\n\r\n" +
                            ":: End user script\r\n" +
                            "set \"WORKFLOW_SCRIPT_ERRORLEVEL=%ERRORLEVEL%\"\r\n" +
                            "endlocal & exit /b %WORKFLOW_SCRIPT_ERRORLEVEL%\r\n";

            // Write the script content to the temporary file
            Files.write(tempFile,
                    enhancedScript.getBytes(Charset.defaultCharset()),  // Use default charset instead of StandardCharsets.UTF_8 for compatibility
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            // Set the executable permission on the script file
            scriptFile.setExecutable(true);

            logger.debug("Created CMD script file: {}", scriptFile.getAbsolutePath());
        } catch (IOException e) {
            throw new TaskExecutionException("Failed to create CMD script file: " + e.getMessage(), e, taskId, taskInstanceId);
        }
    }

    @Override
    protected List<String> buildCommand() {
        List<String> command = new ArrayList<>();
        command.add("cmd.exe");
        command.add("/c");
        //command.add("/E:ON");
        //command.add("/V:ON");
        // Avoid manually adding quotes - let ProcessBuilder handle escaping
        command.add(scriptFile.getAbsolutePath());

        logger.debug("CMD command: {}", command);
        return command;
    }

    /**
     * Validates that the specified working directory exists and is accessible
     * Performs Windows-specific path and command handling
     */
    @Override
    protected void validateWorkingDirectory(String directory) throws TaskExecutionException {
        super.validateWorkingDirectory(directory);

        if (directory.length() > 260) {
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
}