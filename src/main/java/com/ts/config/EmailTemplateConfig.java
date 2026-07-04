/*
 * Description: Configuration bean for email notification subject/body templates
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;
/**
 * Configuration bean holding email subject and body templates for workflow
 * and task notification events.
 *
 * <p>Templates support {@code {variableName}} placeholders that are substituted
 * with actual values by {@link com.ts.core.engine.NotificationManager} at
 * notification dispatch time. Loaded from the global parameter store at startup.
 */
public class EmailTemplateConfig {
    private static final Logger logger = LogManager.getLogger(EmailTemplateConfig.class);

    // Workflow notification templates
    private String workflowStartSubject;
    private String workflowStartBody;
    private String workflowEndSubject;
    private String workflowEndBody;
    private String workflowCancelSubject;
    private String workflowCancelBody;
    private String workflowTimeoutSubject;
    private String workflowTimeoutBody;
    private String workflowErrorSubject;
    private String workflowErrorBody;

    // Task notification templates
    private String taskErrorSubject;
    private String taskErrorBody;
    private String taskCancelSubject;
    private String taskCancelBody;
    private String taskTimeoutSubject;
    private String taskTimeoutBody;

    // Default constructor with default values
    /**
     * Constructs a new EmailTemplateConfig instance.
     */
    public EmailTemplateConfig() {
        // Initialize with default templates
        this.workflowStartSubject = "Workflow Started: {workflowName}";
        this.workflowStartBody = "Dear,\n\nWorkflow '{workflowName}' has started at {startTime}.\n\nThanks";
        this.workflowEndSubject = "Workflow Completed: {workflowName}";
        this.workflowEndBody = "Dear,\n\nWorkflow '{workflowName}' has completed at {endTime}. Started at {startTime}. Total duration: {duration} seconds.\n\nThanks";
        this.workflowCancelSubject = "Workflow Cancelled: {workflowName}";
        this.workflowCancelBody = "Dear,\n\nWorkflow '{workflowName}' has been Cancelled at {endTime}.\n\nThanks";
        this.workflowTimeoutSubject = "Workflow Timeout: {workflowName}";
        this.workflowTimeoutBody = "Dear,\n\nWorkflow '{workflowName}' has been Timeout at {endTime}.\n\nThanks";
        this.workflowErrorSubject = "Workflow Error: {workflowName}";
        this.workflowErrorBody = "Dear,\n\nWorkflow '{workflowName}' encountered an error at {endTime}. Error details: \n{errorMessage}\n\nThanks";
        this.taskErrorSubject = "Task Error: {taskName} in {workflowName}";
        this.taskErrorBody = "Dear,\n\nTask '{taskName}' in workflow '{workflowName}' encountered an error at {endTime}. Error details: \n{errorMessage}\n\nThanks";
        this.taskCancelSubject = "Task Cancelled: {taskName} in {workflowName}";
        this.taskCancelBody = "Dear,\n\nTask '{taskName}' in workflow '{workflowName}' has been Cancelled at {endTime}.\n\nThanks";
        this.taskTimeoutSubject = "Task Timeout: {taskName} in {workflowName}";
        this.taskTimeoutBody = "Dear,\n\nTask '{taskName}' in workflow '{workflowName}' has been Timeout at {endTime}.\n\nThanks";
    }

    /**
     * Get the email templates as properties
     *
     * @return Properties object containing all template values
     */
    public Properties toProperties() {
        Properties props = new Properties();
        props.put("workflowStartSubject", workflowStartSubject);
        props.put("workflowStartBody", workflowStartBody);
        props.put("workflowEndSubject", workflowEndSubject);
        props.put("workflowEndBody", workflowEndBody);
        props.put("workflowCancelSubject", workflowCancelSubject);
        props.put("workflowCancelBody", workflowCancelBody);
        props.put("workflowTimeoutSubject", workflowTimeoutSubject);
        props.put("workflowTimeoutBody", workflowTimeoutBody);
        props.put("workflowErrorSubject", workflowErrorSubject);
        props.put("workflowErrorBody", workflowErrorBody);
        props.put("taskErrorSubject", taskErrorSubject);
        props.put("taskErrorBody", taskErrorBody);
        props.put("taskCancelSubject", taskCancelSubject);
        props.put("taskCancelBody", taskCancelBody);
        props.put("taskTimeoutSubject", taskTimeoutSubject);
        props.put("taskTimeoutBody", taskTimeoutBody);
        return props;
    }

