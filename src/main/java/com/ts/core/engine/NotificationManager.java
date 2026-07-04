/*
 * Description: Asynchronous dispatcher for workflow and task lifecycle email/Webhook notifications
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.core.engine;

import com.ts.common.constants.Constants;
import com.ts.common.utils.DateUtils;
import com.ts.common.utils.JsonUtils;
import com.ts.model.entity.TaskInstance;
import com.ts.model.entity.TaskParameter;
import com.ts.model.entity.WfInstance;
import com.ts.model.entity.WfParameter;
import com.ts.msg.NotificationFactory;
import com.ts.msg.NotificationService;
import com.ts.service.DefinitionService;
import com.ts.service.ExecutionService;
import com.ts.service.ParamsService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * Asynchronous notification dispatcher for workflow and task lifecycle events.
 *
 * <p>Formats and sends email/Webhook alerts when workflows or tasks start, complete,
 * fail, time out, or are cancelled. Notifications are dispatched on a dedicated
 * 4-thread executor to avoid blocking the main workflow execution path.
 *
 * <p>Two notification channels are supported and can be enabled independently
 * (or together) per task/workflow via GLOBAL_PARAMS:
 * <ul>
 *     <li>Email - controlled by {@link Constants#PARAM_NOTICE_EMAIL} / {@link Constants#PARAM_ERROR_EMAIL}</li>
 *     <li>Webhook (e.g. 企业微信, 飞书) - controlled by {@link Constants#PARAM_NOTICE_WEBHOOK} / {@link Constants#PARAM_ERROR_WEBHOOK}</li>
 * </ul>
 * "Notice" params fire on workflow start/end; "error" params fire on error,
 * cancellation, and timeout events.
 *
 * <p>Each notification is parameterised using template variables drawn from
 * workflow/task metadata and the {@link com.ts.config.EmailTemplateConfig}
 * loaded at startup. The actual delivery is delegated to a
 * {@link com.ts.msg.NotificationFactory}-supplied
 * {@link com.ts.msg.NotificationService} instance.
 *
 * @see com.ts.msg.EmailService
 * @see com.ts.msg.WebhookService
 * @see com.ts.config.EmailTemplateConfig
 */
public class NotificationManager {
    private static final Logger logger = LogManager.getLogger(NotificationManager.class);
    private final DefinitionService definitionService;
    private final ExecutionService executionService;
    private final ParamsService paramsService;
    private final NotificationFactory notificationFactory;
    // Async email/webhook dispatch; the dedicated thread pool has 4 threads
    private static final ExecutorService NOTIFICATION_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * Constructs a new NotificationManager instance.
     *
     * @param definitionService  service dependency
     * @param executionService  service dependency
     * @param paramsService  service dependency
     * @param notificationFactory  factory instance
     */
    public NotificationManager(DefinitionService definitionService, ExecutionService executionService,
                               ParamsService paramsService, NotificationFactory notificationFactory) {
        this.definitionService = definitionService;
        this.executionService = executionService;
        this.paramsService = paramsService;
        this.notificationFactory = notificationFactory;
    }

