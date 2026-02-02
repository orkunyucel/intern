package com.mcp.common.model.camara;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * QoS Session information as per CAMARA QoD API v1.1.0
 *
 * LAYER: CAMARA API (Model)
 *
 * Response body for:
 * - POST /sessions (201 Created)
 * - GET /sessions/{sessionId}
 * - POST /sessions/{sessionId}/extend
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionInfo {

    private String sessionId;
    private Device device;
    private ApplicationServer applicationServer;
    private PortsSpec devicePorts;
    private PortsSpec applicationServerPorts;
    private String qosProfile;
    private QosStatus qosStatus;
    private StatusInfo statusInfo;
    private Integer duration;
    private Instant startedAt;
    private Instant expiresAt;
    private String sink;

    public SessionInfo() {
    }

    // Helper methods

    public boolean isActive() {
        return qosStatus == QosStatus.AVAILABLE;
    }

    public boolean isPending() {
        return qosStatus == QosStatus.REQUESTED;
    }

    public boolean isEnded() {
        return qosStatus == QosStatus.UNAVAILABLE;
    }

    public long getRemainingSeconds() {
        if (expiresAt == null) return 0;
        long remaining = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }

    // Getters and Setters

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        this.device = device;
    }

    public ApplicationServer getApplicationServer() {
        return applicationServer;
    }

    public void setApplicationServer(ApplicationServer applicationServer) {
        this.applicationServer = applicationServer;
    }

    public PortsSpec getDevicePorts() {
        return devicePorts;
    }

    public void setDevicePorts(PortsSpec devicePorts) {
        this.devicePorts = devicePorts;
    }

    public PortsSpec getApplicationServerPorts() {
        return applicationServerPorts;
    }

    public void setApplicationServerPorts(PortsSpec applicationServerPorts) {
        this.applicationServerPorts = applicationServerPorts;
    }

    public String getQosProfile() {
        return qosProfile;
    }

    public void setQosProfile(String qosProfile) {
        this.qosProfile = qosProfile;
    }

    public QosStatus getQosStatus() {
        return qosStatus;
    }

    public void setQosStatus(QosStatus qosStatus) {
        this.qosStatus = qosStatus;
    }

    public StatusInfo getStatusInfo() {
        return statusInfo;
    }

    public void setStatusInfo(StatusInfo statusInfo) {
        this.statusInfo = statusInfo;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getSink() {
        return sink;
    }

    public void setSink(String sink) {
        this.sink = sink;
    }

    @Override
    public String toString() {
        return String.format("SessionInfo{sessionId='%s', qosProfile='%s', qosStatus=%s, duration=%d}",
            sessionId, qosProfile, qosStatus, duration);
    }
}
