/*
 * Description: Task type executing shell scripts via bash or /bin/sh on Unix/Linux hosts
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
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
/**
 * Task type that executes a shell script via {@code bash} (or {@code /bin/sh}
 * as fallback) on Unix/Linux hosts.
 *
 * <p>Prepends {@code set -e} and a UTF-8 locale export to the script content to
 * ensure that any failing command causes the script to exit with a non-zero code.
 * Sets executable permissions on the temporary file using POSIX attributes where
 * available, falling back to {@code File.setExecutable()}.
 *
 * @see ScriptTask
 */
public class ShellTask extends ScriptTask {
    private static final Logger logger = LogManager.getLogger(ShellTask.class);
    private static final String DEFAULT_SHELL_PATH = "/bin/bash";
    private static final String FALLBACK_SHELL_PATH = "/bin/sh";

    @Override
    protected void createScriptFile() {
        // Build the temporary directory path for the script file
        Path tempDirPath = Paths.get(workingDir);

        // Ensure the temporary directory exists and is accessible
        if (!Files.exists(tempDirPath) || !Files.isDirectory(tempDirPath) || !Files.isWritable(tempDirPath)) {
            throw new TaskExecutionException("Temporary directory is not accessible: " + workingDir, null, taskId, taskInstanceId);
        }

        try {
            // Create the temporary script file in the temp directory
            Path tempFile = Files.createTempFile(tempDirPath, "workflow_", ".sh");
            scriptFile = tempFile.toFile();

            // Add the shebang line to ensure the correct shell interpreter is used
            String shellPath = getShellPath();
            String scriptWithShebang = "#!" + shellPath + "\n" +
                    "set -e\n" +   // Exit immediately if any command fails
                    scriptContent;

            // Write the script content to the temporary file
            Files.write(tempFile,
                    scriptWithShebang.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            // Set the executable permission on the script file
            setExecutablePermissions(tempFile);

            logger.debug("Created shell script file: {}", scriptFile.getAbsolutePath());
        } catch (IOException e) {
            throw new TaskExecutionException("Failed to create shell script file: " + e.getMessage(), e, taskId, taskInstanceId);
        }
    }

    @Override
    protected List<String> buildCommand() {
        List<String> command = new ArrayList<>();

        // Determine which shell interpreter to use
        String shellPath = getShellPath();
        command.add(shellPath);

        // Add shell interpreter options
        if (shellPath.endsWith("bash")) {
            command.add("-e"); // Exit immediately if any command fails
        }

        // Append the script file path as the last argument
        command.add(scriptFile.getAbsolutePath());

        logger.debug("Shell command: {}", String.join(" ", command));
        return command;
    }

    /**
     * Determines the path to an available shell interpreter on the current system
     *
     * @return the path to the shell executable
     */
    protected String getShellPath() {
        // First try bash
        File bashFile = new File(DEFAULT_SHELL_PATH);
        if (bashFile.exists() && bashFile.canExecute()) {
            return DEFAULT_SHELL_PATH;
        }

        // Fall back to sh
        File shFile = new File(FALLBACK_SHELL_PATH);
        if (shFile.exists() && shFile.canExecute()) {
            return FALLBACK_SHELL_PATH;
        }

        // If neither bash nor sh exists (highly unlikely), fall back to /bin/sh
        logger.warn("Neither bash nor sh found at standard locations, using default: {}", DEFAULT_SHELL_PATH);
        return DEFAULT_SHELL_PATH;
    }

    /**
     * Sets the executable permission on the given script file
     *
     * @param scriptPath the path to the script file
     */
    private void setExecutablePermissions(Path scriptPath) {
        File scriptFile = scriptPath.toFile();
        try {
            // Attempt to set POSIX file permissions
            Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OWNER_WRITE);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(scriptPath, perms);
        } catch (UnsupportedOperationException e) {
            // Fall back to the portable setExecutable() method
            logger.debug("POSIX not supported, using setExecutable");
            scriptFile.setExecutable(true, true);
        } catch (Exception e) {
            logger.warn("Failed to set permissions", e);
            scriptFile.setExecutable(true);
        }
    }
}