    /**
     * Sends the wf notification.
     *
     * @param wfInstanceId  instance identifier
     * @param notificationType  type discriminator
     */
    public void sendWfNotification(String wfInstanceId, String notificationType) {
        try {
            boolean isError = isErrorNotificationType(notificationType);
            WfParameter wfParameter = paramsService.getWfParamsByInstanceId(wfInstanceId);
            Map<String, Object> parameters = JsonUtils.jsonToMap(wfParameter.getPvalue());
            WfInstance wfInstance = executionService.getWfInstance(wfInstanceId);

            Map<String, Object> templateParams = new HashMap<>();
            templateParams.put("workflowName", wfParameter.getWorkflowName());

            // Populate notification parameters based on the notification type
            switch (notificationType) {
                case Constants.TYPE_WORKFLOW_START:
                    templateParams.put("startTime", DateUtils.formatDate(wfInstance.getStartDate(), "yyyy-MM-dd HH:mm:ss"));
                    break;

                case Constants.TYPE_WORKFLOW_END:
                    templateParams.put("startTime", DateUtils.formatDate(wfInstance.getStartDate(), "yyyy-MM-dd HH:mm:ss"));
                    templateParams.put("endTime", DateUtils.formatDate(wfInstance.getEndDate(), "yyyy-MM-dd HH:mm:ss"));
                    templateParams.put("duration", DateUtils.getTimeDifferenceInSeconds(wfInstance.getStartDate(), wfInstance.getEndDate()));
                    break;

                default: // Covers error, cancellation, timeout, and other notification types
                    templateParams.put("endTime", DateUtils.formatDate(wfInstance.getEndDate(), "yyyy-MM-dd HH:mm:ss"));
                    String errorMsg = wfInstance.getErrorMessage();
                    templateParams.put("errorMessage", errorMsg != null ? errorMsg : "No error details available");
                    break;
            }

            // Email and Webhook channels are independent - either, both, or neither may fire
            dispatchEmail(parameters, isError, notificationType, templateParams, wfInstanceId, "workflow");
            dispatchWebhook(parameters, isError, notificationType, templateParams, wfInstanceId, "workflow");
        } catch (Exception e) {
            logger.error("Error preparing notification data for workflow: {}", wfInstanceId, e);
        }
    }

    /**
     * Sends the task notification.
     *
     * @param taskInstanceId  instance identifier
     * @param notificationType  type discriminator
     */
    public void sendTaskNotification(String taskInstanceId, String notificationType) {
        try {
            boolean isError = isErrorNotificationType(notificationType);
            TaskParameter taskParam = paramsService.getTaskParamsByInstanceId(taskInstanceId);
            Map<String, Object> parameters = JsonUtils.jsonToMap(taskParam.getPvalue());

            WfParameter wfParam = paramsService.getWfParamsByInstanceId(taskParam.getWfInstanceId());
            TaskInstance taskInstance = executionService.getTaskInstance(taskInstanceId);

            Map<String, Object> templateParams = new HashMap<>();
            templateParams.put("taskName", taskParam.getTaskName());
            templateParams.put("workflowName", wfParam.getWorkflowName());
            templateParams.put("endTime", taskInstance.getEndDate());
            String errorMsg = taskInstance.getErrorMessage();
            templateParams.put("errorMessage", errorMsg != null ? errorMsg : "No error details available");

            // Email and Webhook channels are independent - either, both, or neither may fire
            dispatchEmail(parameters, isError, notificationType, templateParams, taskInstanceId, "task");
            dispatchWebhook(parameters, isError, notificationType, templateParams, taskInstanceId, "task");
        } catch (Exception e) {
            logger.error("Error preparing notification data for task: {}", taskInstanceId, e);
        }
    }

    // Utility / helper methods

    /**
     * Dispatch a notification through the email channel, if enabled for this
     * task/workflow and notification type.
     *
     * @param parameters       resolved GLOBAL_PARAMS map for the task/workflow
     * @param isError          whether this is an error-family notification (error/cancel/timeout)
     * @param notificationType type discriminator
     * @param templateParams   template variables for the message body
     * @param instanceId       task or workflow instance id, used for logging only
     * @param scope            "task" or "workflow", used for logging only
     */
    private void dispatchEmail(Map<String, Object> parameters, boolean isError, String notificationType,
                                Map<String, Object> templateParams, String instanceId, String scope) {
        String flagKey = isError ? Constants.PARAM_ERROR_EMAIL : Constants.PARAM_NOTICE_EMAIL;
        String notifyFlag = String.valueOf(parameters.getOrDefault(flagKey, Constants.FLAG_NO));
        if (!Constants.FLAG_YES.equals(notifyFlag)) {
            return;
        }

        String addressKey = isError ? Constants.PARAM_ERROR_EMAIL_ADDRESS : Constants.PARAM_NOTICE_EMAIL_ADDRESS;
        String emailAddresses = (String) parameters.getOrDefault(addressKey, "");
        if (emailAddresses.isEmpty()) {
            logger.warn("No email addresses configured for {} msg", scope);
            return;
        }

        NotificationService emailService = notificationFactory.getNotificationService("email");
        if (emailService == null) {
            logger.warn("Email msg service not available");
            return;
        }

        List<String> recipients = Arrays.asList(emailAddresses.split(","));
        submitNotification(emailService, "email", notificationType, templateParams, recipients, instanceId, scope);
    }