    // Getters and Setters
    /**
     * Returns the workflow start subject.
     *
     * @return the result as a String
     */
    public String getWorkflowStartSubject() {
        return workflowStartSubject;
    }

    /**
     * Sets the workflow start subject.
     *
     * @param workflowStartSubject  workflowStartSubject value
     */
    public void setWorkflowStartSubject(String workflowStartSubject) {
        this.workflowStartSubject = workflowStartSubject;
    }

    /**
     * Returns the workflow start body.
     *
     * @return the result as a String
     */
    public String getWorkflowStartBody() {
        return workflowStartBody;
    }

    /**
     * Sets the workflow start body.
     *
     * @param workflowStartBody  workflowStartBody value
     */
    public void setWorkflowStartBody(String workflowStartBody) {
        this.workflowStartBody = workflowStartBody;
    }

    /**
     * Returns the workflow end subject.
     *
     * @return the result as a String
     */
    public String getWorkflowEndSubject() {
        return workflowEndSubject;
    }

    /**
     * Sets the workflow end subject.
     *
     * @param workflowEndSubject  workflowEndSubject value
     */
    public void setWorkflowEndSubject(String workflowEndSubject) {
        this.workflowEndSubject = workflowEndSubject;
    }

    /**
     * Returns the workflow end body.
     *
     * @return the result as a String
     */
    public String getWorkflowEndBody() {
        return workflowEndBody;
    }

    /**
     * Sets the workflow end body.
     *
     * @param workflowEndBody  workflowEndBody value
     */
    public void setWorkflowEndBody(String workflowEndBody) {
        this.workflowEndBody = workflowEndBody;
    }

    /**
     * Returns the workflow cancel subject.
     *
     * @return the result as a String
     */
    public String getWorkflowCancelSubject() {
        return workflowCancelSubject;
    }

    /**
     * Sets the workflow cancel subject.
     *
     * @param workflowCancelSubject  workflowCancelSubject value
     */
    public void setWorkflowCancelSubject(String workflowCancelSubject) {
        this.workflowCancelSubject = workflowCancelSubject;
    }

    /**
     * Returns the workflow cancel body.
     *
     * @return the result as a String
     */
    public String getWorkflowCancelBody() {
        return workflowCancelBody;
    }

    /**
     * Sets the workflow cancel body.
     *
     * @param workflowCancelBody  workflowCancelBody value
     */
    public void setWorkflowCancelBody(String workflowCancelBody) {
        this.workflowCancelBody = workflowCancelBody;
    }

    /**
     * Returns the workflow timeout subject.
     *
     * @return the result as a String
     */
    public String getWorkflowTimeoutSubject() {
        return workflowTimeoutSubject;
    }

    /**
     * Sets the workflow timeout subject.
     *
     * @param workflowTimeoutSubject  date/time value
     */
    public void setWorkflowTimeoutSubject(String workflowTimeoutSubject) {
        this.workflowTimeoutSubject = workflowTimeoutSubject;
    }

    /**
     * Returns the workflow timeout body.
     *
     * @return the result as a String
     */
    public String getWorkflowTimeoutBody() {
        return workflowTimeoutBody;
    }

    /**
     * Sets the workflow timeout body.
     *
     * @param workflowTimeoutBody  date/time value
     */
    public void setWorkflowTimeoutBody(String workflowTimeoutBody) {
        this.workflowTimeoutBody = workflowTimeoutBody;
    }

