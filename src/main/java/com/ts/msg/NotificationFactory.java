/*
 * Description: Contract for creating NotificationService instances
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.msg;
/**
 * Contract for creating {@link NotificationService} instances.
 *
 * <p>Allows the notification channel (email, Webhook, or both) to be selected
 * at runtime without changing the caller.
 */
public class NotificationFactory {
    /**
     * Returns the notification service.
     *
     * @param type  type discriminator
     * @return the requested notificationservice object, or null if not found
     */
    public NotificationService getNotificationService(String type) {
        // Implementation to return the appropriate msg service
        return null;
    }
}