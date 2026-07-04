/*
 * Description: SMTP-based email service using JavaMail with TLS support
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.msg;

import com.ts.config.EmailTemplateConfig;
import com.ts.config.MailConfig;
import com.ts.service.ParamsService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * SMTP-based implementation of {@link EmailService} using
 * {@link javax.mail.internet.MimeMessage}.
 *
 * <p>Connection parameters are loaded from {@link com.ts.config.MailConfig}.
 * Supports TLS (STARTTLS) and optional authentication.
 */
public class EmailServiceImpl implements EmailService {
    private static final Logger logger = LogManager.getLogger(EmailServiceImpl.class);

    private final ParamsService paramsService;
    private volatile Properties emailProperties = null;
    private volatile Session session = null;
    private volatile Properties templateProperties = null;
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * Constructor with default properties
     */
    public EmailServiceImpl(ParamsService paramsService) {
        this.paramsService = paramsService;
        //this.emailProperties = loadEmailProperties();
        //this.templateProperties = loadTemplateProperties();
        //this.session = createSession(emailProperties);
    }

    @Override
    /**
     * Sends the email.
     *
     * @param subject  subject value
     * @param content  content value
     * @param recipients  recipients value
     */
    public void sendEmail(String subject, String content, List<String> recipients) {
        try {
           getOrCreateSession();
            MimeMessage message = new MimeMessage(session);

            // Set from address
            message.setFrom(new InternetAddress(emailProperties.getProperty("username")));

            // Set recipients
            for (String recipient : recipients) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
            }

            // Set subject and content
            message.setSubject(subject);
            message.setText(content);

            // Send message
            Transport.send(message);

            logger.info("Email sent to: {}", String.join(", ", recipients));
        } catch (MessagingException e) {
            logger.error("Error sending email to {} ,", String.join(", ", recipients), e.getMessage());
            throw new RuntimeException("Error sending email", e);
        }
    }

    @Override
    /**
     * Sends the notification.
     *
     * @param template  template value
     * @param parameters  parameter map
     * @param recipients  recipients value
     */
    public void sendNotification(String template, Map<String, Object> parameters, List<String> recipients) {
        getTemplateProperties();
        // Get template subject and body
        String subjectTemplate = templateProperties.getProperty(template + "Subject");
        String bodyTemplate = templateProperties.getProperty(template + "Body");

        if (subjectTemplate == null || bodyTemplate == null) {
            logger.error("Email template not found: {}", template);
            throw new IllegalArgumentException("Email template not found: " + template);
        }

        // Replace placeholders with parameter values
        String subject = replacePlaceholders(subjectTemplate, parameters);
        String body = replacePlaceholders(bodyTemplate, parameters);

        // Send email
        sendEmail(subject, body, recipients);
    }

    /**
     * Replace placeholders in template with parameter values
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
                //result = result.replace("{" + placeholder + "}", value.toString());
                String replacement;
                if (value instanceof Date) {
                    // 格式化日期
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

    /**
     * Returns a valid Session, creating it lazily with thread-safe
     * double-checked locking. Initialization happens at most once;
     * subsequent calls return the cached instance directly.
     */
    private Session getOrCreateSession() {
        Session s = session;
        if (s == null) {
            synchronized (this) {
                s = session;
                if (s == null) {
                    Properties props = loadEmailProperties();
                    emailProperties = props;
                    s = createSession(props);
                    session = s;
                }
            }
        }
        return s;
    }

    /**
     * Load email properties from GLOBAL_PARAMS
     *
     * @return Email properties
     */
    private Properties loadEmailProperties() {
        MailConfig mailConfig = paramsService.getMailConfig();
        return mailConfig.toProperties();
    }

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
     * Load template properties from GLOBAL_PARAMS
     *
     * @return Template properties
     */
    private Properties loadTemplateProperties() {
        EmailTemplateConfig etc = paramsService.getEmailTemplateConfig();
        return etc.toProperties();
    }

    /**
     * Create JavaMail session
     *
     * @param properties Email properties
     * @return JavaMail session
     */
    private Session createSession(Properties properties) {
        Properties sessionProps = new Properties();

        sessionProps.put("mail.smtp.host", properties.getProperty("smtpHost", "localhost"));
        sessionProps.put("mail.smtp.port", properties.getProperty("smtpPort", "25"));
        sessionProps.put("mail.smtp.auth", properties.getProperty("smtpAuth", "false"));
        sessionProps.put("mail.smtp.connectiontimeout", properties.getProperty("connectionTimeout", "5000"));
        sessionProps.put("mail.smtp.timeout", properties.getProperty("timeout", "5000"));

        boolean ssl = Boolean.parseBoolean(properties.getProperty("sslEnable", "false"));
        boolean starttls = Boolean.parseBoolean(properties.getProperty("starttlsEnable", "false"));

        if (ssl) {
            sessionProps.put("mail.smtp.ssl.enable", "true");
            sessionProps.put("mail.smtp.starttls.enable", "false");
            sessionProps.put("mail.smtp.ssl.trust", "*");
        } else {
            sessionProps.put("mail.smtp.ssl.enable", "false");
            sessionProps.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        }

        // for mail debug
        //sessionProps.put("mail.debug", "true");

        if ("true".equalsIgnoreCase(properties.getProperty("smtpAuth"))) {
            final String username = properties.getProperty("username");
            final String password = properties.getProperty("password");
            return Session.getInstance(sessionProps, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });
        } else {
            return Session.getInstance(sessionProps);
        }
    }
}