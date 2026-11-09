/*
 * Description: Contract for sending group-bot Webhook notifications (e.g. WeCom, Feishu)
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.msg;

/**
 * Contract for sending Webhook notifications.
 *
 * <p>Targets group-bot style Webhooks such as WeCom (企业微信) or Feishu (飞书).
 * These providers are all invoked the same way - an HTTP POST of a small JSON
 * payload to a per-provider URL that already embeds the bot key/token - so a
 * single implementation is enough to cover any of them; only the URL differs
 * per configuration.
 */
public interface WebhookService extends NotificationService {
    void sendWebhook(String webhookUrl, String content);
}
