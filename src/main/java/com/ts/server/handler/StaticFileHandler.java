/*
 * Description: HTTP handler serving static web assets from the classpath with CSP header support
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
/**
 * HTTP handler that serves static web assets (HTML, JS, CSS) from the classpath.
 *
 * <p>Resolves paths relative to the configured static resource root and sets
 * appropriate {@code Content-Type} headers. In development mode, CSP headers
 * permit inline scripts and styles to ease frontend debugging.
 */
public class StaticFileHandler implements HttpHandler {
    private static final Logger logger = LogManager.getLogger(StaticFileHandler.class);
    private final String basePath;
    private final Path basePathNormalized;
    private final Map<String, String> mimeTypes = new HashMap<>();
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB max file size
    private static final int BUFFER_SIZE = 8192;
    private static final long DEFAULT_CACHE_MAX_AGE = TimeUnit.DAYS.toSeconds(1); // 1 day cache by default
    private boolean developmentMode = true; // Development mode enabled by default; allows inline scripts and styles in CSP headers

    /**
     * Constructor with base directory path for static files
     * @param basePath The base directory path for static files
     */
    public StaticFileHandler(String basePath) {
        this.basePath = basePath;
        this.basePathNormalized = Paths.get(basePath).normalize().toAbsolutePath();

        // Initialize common MIME types
        mimeTypes.put("html", "text/html");
        mimeTypes.put("htm", "text/html");
        mimeTypes.put("css", "text/css");
        mimeTypes.put("js", "application/javascript");
        mimeTypes.put("json", "application/json");
        mimeTypes.put("png", "image/png");
        mimeTypes.put("jpg", "image/jpeg");
        mimeTypes.put("jpeg", "image/jpeg");
        mimeTypes.put("gif", "image/gif");
        mimeTypes.put("svg", "image/svg+xml");
        mimeTypes.put("ico", "image/x-icon");
        mimeTypes.put("txt", "text/plain");
        mimeTypes.put("pdf", "application/pdf");
        mimeTypes.put("xml", "application/xml");
        mimeTypes.put("zip", "application/zip");
        mimeTypes.put("woff", "font/woff");
        mimeTypes.put("woff2", "font/woff2");
        mimeTypes.put("ttf", "font/ttf");
        mimeTypes.put("eot", "application/vnd.ms-fontobject");
        mimeTypes.put("otf", "font/otf");
    }

    /**
     * Set development mode - affects CSP
     * @param developmentMode true to enable dev mode with relaxed CSP, false for production mode
     */
    public void setDevelopmentMode(boolean developmentMode) {
        this.developmentMode = developmentMode;
        logger.info("Development mode set to: {}", developmentMode);
    }

    @Override
    /**
     * Handles.
     *
     * @param exchange  HTTP exchange context
     * @throws IOException  if an error occurs
     */
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String requestMethod = exchange.getRequestMethod();

            // Only handle GET and HEAD requests for static files
            if (!requestMethod.equals("GET") && !requestMethod.equals("HEAD")) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            // Get the requested file path
            URI uri = exchange.getRequestURI();
            String path = uri.getPath();

            logger.info("{}", uri);

            // Validate the URI path
            if (!isValidPath(path)) {
                logger.warn("Invalid path characters detected: {}", path);
                sendError(exchange, 400, "Bad Request - Invalid Path");
                return;
            }

            // Remove the context path if needed
            if (path.startsWith("/static")) {
                path = path.substring(7); // Remove "/static"
            }

            // Serve index.html for the root path
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            // Fix path separators for Windows
            path = path.replace('/', File.separatorChar);

            // Normalize path to prevent directory traversal
            Path requestedPath = Paths.get(basePath, path).normalize().toAbsolutePath();

            // Security check: ensure the path is within the base directory
            if (!requestedPath.startsWith(basePathNormalized)) {
                logger.warn("Path traversal attack detected: {}", path);
                sendError(exchange, 403, "Forbidden - Access Denied");
                return;
            }

            File file = requestedPath.toFile();

            // Check if file exists and is a regular file
            if (!file.exists() || !file.isFile()) {
                logger.debug("File not found: {}", requestedPath);
                sendError(exchange, 404, "File Not Found");
                return;
            }