    /**
     * Returns the workflow error subject.
     *
     * @return the result as a String
     */
    public String getWorkflowErrorSubject() {
        return workflowErrorSubject;
    }

    /**
     * Sets the workflow error subject.
     *
     * @param workflowErrorSubject  workflowErrorSubject value
     */
    public void setWorkflowErrorSubject(String workflowErrorSubject) {
        this.workflowErrorSubject = workflowErrorSubject;
    }

    /**
     * Returns the workflow error body.
     *
     * @return the result as a String
     */
    public String getWorkflowErrorBody() {
        return workflowErrorBody;
    }

    /**
     * Sets the workflow error body.
     *
     * @param workflowErrorBody  workflowErrorBody value
     */
    public void setWorkflowErrorBody(String workflowErrorBody) {
        this.workflowErrorBody = workflowErrorBody;
    }

    /**
     * Returns the task error subject.
     *
     * @return the result as a String
     */
    public String getTaskErrorSubject() {
        return taskErrorSubject;
    }

    /**
     * Sets the task error subject.
     *
     * @param taskErrorSubject  taskErrorSubject value
     */
    public void setTaskErrorSubject(String taskErrorSubject) {
        this.taskErrorSubject = taskErrorSubject;
    }

    /**
     * Returns the task error body.
     *
     * @return the result as a String
     */
    public String getTaskErrorBody() {
        return taskErrorBody;
    }

    /**
     * Sets the task error body.
     *
     * @param taskErrorBody  taskErrorBody value
     */
    public void setTaskErrorBody(String taskErrorBody) {
        this.taskErrorBody = taskErrorBody;
    }

    /**
     * Returns the task cancel subject.
     *
     * @return the result as a String
     */
    public String getTaskCancelSubject() {
        return taskCancelSubject;
    }

    /**
     * Sets the task cancel subject.
     *
     * @param taskCancelSubject  taskCancelSubject value
     */
    public void setTaskCancelSubject(String taskCancelSubject) {
        this.taskCancelSubject = taskCancelSubject;
    }

    /**
     * Returns the task cancel body.
     *
     * @return the result as a String
     */
    public String getTaskCancelBody() {
        return taskCancelBody;
    }

    /**
     * Sets the task cancel body.
     *
     * @param taskCancelBody  taskCancelBody value
     */
    public void setTaskCancelBody(String taskCancelBody) {
        this.taskCancelBody = taskCancelBody;
    }

    /**
     * Returns the task timeout subject.
     *
     * @return the result as a String
     */
    public String getTaskTimeoutSubject() {
        return taskTimeoutSubject;
    }

    /**
     * Sets the task timeout subject.
     *
     * @param taskTimeoutSubject  date/time value
     */
    public void setTaskTimeoutSubject(String taskTimeoutSubject) {
        this.taskTimeoutSubject = taskTimeoutSubject;
    }

    /**
     * Returns the task timeout body.
     *
     * @return the result as a String
     */
    public String getTaskTimeoutBody() {
        return taskTimeoutBody;
    }

    /**
     * Sets the task timeout body.
     *
     * @param taskTimeoutBody  date/time value
     */
    public void setTaskTimeoutBody(String taskTimeoutBody) {
        this.taskTimeoutBody = taskTimeoutBody;
    }

    @Override
    /**
     * To the string.
     *
     * @return the result as a String
     */
    public String toString() {
        return "EmailTemplateConfig{" +
                "workflowStartSubject='" + workflowStartSubject + '\'' +
                ", workflowEndSubject='" + workflowEndSubject + '\'' +
                ", workflowCancelSubject='" + workflowCancelSubject + '\'' +
                ", workflowTimeoutSubject='" + workflowTimeoutSubject + '\'' +
                ", workflowErrorSubject='" + workflowErrorSubject + '\'' +
                ", taskErrorSubject='" + taskErrorSubject + '\'' +
                ", taskCancelSubject='" + taskCancelSubject + '\'' +
                ", taskTimeoutSubject='" + taskTimeoutSubject + '\'' +
                '}';
    }
}