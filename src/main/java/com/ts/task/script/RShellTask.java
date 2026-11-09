/*
 * Description: Task implementation for executing shell scripts on remote hosts via SSH.
 *              Extends ShellTask to reuse local script file generation, while overriding
 *              command building and I/O handling to stream the script content over SSH.
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.task.script;

import com.ts.common.constants.Constants;
import com.ts.common.utils.ParamUtils;
import com.ts.exception.TaskExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Task that executes a shell script on a remote host via SSH.
 * <p>
 * Overrides {@link #createScriptFile()} as a no-op so no temporary file is written
 * to local disk. Instead, {@link #writeScriptToProcess(Process)} streams the script
 * content directly from memory to the remote shell's standard input, which is both
 * more efficient and avoids sensitive script content being persisted on the local host.
 * </p>
 * <p>
 * Required parameters (defined in {@link Constants}):
 * <ul>
 *   <li>{@link Constants#PARAM_SSH_HOST} - remote hostname or IP address</li>
 *   <li>{@link Constants#PARAM_SSH_USER} - remote login username</li>
 *   <li>{@link Constants#PARAM_SSH_PORT} - (optional) SSH port, defaults to 22</li>
 *   <li>{@link Constants#PARAM_SSH_PASSWORD} - (optional) password authentication (requires sshpass)</li>
 *   <li>{@link Constants#PARAM_SSH_KEYFILE} - (optional) private key file path</li>
 *   <li>{@link Constants#PARAM_SSH_KNOWN_HOSTS} - (optional) known_hosts file path</li>
 *   <li>{@link Constants#PARAM_SSH_REMOTE_WORKINGDIR} - (optional) absolute path to set as working directory on the remote host</li>
 * </ul>
 * Authentication precedence: if password is provided, {@code sshpass} is used with environment variable;
 * otherwise key-based authentication is attempted using the provided key file.
 * </p>
 *
 * @see ShellTask
 * @see ScriptTask
 */
public class RShellTask extends ShellTask {
    private static final Logger logger = LogManager.getLogger(RShellTask.class);
    private static final int DEFAULT_SSH_PORT = 22;

    // Remote connection fields populated from parameters
    private String remoteHost;
    private int remotePort;
    private String remoteUser;
    private String password;
    private String keyFile;
    private String knownHostsFile;
    private String remoteWorkDir;

