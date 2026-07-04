/*
 * Description: Default NotificationFactory producing the configured notification channel
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.msg;

import com.ts.service.ParamsService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
/**
 * Default {@link NotificationFactory} that instantiates the appropriate
 * {@link NotificationService} based on the configured notification channel.
 */
public class NotificationFactoryImpl extends NotificationFactory {
    private static final Logger logger = LogManager.getLogger(NotificationFactoryImpl.class);

    private final ParamsService paramsService;
    private final EmailService emailService;
    private final WebhookService webhookService;

    /**
     * Constructor with default services
     */
    public NotificationFactoryImpl(ParamsService paramsService) {
        this.paramsService = paramsService;
        this.emailService = new EmailServiceImpl(paramsService);
        this.webhookService = new WebhookServiceImpl(paramsService);
    }

    @Override
    /**
     * Returns the notification service.
     *
     * @param type  type discriminator
     * @return the requested notificationservice object, or null if not found
     */
    public NotificationService getNotificationService(String type) {
        if ("email".equalsIgnoreCase(type)) {
            return emailService;
        } else if ("webhook".equalsIgnoreCase(type)) {
            return webhookService;
        } else {
            logger.warn("Unknown msg type: {}", type);
            return null;
        }
    }
}
