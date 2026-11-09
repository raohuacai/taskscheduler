/*
 * Description: Configuration bean for the embedded HTTP server (port, static path, etc.)
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;
/**
 * Configuration bean for the embedded HTTP server.
 *
 * <p>Carries the listen port, static-resource path prefix, and whether
 * static file serving is enabled. Deserialised from the global parameter
 * store at startup by {@link com.ts.service.ParamsService}.
 */
public class HttpServerConfig {
    private static final Logger logger = LogManager.getLogger(HttpServerConfig.class);

    private String httpServerPath;
    private int httpServerPort;
    private boolean enableStaticResource;

    // Default constructor with default values
    /**
     * Constructs a new HttpServerConfig instance.
     */
    public HttpServerConfig() {
        this.httpServerPath = "/static";
        this.httpServerPort = 8888;
    }

    // Constructor with all parameters
    /**
     * Constructs a new HttpServerConfig instance.
     *
     * @param httpServerPath  file-system path
     * @param httpServerPort  httpServerPort value
     * @param enableStaticResource  enableStaticResource value
     */
    public HttpServerConfig(String httpServerPath, int httpServerPort,boolean enableStaticResource) {
        this.httpServerPath = httpServerPath;
        this.httpServerPort = httpServerPort;
        this.enableStaticResource = enableStaticResource;
    }

    // Getters and Setters
    /**
     * Returns the http server port.
     *
     * @return the numeric result
     */
    public int getHttpServerPort() {
        return httpServerPort;
    }

    /**
     * Sets the http server port.
     *
     * @param httpServerPort  httpServerPort value
     */
    public void setHttpServerPort(int httpServerPort) {
        this.httpServerPort = httpServerPort;
    }

    /**
     * Returns the http server path.
     *
     * @return the result as a String
     */
    public String getHttpServerPath() {
        return httpServerPath;
    }

    /**
     * Sets the http server path.
     *
     * @param httpServerPath  file-system path
     */
    public void setHttpServerPath(String httpServerPath) {
        this.httpServerPath = httpServerPath;
    }

    /**
     * Checks whether the enable static resource.
     *
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean isEnableStaticResource() {
        return enableStaticResource;
    }

    /**
     * Sets the enable static resource.
     *
     * @param enableStaticResource  enableStaticResource value
     */
    public void setEnableStaticResource(boolean enableStaticResource) {
        this.enableStaticResource = enableStaticResource;
    }

    /**
     * Get the mail configuration as a properties map
     *
     * @return Properties map containing mail configuration
     */
    public Properties toProperties() {
        Properties props = new Properties();
        props.put("httpServerPath", httpServerPath);
        props.put("httpServerPort", String.valueOf(httpServerPort));
        props.put("enableStaticResource", String.valueOf(enableStaticResource));
        return props;
    }

    @Override
    /**
     * To the string.
     *
     * @return the result as a String
     */
    public String toString() {
        return "HttpServerConfig{" +
                "httpServerPath='" + httpServerPath + '\'' +
                ", httpServerPort=" + httpServerPort +
                ", enableStaticResource=" + enableStaticResource +
                '}';
    }
}
