/*
 * Description: Universal file transfer task supporting copy/move/sync operations between
 *              local file systems, FTP, and SFTP with wildcard filtering, checksum,
 *              and source deletion after copy.
 * Author: Rock Rao
 * Date: 2026/04/20
 * Version: 2.2
 */
package com.ts.task.file;

import com.ts.common.constants.Constants;
import com.ts.common.utils.ParamUtils;
import com.ts.exception.TaskExecutionException;
import com.ts.service.impl.TaskLogService;
import com.ts.task.timeout.TimeoutTask;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.vfs2.*;
import org.apache.commons.vfs2.auth.StaticUserAuthenticator;
import org.apache.commons.vfs2.filter.*;
import org.apache.commons.vfs2.impl.DefaultFileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.ftp.FtpFileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Universal file transfer task supporting copy/move/sync operations between
 * local file systems, FTP, and SFTP.
 * <p>
 * Built on Apache Commons VFS2 for uniform resource access. Implements
 * wildcard filtering, checksum verification, and optional source deletion
 * after successful copy/sync. Inherits hard timeout control from {@link TimeoutTask}.
 * <p>
 * ── preAction ────────────────────────────────────────────────────────────────
 * 1. Loads and validates all required parameters (sourceUri, targetUri, etc.).
 * 2. Initializes VFS FileSystemManager and resolves source/target FileObjects.
 * 3. Validates source existence and target parent directory writability.
 * <p>
 * ── action ───────────────────────────────────────────────────────────────────
 * 1. For folder sources, uses manual recursion with {@link #matchesPatterns} to
 * avoid directory name filtering and ensure consistency across all operations.
 * 2. Executes the transfer operation (copy/move/sync).
 * 3. Preserves last-modified timestamps if {@code preserveTimestamp} is true.
 * 4. Verifies checksum if {@code verifyChecksum} is true and files were transferred.
 * 5. For copy/sync operations, optionally deletes source files if
 * {@code deleteSourceAfterCopy} is true and the operation succeeded.
 * 6. Catches {@link InterruptedException} and rethrows it directly for parent
 * class to handle timeout vs cancel distinction.
 * <p>
 * ── cleanupResources ─────────────────────────────────────────────────────────
 * Closes all VFS FileObjects to release network/file handles. Idempotent.
 * Calls {@link Thread#interrupted()} to clear interrupt flag before returning.
 * <p>
 * Supported URI schemes:
 * <ul>
 *   <li>{@code file:///path} – Local file system</li>
 *   <li>{@code ftp://[user[:pass]@]host[:port]/path} – FTP</li>
 *   <li>{@code sftp://[user@]host[:port]/path} – SFTP</li>
 * </ul>
 * <p>
 * Author: Rock Rao<br>
 * Date: 2026/04/20<br>
 * Version: 2.2
 */
public class FileXTask extends TimeoutTask {
    private static final Logger logger = LogManager.getLogger(FileXTask.class);

    // --- Task parameters (camelCase as per convention) ---
    private String sourceUri;
    private String targetUri;
    private String operation;               // "copy" / "move" / "sync"
    private boolean recursive;
    private boolean overwrite;
    private boolean preserveTimestamp;
    private boolean deleteSourceAfterCopy;  // delete source files after successful copy/sync
    private List<String> includePatterns;   // null or empty means no include filter
    private List<String> excludePatterns;   // null or empty means no exclude filter
    private String checksumAlgorithm;       // "MD5", "SHA-1", "SHA-256"
    private boolean verifyChecksum;
    private int connectionTimeoutSecs;
    private int dataTimeoutSecs;
    private String username;
    private String password;
    private String privateKeyPath;
    private String privateKeyPassphrase;
    private boolean passiveMode;

    // VFS components
    private FileSystemManager fsManager;
    private FileObject sourceFileObj;
    private FileObject targetFileObj;

    // Track resource allocation for idempotent cleanup
    private volatile boolean sourceResolved = false;
    private volatile boolean targetResolved = false;

    // Transfer statistics
    private final AtomicLong transferredBytes = new AtomicLong(0);
    private final AtomicInteger transferredFiles = new AtomicInteger(0);

    // -------------------------------------------------------------------------
    // preAction
    // -------------------------------------------------------------------------

    /**
     * Prepares resources and reads task parameters before the main action.
     * Called by {@link TimeoutTask#execute} before {@link #action}.
     *
     * @param parameters merged task parameter map supplied by the workflow engine
     * @throws Exception if a required parameter is missing or resource initialization fails
     */
    @Override
    public void preAction(Map<String, Object> parameters) throws Exception {
        super.preAction(parameters);

        // 1. Load parameters
        loadParameters(parameters);

        // 2. Initialize VFS manager
        fsManager = VFS.getManager();

        // 3. Resolve file objects
        FileSystemOptions srcOpts = buildFileSystemOptions(sourceUri);
        FileSystemOptions dstOpts = buildFileSystemOptions(targetUri);
        sourceFileObj = fsManager.resolveFile(sourceUri, srcOpts);
        sourceResolved = true;
        targetFileObj = fsManager.resolveFile(targetUri, dstOpts);
        targetResolved = true;

        // 4. Validate access
        validateAccess();

        logger.debug("FileXTask preAction completed. Task: {}, {}, {}",
                taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                "FileXTask preAction completed");
    }

    // -------------------------------------------------------------------------
    // action
    // -------------------------------------------------------------------------

    /**
     * Executes the core file transfer logic.
     * Called by {@link TimeoutTask#execute} after {@link #preAction}.
     *
     * @param parameters merged task parameter map
     * @throws Exception if the task logic fails
     */
    @Override
    public void action(Map<String, Object> parameters) throws Exception {
        logger.info("Starting {} operation: {} -> {}, Recursive: {}, Overwrite: {}",
                operation, sourceUri, targetUri, recursive, overwrite);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                String.format("Starting %s: %s -> %s", operation, sourceUri, targetUri));

        long startTime = System.currentTimeMillis();
        boolean operationSucceeded = false;

        try {
            if ("move".equals(operation)) {
                // Cache source stats before move (source will be gone after moveTo)
                Stats srcStats = computeSourceStats();

                // Warn if pattern filters are set with directory move: moveTo moves the
                // entire directory atomically; include/exclude patterns cannot filter
                // individual files during a directory move.
                if (sourceFileObj.getType() == FileType.FOLDER
                        && ((includePatterns != null && !includePatterns.isEmpty())
                        || (excludePatterns != null && !excludePatterns.isEmpty()))) {
                    logger.warn("Pattern filters (include/exclude) are ignored for directory move: " +
                                    "moveTo moves the entire source directory atomically. " +
                                    "All files under '{}' will be moved regardless of patterns. " +
                                    "Use copy+deleteSourceAfterCopy if pattern filtering is required.",
                            sourceUri);
                    TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                            "WARNING: Pattern filters ignored for directory move. " +
                                    "All files under source will be moved.");
                }

                sourceFileObj.moveTo(targetFileObj);
                logger.info("File move completed: {} -> {}", sourceUri, targetUri);
                transferredBytes.addAndGet(srcStats.bytes);
                transferredFiles.addAndGet(srcStats.files);
                operationSucceeded = true;

            } else if ("sync".equals(operation)) {
                if (sourceFileObj.getType() == FileType.FOLDER) {
                    transferDirectory(sourceFileObj, targetFileObj, true);
                    operationSucceeded = true;
                } else {
                    // Single-file sync
                    if (matchesPatterns(sourceFileObj)) {
                        long srcSize = shouldSyncFile(sourceFileObj, targetFileObj);
                        if (srcSize >= 0) {
                            targetFileObj.copyFrom(sourceFileObj, Selectors.SELECT_SELF);
                            transferredFiles.incrementAndGet();
                            transferredBytes.addAndGet(srcSize);
                            operationSucceeded = true;
                        }
                    }
                }

                if (operationSucceeded) {
                    // Preserve timestamps before deleting sources
                    if (preserveTimestamp) {
                        preserveTimestamps(sourceFileObj, targetFileObj);
                    }
                    // Verify checksum before deleting sources
                    if (verifyChecksum && checksumAlgorithm != null) {
                        logger.info("Verifying checksum using algorithm: {}", checksumAlgorithm);
                        if (!verifyTransfer()) {
                            throw new TaskExecutionException("Checksum verification failed for " +
                                    sourceUri + " -> " + targetUri, null, taskId, taskInstanceId);
                        }
                        logger.info("Checksum verification passed.");
                    }
                    // Delete source files after successful sync
                    if (deleteSourceAfterCopy) {
                        deleteSourceFiles();
                    }
                }

            } else {
                // Default: copy
                if (sourceFileObj.getType() == FileType.FOLDER) {
                    transferDirectory(sourceFileObj, targetFileObj, false);
                    operationSucceeded = true;
                } else {
                    // Single-file copy
                    if (!overwrite && targetFileObj.exists()) {
                        logger.info("Skipping existing target (overwrite=false): {}", targetUri);
                    } else {
                        targetFileObj.copyFrom(sourceFileObj, Selectors.SELECT_SELF);
                        transferredFiles.incrementAndGet();
                        transferredBytes.addAndGet(sourceFileObj.getContent().getSize());
                        operationSucceeded = true;
                    }
                }

                if (operationSucceeded) {
                    // Preserve timestamps before deleting sources
                    if (preserveTimestamp) {
                        preserveTimestamps(sourceFileObj, targetFileObj);
                    }
                    // Verify checksum BEFORE deleting sources
                    if (verifyChecksum && checksumAlgorithm != null) {
                        logger.info("Verifying checksum using algorithm: {}", checksumAlgorithm);
                        if (!verifyTransfer()) {
                            throw new TaskExecutionException("Checksum verification failed for " +
                                    sourceUri + " -> " + targetUri, null, taskId, taskInstanceId);
                        }
                        logger.info("Checksum verification passed.");
                    }
                    // Delete source files after successful copy
                    if (deleteSourceAfterCopy) {
                        deleteSourceFiles();
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            logger.info("{} operation completed. Files: {}, Bytes: {}, Duration: {} ms",
                    operation, transferredFiles.get(), transferredBytes.get(), duration);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                    String.format("%s completed: %d files, %d bytes, %d ms",
                            operation, transferredFiles.get(), transferredBytes.get(), duration));

        } catch (InterruptedException e) {
            throw e;
        } catch (FileSystemException e) {
            String msg = "File transfer failed: " + e.getMessage();
            logger.error("{}, Task: {}, {}, {}", msg, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, msg);
            throw new TaskExecutionException(msg, e, taskId, taskInstanceId);
        }
    }

    // -------------------------------------------------------------------------
    // cleanupResources
    // -------------------------------------------------------------------------

    @Override
    public void cleanupResources() {
        if (targetResolved) {
            closeQuietly(targetFileObj);
            targetResolved = false;
        }
        if (sourceResolved) {
            closeQuietly(sourceFileObj);
            sourceResolved = false;
        }
        Thread.interrupted();
        super.cleanupResources();
    }

    // =========================================================================
    // Private helper methods
    // =========================================================================

    /**
     * Loads and validates all task parameters from the merged parameter map.
     */
    private void loadParameters(Map<String, Object> parameters) throws TaskExecutionException {
        sourceUri = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_SOURCE_URI, taskId, taskInstanceId);
        targetUri = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TARGET_URI, taskId, taskInstanceId);

        operation = ParamUtils.getParameter(parameters, Constants.PARAM_OPERATION, "copy").toLowerCase();
        recursive = ParamUtils.getBooleanParameter(parameters, Constants.PARAM_RECURSIVE, true);
        overwrite = ParamUtils.getBooleanParameter(parameters, Constants.PARAM_OVERWRITE, true);
        preserveTimestamp = ParamUtils.getBooleanParameter(parameters, Constants.PARAM_PRESERVE_TIMESTAMP, false);
        deleteSourceAfterCopy = ParamUtils.getBooleanParameter(parameters, Constants.PARAM_DELETE_SOURCE_AFTER_COPY, false);

        String inc = ParamUtils.getParameter(parameters, Constants.PARAM_INCLUDE_PATTERNS, "");
        includePatterns = inc.isEmpty() ? null : Arrays.asList(inc.split("\\s*,\\s*"));
        String exc = ParamUtils.getParameter(parameters, Constants.PARAM_EXCLUDE_PATTERNS, "");
        excludePatterns = exc.isEmpty() ? null : Arrays.asList(exc.split("\\s*,\\s*"));

        checksumAlgorithm = ParamUtils.getParameter(parameters, Constants.PARAM_CHECKSUM_ALGORITHM, null);
        verifyChecksum = ParamUtils.getBooleanParameter(parameters, Constants.PARAM_VERIFY_CHECKSUM, false);

        connectionTimeoutSecs = ParamUtils.getIntParameter(parameters, Constants.PARAM_CONNECTION_TIMEOUT, 30);
        dataTimeoutSecs = ParamUtils.getIntParameter(parameters, Constants.PARAM_DATA_TIMEOUT, 300);

        username = ParamUtils.getParameter(parameters, Constants.PARAM_USERNAME, null);
        password = ParamUtils.getParameter(parameters, Constants.PARAM_PASSWORD, null);
        privateKeyPath = ParamUtils.getParameter(parameters, Constants.PARAM_PRIVATE_KEY_PATH, null);
        privateKeyPassphrase = ParamUtils.getParameter(parameters, Constants.PARAM_PRIVATE_KEY_PASSPHRASE, null);

        passiveMode = ParamUtils.getBooleanParameter(parameters, Constants.PARAM_PASSIVE_MODE, true);

        if (!Arrays.asList("copy", "move", "sync").contains(operation)) {
            throw new TaskExecutionException("Invalid operation: " + operation +
                    ". Supported: copy, move, sync", null, taskId, taskInstanceId);
        }

        if (verifyChecksum && (checksumAlgorithm == null || checksumAlgorithm.isEmpty())) {
            throw new TaskExecutionException(
                    "checksumAlgorithm is required when verifyChecksum=true",
                    null, taskId, taskInstanceId);
        }

        if (verifyChecksum && checksumAlgorithm != null) {
            try {
                MessageDigest.getInstance(checksumAlgorithm);
            } catch (Exception e) {
                throw new TaskExecutionException("Unsupported checksum algorithm: " +
                        checksumAlgorithm, e, taskId, taskInstanceId);
            }
        }

    }

    /**
     * Builds protocol-specific FileSystemOptions for a given URI.
     */
    private FileSystemOptions buildFileSystemOptions(String uri) throws FileSystemException {
        FileSystemOptions opts = new FileSystemOptions();
        String scheme = getScheme(uri);

        if (username != null && !username.isEmpty()) {
            DefaultFileSystemConfigBuilder.getInstance().setUserAuthenticator(
                    opts, new StaticUserAuthenticator(null, username, password));
        }

        if ("ftp".equals(scheme) || "ftps".equals(scheme)) {
            FtpFileSystemConfigBuilder ftp = FtpFileSystemConfigBuilder.getInstance();
            ftp.setPassiveMode(opts, passiveMode);
            // Note: In Commons VFS 2.9.0, these timeouts are in SECONDS
            ftp.setConnectTimeout(opts, connectionTimeoutSecs);
            ftp.setDataTimeout(opts, dataTimeoutSecs);
            ftp.setUserDirIsRoot(opts, false);
        }

        if ("sftp".equals(scheme)) {
            SftpFileSystemConfigBuilder sftp = SftpFileSystemConfigBuilder.getInstance();
            sftp.setStrictHostKeyChecking(opts, "no");
            sftp.setTimeout(opts, connectionTimeoutSecs); // SECONDS in VFS 2.9.0
            if (privateKeyPath != null && !privateKeyPath.isEmpty()) {
                File keyFile = new File(privateKeyPath);
                if (!keyFile.exists()) {
                    throw new FileSystemException("SFTP private key not found: " + privateKeyPath);
                }
                sftp.setIdentities(opts, new File[]{keyFile});
            }
            if (password != null && !password.isEmpty()) {
                sftp.setUserInfo(opts, new com.jcraft.jsch.UserInfo() {
                    @Override
                    public String getPassphrase() {
                        return privateKeyPassphrase;
                    }

                    @Override
                    public String getPassword() {
                        return password;
                    }

                    @Override
                    public boolean promptPassword(String message) {
                        return false;
                    }

                    @Override
                    public boolean promptPassphrase(String message) {
                        return false;
                    }

                    @Override
                    public boolean promptYesNo(String message) {
                        return true;
                    }

                    @Override
                    public void showMessage(String message) {
                        logger.debug(message);
                    }
                });
            }
        }

        return opts;
    }

    private String getScheme(String uri) {
        int idx = uri.indexOf(':');
        return idx > 0 ? uri.substring(0, idx) : "file";
    }

    /**
     * Validates source existence and target parent directory writability.
     */
    private void validateAccess() throws TaskExecutionException {
        try {
            if (!sourceFileObj.exists()) {
                throw new TaskExecutionException("Source does not exist: " + sourceUri,
                        null, taskId, taskInstanceId);
            }
            if (sourceFileObj.getType() == FileType.FOLDER) {
                sourceFileObj.getChildren();
            } else {
                try (InputStream is = sourceFileObj.getContent().getInputStream()) {
                    // just validate readability
                }
            }
        } catch (FileSystemException e) {
            throw new TaskExecutionException("Cannot access source: " + e.getMessage(),
                    e, taskId, taskInstanceId);
        } catch (IOException e) {
            throw new TaskExecutionException("Cannot access source: " + e.getMessage(),
                    e, taskId, taskInstanceId);
        }

        try {
            FileObject parent = targetFileObj.getParent();
            if (parent != null && !parent.exists()) {
                parent.createFolder();
                logger.debug("Created target parent directory: {}", parent.getName());
            }
        } catch (FileSystemException e) {
            throw new TaskExecutionException("Cannot prepare target directory: " + e.getMessage(),
                    e, taskId, taskInstanceId);
        }
    }

    /**
     * Computes the total number of files and bytes in the source (respecting filters).
     * Used for move operation statistics before the source is moved.
     */
    private Stats computeSourceStats() throws FileSystemException {
        Stats stats = new Stats();
        if (sourceFileObj.getType() == FileType.FOLDER) {
            accumulateStats(sourceFileObj, stats);
        } else {
            // Single-file move: the file is always moved regardless of patterns
            // (moveTo is atomic; no pattern filtering applies at this level).
            // Stats reflect the actual file being moved.
            stats.files = 1;
            stats.bytes = sourceFileObj.getContent().getSize();
        }
        return stats;
    }

    private void accumulateStats(FileObject dir, Stats stats) throws FileSystemException {
        for (FileObject child : dir.getChildren()) {
            if (child.getType() == FileType.FOLDER) {
                if (recursive) {
                    accumulateStats(child, stats);
                }
            } else {
                if (matchesPatterns(child)) {
                    stats.files++;
                    stats.bytes += child.getContent().getSize();
                }
            }
        }
    }

    private static class Stats {
        int files;
        long bytes;
    }

    /**
     * Matches a file's base name against the configured include/exclude patterns.
     * Uses {@link FilenameUtils#wildcardMatch} for consistent filtering across
     * manual traversal and VFS-native operations.
     */
    private boolean matchesPatterns(FileObject file) {
        String baseName = file.getName().getBaseName();

        if (includePatterns != null && !includePatterns.isEmpty()) {
            boolean matched = false;
            for (String pattern : includePatterns) {
                if (FilenameUtils.wildcardMatch(baseName, pattern)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }

        if (excludePatterns != null && !excludePatterns.isEmpty()) {
            for (String pattern : excludePatterns) {
                if (FilenameUtils.wildcardMatch(baseName, pattern)) {
                    return false;
                }
            }
        }

        return true;
    }

    private void transferDirectory(FileObject srcDir, FileObject dstDir, boolean syncMode)
            throws FileSystemException, InterruptedException {
        if (!dstDir.exists()) {
            dstDir.createFolder();
        }

        for (FileObject child : srcDir.getChildren()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Directory transfer interrupted");
            }

            FileObject targetChild = dstDir.resolveFile(child.getName().getBaseName());

            if (child.getType() == FileType.FOLDER) {
                if (recursive) {
                    transferDirectory(child, targetChild, syncMode);
                }
            } else {
                if (!matchesPatterns(child)) continue;

                if (syncMode) {
                    // sync: shouldSyncFile returns source file size if transfer needed, -1 if skip
                    long srcSize = shouldSyncFile(child, targetChild);
                    if (srcSize < 0) continue;
                    targetChild.copyFrom(child, Selectors.SELECT_SELF);
                    transferredFiles.incrementAndGet();
                    transferredBytes.addAndGet(srcSize);  // reuse, avoid second getSize()
                } else {
                    // copy: respect overwrite flag explicitly
                    if (!overwrite && targetChild.exists()) {
                        logger.debug("Skipping existing target (overwrite=false): {}",
                                targetChild.getName());
                        continue;
                    }
                    targetChild.copyFrom(child, Selectors.SELECT_SELF);
                    transferredFiles.incrementAndGet();
                    transferredBytes.addAndGet(child.getContent().getSize());
                }
                logger.debug("Transferred file: {}", child.getName());
            }
        }
    }

    /**
     * Determines whether a file should be synced to the target.
     * Returns the source file size if the file should be transferred,
     * or -1 if the file should be skipped. This avoids a redundant
     * getSize() call in the caller for statistics.
     *
     * @param src source file object
     * @param dst target file object
     * @return source file size in bytes if transfer is needed, -1 if skipped
     * @throws FileSystemException if file attributes cannot be read
     */
    private long shouldSyncFile(FileObject src, FileObject dst) throws FileSystemException {
        if (!dst.exists()) {
            return src.getContent().getSize();
        }
        if (!overwrite) {
            return -1;
        }
        long srcSize = src.getContent().getSize();
        long srcTime = src.getContent().getLastModifiedTime();
        long dstTime = dst.getContent().getLastModifiedTime();
        if (srcTime > dstTime || srcSize != dst.getContent().getSize()) {
            return srcSize;  // needs update, reuse srcSize for stats
        }
        return -1;
    }

    private void deleteSourceFiles() throws FileSystemException, InterruptedException {
        if (sourceFileObj.getType() == FileType.FOLDER) {
            deleteDirectoryContents(sourceFileObj);
        } else {
            if (sourceFileObj.exists()) {
                sourceFileObj.delete();
                logger.debug("Deleted source file: {}", sourceFileObj.getName());
            }
        }
    }

    private void deleteDirectoryContents(FileObject dir) throws FileSystemException, InterruptedException {
        for (FileObject child : dir.getChildren()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Source deletion interrupted");
            }
            if (child.getType() == FileType.FOLDER) {
                if (recursive) {
                    deleteDirectoryContents(child);
                }
            } else {
                if (matchesPatterns(child)) {
                    child.delete();
                    logger.debug("Deleted source file: {}", child.getName());
                }
            }
        }
    }

    private void preserveTimestamps(FileObject src, FileObject dst)
            throws FileSystemException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Timestamp preservation interrupted");
        }

        if (src.getType() != FileType.FOLDER) {
            setLastModified(src, dst);
            return;
        }

        for (FileObject child : src.getChildren()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Timestamp preservation interrupted");
            }
            FileObject targetChild = dst.resolveFile(child.getName().getBaseName());
            if (child.getType() == FileType.FOLDER && recursive) {
                preserveTimestamps(child, targetChild);
            } else if (child.getType() == FileType.FILE && matchesPatterns(child)) {
                setLastModified(child, targetChild);
            }
        }
    }

    private void setLastModified(FileObject src, FileObject dst) {
        try {
            long modTime = src.getContent().getLastModifiedTime();
            dst.getContent().setLastModifiedTime(modTime);
        } catch (FileSystemException e) {
            logger.debug("Could not preserve timestamp for {}: {}", dst.getName(), e.getMessage());
        }
    }

    /**
     * Verifies checksum of all transferred files against their source counterparts.
     * Uses manual traversal with {@link #matchesPatterns} for consistent filtering.
     */
    private boolean verifyTransfer() throws Exception {
        if (sourceFileObj.getType() == FileType.FOLDER) {
            return verifyDirectoryChecksum(sourceFileObj, targetFileObj);
        } else {
            return verifyFileChecksum(sourceFileObj, targetFileObj);
        }
    }

    private boolean verifyFileChecksum(FileObject src, FileObject dst) throws Exception {
        String srcChecksum = computeChecksum(src);
        String dstChecksum = computeChecksum(dst);
        boolean match = srcChecksum.equals(dstChecksum);
        if (!match) {
            logger.error("Checksum mismatch: src={} dst={}", srcChecksum, dstChecksum);
        }
        return match;
    }

    private boolean verifyDirectoryChecksum(FileObject srcDir, FileObject dstDir) throws Exception {
        for (FileObject child : srcDir.getChildren()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Checksum verification interrupted");
            }
            FileObject targetChild = dstDir.resolveFile(child.getName().getBaseName());
            if (child.getType() == FileType.FOLDER) {
                if (recursive && !verifyDirectoryChecksum(child, targetChild)) {
                    return false;
                }
            } else if (matchesPatterns(child)) {
                if (!verifyFileChecksum(child, targetChild)) {
                    return false;
                }
            }
        }
        return true;
    }

    private String computeChecksum(FileObject file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(checksumAlgorithm);
        try (InputStream is = file.getContent().getInputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                digest.update(buffer, 0, len);
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Checksum computation interrupted");
                }
            }
        }
        return bytesToHex(digest.digest());
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void closeQuietly(FileObject file) {
        if (file != null) {
            try {
                file.close();
            } catch (FileSystemException ignored) {
            }
        }
    }
}