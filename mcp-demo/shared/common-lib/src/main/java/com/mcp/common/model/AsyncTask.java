package com.mcp.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Async Task Tracking Model
 *
 * Used to track asynchronous operations like QoS session creation.
 * When a tool call is async, the immediate response contains a taskId
 * and status "REQUESTED". Updates come via webhook notifications.
 *
 * Status Flow:
 * REQUESTED → WORKING → AVAILABLE (success)
 * → FAILED (error)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AsyncTask {

    public enum Status {
        REQUESTED, // Initial state - task accepted
        WORKING, // Network is processing
        AVAILABLE, // Task completed successfully
        FAILED // Task failed
    }

    private String taskId;
    private Status status;
    private String event; // e.g. "SUCCESSFUL_RESOURCES_ALLOCATION"
    private String toolName; // Original tool that started this task
    private Map<String, Object> arguments; // Original tool arguments
    private Map<String, Object> result; // Final result when completed
    private String errorMessage; // Error message if failed
    private String notificationUrl; // Callback URL for this task
    private Instant createdAt;
    private Instant updatedAt;

    public AsyncTask() {
        this.taskId = UUID.randomUUID().toString();
        this.status = Status.REQUESTED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public AsyncTask(String toolName, Map<String, Object> arguments, String notificationUrl) {
        this();
        this.toolName = toolName;
        this.arguments = arguments;
        this.notificationUrl = notificationUrl;
    }

    // Status updates
    public void markWorking() {
        this.status = Status.WORKING;
        this.event = "RESOURCES_ALLOCATION_IN_PROGRESS";
        this.updatedAt = Instant.now();
    }

    public void markAvailable(Map<String, Object> result) {
        this.status = Status.AVAILABLE;
        this.event = "SUCCESSFUL_RESOURCES_ALLOCATION";
        this.result = result;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = Status.FAILED;
        this.event = "RESOURCES_ALLOCATION_FAILED";
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getNotificationUrl() {
        return notificationUrl;
    }

    public void setNotificationUrl(String notificationUrl) {
        this.notificationUrl = notificationUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return String.format("AsyncTask{taskId='%s', status=%s, event='%s', toolName='%s'}",
                taskId, status, event, toolName);
    }
}
