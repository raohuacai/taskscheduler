/*
 * Description: Contract for dispatching a notification with template variable substitution
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.msg;

import java.util.List;
import java.util.Map;
/**
 * Contract for sending a workflow or task lifecycle notification.
 *
 * <p>Called by {@link com.ts.core.engine.NotificationManager} with a map of
 * template variables that the implementation uses to format and dispatch the
 * notification message.
 */
public interface NotificationService {
    void sendNotification(String template, Map<String, Object> parameters, List<String> recipients);
}