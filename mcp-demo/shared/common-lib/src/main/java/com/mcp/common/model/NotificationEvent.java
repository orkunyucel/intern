package com.mcp.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Notification Event Model (CloudEvents-inspired format)
 *
 * Represents a notification sent from downstream services (CAMARA/Network)
 * back to MCP Server via webhook.
 *
 * Based on CAMARA QoD notification format:
 * - type: "org.camaraproject.qod.v1.session-available"
 * - source: "/sessions/{sessionId}"
 * - data: {sessionId, qosStatus, statusInfo}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationEvent {

    // CloudEvents standard fields
    private String id;
    private String source; // e.g. "/sessions/abc123"
    private String type; // e.g. "org.camaraproject.qod.v1.session-available"
    private String specversion; // "1.0"
    private Instant time;

    // Custom fields for MCP integration
    private String taskId; // Links back to AsyncTask
    private String sessionId; // CAMARA session ID
    private Map<String, Object> data; // Event-specific payload

    public NotificationEvent() {
        this.id = UUID.randomUUID().toString();
        this.specversion = "1.0";
        this.time = Instant.now();
    }

    // Factory methods for common event types
    public static NotificationEvent sessionAvailable(String taskId, String sessionId, Map<String, Object> data) {
        NotificationEvent event = new NotificationEvent();
        event.setTaskId(taskId);
        event.setSessionId(sessionId);
        event.setType("org.camaraproject.qod.v1.session-available");
        event.setSource("/sessions/" + sessionId);
        event.setData(data);
        return event;
    }

    public static NotificationEvent sessionUnavailable(String taskId, String sessionId, String reason) {
        NotificationEvent event = new NotificationEvent();
        event.setTaskId(taskId);
        event.setSessionId(sessionId);
        event.setType("org.camaraproject.qod.v1.session-unavailable");
        event.setSource("/sessions/" + sessionId);
        event.setData(Map.of("reason", reason));
        return event;
    }

    public static NotificationEvent bandwidthChanged(String taskId, int oldBandwidth, int newBandwidth) {
        NotificationEvent event = new NotificationEvent();
        event.setTaskId(taskId);
        event.setType("org.camaraproject.qod.v1.bandwidth-changed");
        event.setSource("/qod/bandwidth");
        event.setData(Map.of(
                "previousBandwidthMbps", oldBandwidth,
                "newBandwidthMbps", newBandwidth,
                "status", "AVAILABLE"));
        return event;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSpecversion() {
        return specversion;
    }

    public void setSpecversion(String specversion) {
        this.specversion = specversion;
    }

    public Instant getTime() {
        return time;
    }

    public void setTime(Instant time) {
        this.time = time;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return String.format("NotificationEvent{id='%s', type='%s', taskId='%s', sessionId='%s'}",
                id, type, taskId, sessionId);
    }
}