    /**
     * Extracts remote connection parameters from the task input map.
     * No local script file is created; script content is streamed from memory in
     * {@link #writeScriptToProcess(Process)}.
     *
     * @param parameters task input parameters; must contain {@link Constants#PARAM_SSH_HOST}
     *                   and {@link Constants#PARAM_SSH_USER}
     * @throws Exception if required parameters are missing or validation fails
     */
    @Override
    public void preAction(Map<String, Object> parameters) throws Exception {
        // Extract remote connection parameters using ParamUtils
        remoteHost = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_SSH_HOST,
                taskId, taskInstanceId);
        remoteUser = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_SSH_USER,
                taskId, taskInstanceId);

        remotePort = ParamUtils.getIntParameter(parameters, Constants.PARAM_SSH_PORT, DEFAULT_SSH_PORT);
        if (remotePort < 1 || remotePort > 65535) {
            throw new TaskExecutionException(
                    "Invalid SSH port: " + remotePort + ". Must be between 1 and 65535",
                    null, taskId, taskInstanceId);
        }

        password = ParamUtils.getParameter(parameters, Constants.PARAM_SSH_PASSWORD, null);
        keyFile = ParamUtils.getParameter(parameters, Constants.PARAM_SSH_KEYFILE, null);
        knownHostsFile = ParamUtils.getParameter(parameters, Constants.PARAM_SSH_KNOWN_HOSTS, null);
        remoteWorkDir = ParamUtils.getParameter(parameters, Constants.PARAM_SSH_REMOTE_WORKINGDIR, null);

        // Validate at least one authentication method is configured
        if ((password == null || password.isEmpty()) && (keyFile == null || keyFile.isEmpty())) {
            throw new TaskExecutionException(
                    "Either SSH password or private key file must be provided",
                    null, taskId, taskInstanceId);
        }

        // If key file is specified, verify that it exists and is readable (fail early)
        if (keyFile != null && !keyFile.isEmpty()) {
            File f = new File(keyFile);
            if (!f.exists() || !f.canRead()) {
                throw new TaskExecutionException(
                        "SSH key file not found or not readable: " + keyFile,
                        null, taskId, taskInstanceId);
            }
        }

        // If known_hosts file is specified, verify that it exists and is readable (fail early)
        if (knownHostsFile != null && !knownHostsFile.isEmpty()) {
            File khf = new File(knownHostsFile);
            if (!khf.exists() || !khf.canRead()) {
                throw new TaskExecutionException(
                        "SSH known_hosts file not found or not readable: " + knownHostsFile,
                        null, taskId, taskInstanceId);
            }
        }

        // validate remoteWorkDir
        if (remoteWorkDir != null && !remoteWorkDir.isEmpty()) {
            if (!remoteWorkDir.startsWith("/")) {
                throw new TaskExecutionException(
                        "SSH remote working directory must be an absolute path: " + remoteWorkDir,
                        null, taskId, taskInstanceId);
            }
        }

        // Invoke parent preAction (from ShellTask -> ScriptTask) to read scriptContent,
        // workingDir and collect any "env.*" parameters into environmentVariables.
        super.preAction(parameters);

        // Inject SSHPASS after super.preAction() to guarantee it is not overwritten
        // by processEnvironmentVariables() inside the parent call.
        if (password != null && !password.isEmpty()) {
            // environmentVariables is a protected field inherited from ScriptTask
            environmentVariables.put("SSHPASS", password);
        }
        logger.info("RShellTask preAction completed: host={}:{}, user={}, remoteWorkDir={}, Task: {}, {}, {}",
                remoteHost, remotePort, remoteUser,
                remoteWorkDir != null ? remoteWorkDir : "(default)",
                taskId, taskName, taskInstanceId);
    }

    /**
     * No-op: RShellTask streams script content from memory via stdin and does not
     * require a temporary script file on the local host.
     */
    @Override
    protected void createScriptFile() {
        // RShellTask streams script via stdin; no local file needed
        logger.debug("Skipping local script file creation for remote SSH task: {}, {}, {}",
                taskId, taskName, taskInstanceId);
    }

    /**
     * Builds the SSH command line instead of the local shell command.
     *
     * @return list of command tokens for {@link ProcessBuilder}
     */
    @Override
    protected List<String> buildCommand() {
        List<String> cmd = new ArrayList<>();

        // Use sshpass with -e flag to read password from SSHPASS environment variable.
        // The variable is set in preAction and will be injected into the process by the parent.
        if (password != null && !password.isEmpty()) {
            cmd.add("sshpass");
            cmd.add("-e");   // Read password from environment variable SSHPASS
        }

        cmd.add("ssh");

        // Send a keep-alive packet every 60 seconds to keep the connection alive
        cmd.add("-o");
        cmd.add("ServerAliveInterval=60");
        // Maximum number of consecutive keep-alive failures before disconnecting
        cmd.add("-o");
        cmd.add("ServerAliveCountMax=3");

        cmd.add("-o");
        // Strict host key checking: enabled only if a known_hosts file is explicitly provided
        if (knownHostsFile != null && !knownHostsFile.isEmpty()) {
            cmd.add("StrictHostKeyChecking=yes");
        } else {
            cmd.add("StrictHostKeyChecking=no");
            logger.warn("SSH host key checking disabled for task: {}, {}. " +
                            "Consider providing knownHostsFile for production use.",
                    taskId, taskInstanceId);
        }

        if (knownHostsFile != null && !knownHostsFile.isEmpty()) {
            cmd.add("-o");
            cmd.add("UserKnownHostsFile=" + knownHostsFile);
        } else {
            cmd.add("-o");
            cmd.add("UserKnownHostsFile=/dev/null");
        }

        // Key-based authentication (only if password is not used)
        if ((password == null || password.isEmpty()) && keyFile != null && !keyFile.isEmpty()) {
            cmd.add("-i");
            cmd.add(keyFile);
            cmd.add("-o");
            cmd.add("IdentitiesOnly=yes");
        }

        cmd.add("-p");
        cmd.add(String.valueOf(remotePort));
        cmd.add(remoteUser + "@" + remoteHost);
        cmd.add("bash");
        cmd.add("-s");  // Read script from stdin

        if (logger.isDebugEnabled()) {
            List<String> logCmd = new ArrayList<>(cmd);
            // Password is no longer on the command line; environment variable is used.
            logger.debug("Built SSH command: {}", String.join(" ", logCmd));
        }
        return cmd;
    }

    /**
     * Hook method called by {@link ScriptTask#action} after process start.
     * Streams the script content directly from memory to the remote SSH process's stdin,
     * prepending the same shebang and {@code set -e} preamble that {@link ShellTask}
     * would have written to the local script file.
     *
     * @param process the started {@link Process} instance
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void writeScriptToProcess(Process process) throws IOException {
        // Build the full script content with shebang and set -e, mirroring ShellTask.createScriptFile()
        String shellPath = getShellPath();
        StringBuilder sb = new StringBuilder();
        sb.append("#!").append(shellPath).append("\n");
        sb.append("set -e\n");
        if (remoteWorkDir != null && !remoteWorkDir.isEmpty()) {
            String safePath = remoteWorkDir.replace("'", "'\\''");
            sb.append("cd '").append(safePath).append("'\n");
        }
        sb.append(scriptContent);

        try (OutputStream os = process.getOutputStream()) {
            byte[] scriptBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            os.write(scriptBytes);
            os.flush();
            logger.debug("Script content streamed to remote SSH process stdin ({} bytes): {}, {}, {}",
                    scriptBytes.length, taskId, taskName, taskInstanceId);
        }
    }
}
