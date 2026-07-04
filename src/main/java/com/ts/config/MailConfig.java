/*
 * Description: Configuration bean for SMTP mail sender settings
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;
/**
 * Configuration bean for the SMTP mail sender.
 *
 * <p>Carries SMTP host, port, credentials, TLS flag, and the default sender
 * address. Deserialised from the global parameter store at startup.
 */
public class MailConfig {
    private static final Logger logger = LogManager.getLogger(MailConfig.class);

    private String smtpHost;
    private int smtpPort;
    private boolean smtpAuth;
    private boolean starttlsEnable;
    private int connectionTimeout;
    private int timeout;
    private String username;
    private String password;
    private boolean sslEnable;

    // Default constructor with default values
    /**
     * Constructs a new MailConfig instance.
     */
    public MailConfig() {
        this.smtpHost = "localhost";
        this.smtpPort = 25;
        this.smtpAuth = false;
        this.starttlsEnable = false;
        this.sslEnable = false;
        this.connectionTimeout = 5000;
        this.timeout = 5000;
        this.username = "";
        this.password = "";
    }

    // Constructor with all parameters
    /**
     * Constructs a new MailConfig instance.
     *
     * @param smtpHost  smtpHost value
     * @param smtpPort  smtpPort value
     * @param smtpAuth  smtpAuth value
     * @param starttlsEnable  starttlsEnable value
     * @param sslEnable  sslEnable value
     * @param connectionTimeout  date/time value
     * @param timeout  date/time value
     * @param username  username name
     * @param password  password value
     */
    public MailConfig(String smtpHost, int smtpPort, boolean smtpAuth, boolean starttlsEnable, boolean sslEnable,
                      int connectionTimeout, int timeout, String username, String password) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpAuth = smtpAuth;
        this.starttlsEnable = starttlsEnable;
        this.sslEnable = sslEnable;
        this.connectionTimeout = connectionTimeout;
        this.timeout = timeout;
        this.username = username;
        this.password = password;
    }

    // Getters and Setters
    /**
     * Returns the smtp host.
     *
     * @return the result as a String
     */
    public String getSmtpHost() {
        return smtpHost;
    }

    /**
     * Sets the smtp host.
     *
     * @param smtpHost  smtpHost value
     */
    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    /**
     * Returns the smtp port.
     *
     * @return the numeric result
     */
    public int getSmtpPort() {
        return smtpPort;
    }

    /**
     * Sets the smtp port.
     *
     * @param smtpPort  smtpPort value
     */
    public void setSmtpPort(int smtpPort) {
        this.smtpPort = smtpPort;
    }

    /**
     * Checks whether the smtp auth.
     *
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean isSmtpAuth() {
        return smtpAuth;
    }

    /**
     * Sets the smtp auth.
     *
     * @param smtpAuth  smtpAuth value
     */
    public void setSmtpAuth(boolean smtpAuth) {
        this.smtpAuth = smtpAuth;
    }

    /**
     * Checks whether the starttls enable.
     *
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean isStarttlsEnable() {
        return starttlsEnable;
    }

    /**
     * Sets the starttls enable.
     *
     * @param starttlsEnable  starttlsEnable value
     */
    public void setStarttlsEnable(boolean starttlsEnable) {
        this.starttlsEnable = starttlsEnable;
    }

    /**
     * Returns the connection timeout.
     *
     * @return the numeric result
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Sets the connection timeout.
     *
     * @param connectionTimeout  date/time value
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * Returns the timeout.
     *
     * @return the numeric result
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * Sets the timeout.
     *
     * @param timeout  date/time value
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /**
     * Returns the username.
     *
     * @return the result as a String
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username.
     *
     * @param username  username name
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Returns the password.
     *
     * @return the result as a String
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password.
     *
     * @param password  password value
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isSslEnable() {
        return sslEnable;
    }

    public void setSslEnable(boolean sslEnable) {
        this.sslEnable = sslEnable;
    }

    /**
     * Get the mail configuration as a properties map
     *
     * @return Properties map containing mail configuration
     */
    public Properties toProperties() {
        Properties props = new Properties();
        props.put("smtpHost", smtpHost);
        props.put("smtpPort", String.valueOf(smtpPort));
        props.put("smtpAuth", String.valueOf(smtpAuth));
        props.put("starttlsEnable", String.valueOf(starttlsEnable));
        props.put("sslEnable", String.valueOf(sslEnable));
        props.put("connectionTimeout", String.valueOf(connectionTimeout));
        props.put("timeout", String.valueOf(timeout));
        props.put("username", username);
        props.put("password", password);
        return props;
    }

    @Override
    /**
     * To the string.
     *
     * @return the result as a String
     */
    public String toString() {
        return "MailConfig{" +
                "smtpHost='" + smtpHost + '\'' +
                ", smtpPort=" + smtpPort +
                ", smtpAuth=" + smtpAuth +
                ", starttlsEnable=" + starttlsEnable +
                ", sslEnable=" + sslEnable +
                ", connectionTimeout=" + connectionTimeout +
                ", timeout=" + timeout +
                ", username='" + username + '\'' +
                ", password='********'" +
                '}';
    }
}