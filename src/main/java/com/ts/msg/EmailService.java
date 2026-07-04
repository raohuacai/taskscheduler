/*
 * Description: Contract for sending email notifications
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.msg;

import java.util.List;
/**
 * Contract for sending email notifications.
 */
public interface EmailService extends NotificationService {
    void sendEmail(String subject, String content, List<String> recipients);
}