            // Check if file is readable
            if (!file.canRead()) {
                logger.warn("Cannot read file: {}", requestedPath);
                sendError(exchange, 403, "Forbidden - Cannot read file");
                return;
            }

            // Check file size limit
            if (file.length() > MAX_FILE_SIZE) {
                logger.warn("File exceeds maximum size: {} ({})", requestedPath, file.length());
                sendError(exchange, 413, "Content Too Large");
                return;
            }

            // Set content type based on file extension
            String fileName = file.getName();
            String contentType = getContentType(fileName);

            // Set response headers
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(file.length()));
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=" + DEFAULT_CACHE_MAX_AGE);

            // Set security headers
            setSecurityHeaders(exchange, fileName);

            // For HEAD requests, don't send the file content
            if (requestMethod.equals("HEAD")) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            // Send response code with content length
            exchange.sendResponseHeaders(200, file.length());

            // Send file content
            try (OutputStream os = exchange.getResponseBody();
                 FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int count;
                long totalBytesRead = 0;

                while ((count = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, count);

                    // Update total bytes read
                    totalBytesRead += count;

                    // Check if we're somehow exceeding the file size (safeguard)
                    if (totalBytesRead > MAX_FILE_SIZE) {
                        logger.warn("File streaming exceeded maximum allowed size: {}", requestedPath);
                        break;
                    }
                }
                os.flush();
            }
        } catch (Exception e) {
            logger.error("Error serving static file", e);
            try {
                sendError(exchange, 500, "Internal Server Error");
            } catch (IOException ignored) {
                // Ignored
            }
        } finally {
            exchange.close();
        }
    }

    /**
     * Get content type based on file extension
     */
    private String getContentType(String fileName) {
        String contentType = "application/octet-stream"; // Default content type

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            String extension = fileName.substring(dotIndex + 1).toLowerCase();
            if (mimeTypes.containsKey(extension)) {
                contentType = mimeTypes.get(extension);
            }
        }

        return contentType;
    }

    /**
     * Set security-related HTTP headers, with different CSP based on file type and mode
     */
    private void setSecurityHeaders(HttpExchange exchange, String fileName) {
        // Prevent MIME type sniffing
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");

        // XSS protection
        exchange.getResponseHeaders().set("X-XSS-Protection", "1; mode=block");

        // Prevent clickjacking
        exchange.getResponseHeaders().set("X-Frame-Options", "SAMEORIGIN");

        // Set appropriate CSP based on file type and mode
        if (fileName.toLowerCase().endsWith(".html") || fileName.toLowerCase().endsWith(".htm")) {
            // For HTML files, set a content security policy
            if (developmentMode) {
                // Development mode - relaxed CSP
                exchange.getResponseHeaders().set("Content-Security-Policy",
                        "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                                "style-src 'self' 'unsafe-inline'; img-src 'self' data:; " +
                                "connect-src 'self'; font-src 'self'; " +
                                "object-src 'none'; media-src 'self'; " +
                                "frame-src 'self';");
            } else {
                // Production mode - stricter CSP
                exchange.getResponseHeaders().set("Content-Security-Policy",
                        "default-src 'self'; script-src 'self'; style-src 'self'; " +
                                "img-src 'self'; connect-src 'self'; font-src 'self'; " +
                                "object-src 'none'; media-src 'self'; frame-src 'self';");
            }
        }
    }

    /**
     * Send error response
     */
    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, message.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(message.getBytes());
        }
    }

    /**
     * Validate path for malicious patterns
     */
    private boolean isValidPath(String path) {
        // Check for null or empty path
        if (path == null || path.isEmpty()) {
            return false;
        }

        // Check for potentially dangerous character sequences
        String[] dangerousPatterns = {
                "//", "\\\\", "\0", "%00", "<?", "?>", "<script", "javascript:", "data:",
                "vbscript:", "file:", ".htaccess", ".htpasswd", "web.config"
        };

        for (String pattern : dangerousPatterns) {
            if (path.contains(pattern) && !pattern.equals("<script")) { // Allow <script> in path for now
                return false;
            }
        }

        // Additional URL encoding validation can be added here

        return true;
    }
}