    /**
     * Dispatch a notification through the Webhook channel (企业微信, 飞书, etc.),
     * if enabled for this task/workflow and notification type.
     *
     * @param parameters       resolved GLOBAL_PARAMS map for the task/workflow
     * @param isError          whether this is an error-family notification (error/cancel/timeout)
     * @param notificationType type discriminator
     * @param templateParams   template variables for the message body
     * @param instanceId       task or workflow instance id, used for logging only
     * @param scope            "task" or "workflow", used for logging only
     */
    private void dispatchWebhook(Map<String, Object> parameters, boolean isError, String notificationType,
                                  Map<String, Object> templateParams, String instanceId, String scope) {
        String flagKey = isError ? Constants.PARAM_ERROR_WEBHOOK : Constants.PARAM_NOTICE_WEBHOOK;
        String webhookFlag = String.valueOf(parameters.getOrDefault(flagKey, Constants.FLAG_NO));
        if (!Constants.FLAG_YES.equals(webhookFlag)) {
            return;
        }

        String urlKey = isError ? Constants.PARAM_ERROR_WEBHOOK_URL : Constants.PARAM_NOTICE_WEBHOOK_URL;
        String webhookUrl = String.valueOf(parameters.getOrDefault(urlKey, ""));
        if (webhookUrl.isEmpty()) {
            logger.warn("No webhook url configured for {} msg", scope);
            return;
        }

        NotificationService webhookService = notificationFactory.getNotificationService("webhook");
        if (webhookService == null) {
            logger.warn("Webhook msg service not available");
            return;
        }

        submitNotification(webhookService, "webhook", notificationType, templateParams,
                Collections.singletonList(webhookUrl), instanceId, scope);
    }

    /**
     * Submit a notification send onto the shared executor. Any send failure is
     * logged only, and never propagated back to the caller's business flow.
     *
     * @param notificationService the channel-specific service to send through
     * @param channel              "email" or "webhook", used for logging only
     * @param notificationType     type discriminator
     * @param templateParams       template variables for the message body
     * @param recipients           email addresses, or a single-element list containing the Webhook url
     * @param instanceId           task or workflow instance id, used for logging only
     * @param scope                "task" or "workflow", used for logging only
     */
    private void submitNotification(NotificationService notificationService, String channel, String notificationType,
                                      Map<String, Object> templateParams, List<String> recipients,
                                      String instanceId, String scope) {
        NOTIFICATION_EXECUTOR.submit(() -> {
            try {
                notificationService.sendNotification(notificationType, templateParams, recipients);
                logger.info("{} {} msg sent for {}: {}", channel, notificationType, scope, instanceId);
            } catch (Exception e) {
                logger.error("Failed to send {} {} msg for {} {}", channel, notificationType, scope, instanceId, e);
            }
        });
    }

    /**
     * Shuts down the notification executor, allowing pending notifications to drain.
     */
    public void shutdown() {
        NOTIFICATION_EXECUTOR.shutdown();
        logger.info("NotificationManager executor shutdown initiated");
    }

    private boolean isErrorNotificationType(String notificationType) {
        return Constants.TYPE_WORKFLOW_ERROR.equals(notificationType) ||
                Constants.TYPE_WORKFLOW_CANCEL.equals(notificationType) ||
                Constants.TYPE_WORKFLOW_TIMEOUT.equals(notificationType) ||
                Constants.TYPE_TASK_ERROR.equals(notificationType) ||
                Constants.TYPE_TASK_CANCEL.equals(notificationType) ||
                Constants.TYPE_TASK_TIMEOUT.equals(notificationType);
    }
}
