/*
 * Description: HTTP Webhook notification service for group bots (WeCom, Feishu, etc.)
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.msg;

import com.ts.config.EmailTemplateConfig;
import com.ts.service.ParamsService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
/**
 * HTTP-based implementation of {@link WebhookService} for group-bot Webhooks
 * such as WeCom (企业微信) and Feishu (飞书).
 *
 * <p>Uses {@link java.net.HttpURLConnection} to POST a small JSON payload,
 * consistent with the HTTP call style used elsewhere in the engine (see
 * {@code com.ts.task.http.HttpTask}).
 *
 * <p>The Webhook URL (which already embeds the provider key/token) and the
 * enable flag are read per task/workflow from GLOBAL_PARAMS; message templates
 * are reused from {@link com.ts.config.EmailTemplateConfig}, the same as
 * {@link EmailServiceImpl}. The HTTP call timeout is not yet exposed via
 * configuration and is fixed by {@link #WEBHOOK_TIMEOUT_SECONDS}.
 */
public class WebhookServiceImpl implements WebhookService {
    private static final Logger logger = LogManager.getLogger(WebhookServiceImpl.class);

    // Webhook call timeout in seconds; not yet exposed through GLOBAL_PARAMS configuration
    private static final int WEBHOOK_TIMEOUT_SECONDS = 15;

    private final ParamsService paramsService;
    private volatile Properties templateProperties;
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * Constructor with default properties.
     *
     * <p>Template properties are loaded lazily on first use to avoid
     * dependency on configuration loading order.
     */
    public WebhookServiceImpl(ParamsService paramsService) {
        this.paramsService = paramsService;
    }

    @Override
    /**
     * Sends the webhook.
     *
     * @param webhookUrl  target Webhook URL (provider key/token is embedded in the URL)
     * @param content  content value
     */
    public void sendWebhook(String webhookUrl, String content) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            logger.warn("Webhook url is empty, skip sending msg");
            return;
        }

        HttpURLConnection conn = null;
        try {
            String payload = buildPayload(webhookUrl, content);
            byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

            URL url = new URL(webhookUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(WEBHOOK_TIMEOUT_SECONDS * 1000);
            conn.setReadTimeout(WEBHOOK_TIMEOUT_SECONDS * 1000);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Content-Length", String.valueOf(payloadBytes.length));
            conn.setDoInput(true);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payloadBytes);
                os.flush();
            }

            int statusCode = conn.getResponseCode();
            String responseBody = readResponse(conn, statusCode);

            if (statusCode >= 200 && statusCode < 300) {
                logger.info("Webhook msg sent to: {}", webhookUrl);
            } else {
                logger.error("Webhook msg failed, url: {}, status: {}, body: {}",
                        webhookUrl, statusCode, responseBody);
            }
        } catch (Exception e) {
            // A failed Webhook call must never break the caller's business flow, log only
            logger.error("Error sending webhook msg to {}", webhookUrl, e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @Override
    /**
     * Sends the notification.
     *
     * @param template  template value
     * @param parameters  parameter map
     * @param recipients  target Webhook URL(s)
     */
    public void sendNotification(String template, Map<String, Object> parameters, List<String> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            logger.warn("No webhook url configured for msg");
            return;
        }

        // Get template body, reusing the same email templates
        Properties templateProps = getTemplateProperties();
        //String subjectTemplate = templateProps.getProperty(template + "Subject");
        String bodyTemplate = templateProps.getProperty(template + "Body");

        //if (subjectTemplate == null || bodyTemplate == null) {
        if (bodyTemplate == null) {
            logger.error("Webhook template not found: {}", template);
            return;
        }

        // Replace placeholders with parameter values
        //String subject = replacePlaceholders(subjectTemplate, parameters);
        String body = replacePlaceholders(bodyTemplate, parameters);
        //String content = subject + "\n" + body;
        String content = body;

        // Send to every configured Webhook url
        for (String webhookUrl : recipients) {
            sendWebhook(webhookUrl, content);
        }
    }

    /**
     * Returns the template properties, loading them on first access with
     * thread-safe double-checked locking.
     *
     * @return the template properties
     */
    private Properties getTemplateProperties() {
        Properties tp = templateProperties;
        if (tp == null) {
            synchronized (this) {
                tp = templateProperties;
                if (tp == null) {
                    tp = loadTemplateProperties();
                    templateProperties = tp;
                }
            }
        }
        return tp;
    }

    /**
     * Load template properties from GLOBAL_PARAMS.
     *
     * @return Template properties
     */
    private Properties loadTemplateProperties() {
        EmailTemplateConfig etc = paramsService.getEmailTemplateConfig();
        return etc.toProperties();
    }

    /**
     * Reads and returns the full response body from the connection as a UTF-8 string.
     *
     * <p>For 4xx/5xx responses, reads from {@link java.net.HttpURLConnection#getErrorStream()}
     * rather than {@link java.net.HttpURLConnection#getInputStream()}. Returns the literal
     * string {@code "(no error body)"} when the error stream is unavailable.
     *
     * @param conn        the connection whose body is to be read
     * @param statusCode  the HTTP response status code; drives stream selection
     * @return the full response body as a string; never {@code null}
     * @throws IOException  if the stream cannot be read
     */
    private String readResponse(HttpURLConnection conn, int statusCode) throws IOException {
        InputStream stream;
        if (statusCode >= 400) {
            stream = conn.getErrorStream();
            if (stream == null) {
                return "(no error body)";
            }
        } else {
            stream = conn.getInputStream();
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * Build the provider-specific Webhook payload.
     *
     * <p>WeCom and Feishu bot Webhooks both accept a small JSON text-message
     * payload but use slightly different field names, so the provider is
     * detected from the url; any other Webhook url falls back to the WeCom
     * style payload, which most generic group-bot receivers also accept.
     *
     * @param webhookUrl Webhook url, used to detect the provider
     * @param content    message content
     * @return JSON payload string
     */
    private String buildPayload(String webhookUrl, String content) {
        String escaped = escapeJson(content);
        if (webhookUrl.contains("feishu.cn") || webhookUrl.contains("larksuite.com")) {
            // Feishu (飞书) bot Webhook payload
            return "{\"msg_type\":\"text\",\"content\":{\"text\":\"" + escaped + "\"}}";
        }
        // WeCom (企业微信) bot Webhook payload, also used as the generic default
        return "{\"msgtype\":\"text\",\"text\":{\"content\":\"" + escaped + "\"}}";
    }

    /**
     * Escape a string for safe inclusion in a JSON string value.
     *
     * @param text raw text
     * @return escaped text
     */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    /**
     * Replace placeholders in template with parameter values.
     *
     * @param template   Template string
     * @param parameters Parameter values
     * @return Template with placeholders replaced
     */
    private String replacePlaceholders(String template, Map<String, Object> parameters) {
        if (template == null || parameters == null) {
            return template;
        }

        String result = template;

        // Find placeholders in the format {placeholder}
        Pattern pattern = Pattern.compile("\\{([^}]*)\\}");
        Matcher matcher = pattern.matcher(template);

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            Object value = parameters.get(placeholder);

            if (value != null) {
                String replacement;
                if (value instanceof Date) {
                    SimpleDateFormat sdf = new SimpleDateFormat(DEFAULT_DATE_FORMAT);
                    replacement = sdf.format((Date) value);
                } else {
                    replacement = value.toString();
                }
                result = result.replace("{" + placeholder + "}", replacement);
            }
        }

        return result;
    }
}