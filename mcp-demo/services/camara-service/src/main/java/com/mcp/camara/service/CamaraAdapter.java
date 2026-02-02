package com.mcp.camara.service;

import com.mcp.common.model.CamaraLocationResponse;
import com.mcp.common.model.camara.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * CAMARA Adapter Service
 *
 * LAYER: CAMARA API
 *
 * Bridges MCP Server to CAMARA QoD API.
 * Supports two modes:
 * 1. REAL MODE: Uses actual CAMARA QoD API (when configured)
 * 2. MOCK MODE: Uses network-mock-service (fallback)
 */
@Service
public class CamaraAdapter {

    private static final Logger log = LoggerFactory.getLogger(CamaraAdapter.class);

    private final RestTemplate restTemplate;
    private final CamaraApiClient camaraClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${camara.api.default-phone:}")
    private String defaultPhoneNumber;

    @Value("${camara.api.default-duration:3600}")
    private int defaultDuration;

    @Value("${network-mock.base-url:http://localhost:8085}")
    private String networkMockBaseUrl;

    // Metrics
    private String lastEndpointCalled;
    private String lastRawResponse;
    private long lastLatencyMs;

    // Current session tracking
    private String currentSessionId;
    private SessionInfo currentSession;

    public CamaraAdapter(RestTemplate restTemplate, CamaraApiClient camaraClient) {
        this.restTemplate = restTemplate;
        this.camaraClient = camaraClient;
    }

    // ==================== MODE DETECTION ====================

    public boolean isRealCamaraConfigured() {
        return camaraClient.isConfigured();
    }

    public String getModeDescription() {
        if (isRealCamaraConfigured()) {
            return "REAL CAMARA API MODE";
        }
        return "MOCK MODE (CAMARA API not configured)";
    }

    // ==================== DEVICE LOCATION ====================

    public String getNetworkContext(String msisdn) {
        String url = networkMockBaseUrl + "/camara/device-location?msisdn=" + msisdn;
        this.lastEndpointCalled = url;

        long start = System.currentTimeMillis();
        CamaraLocationResponse response = restTemplate.getForObject(url, CamaraLocationResponse.class);
        this.lastLatencyMs = System.currentTimeMillis() - start;

        if (response == null) {
            this.lastRawResponse = "null";
            return "Network context unavailable";
        }

        try {
            this.lastRawResponse = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            this.lastRawResponse = response.toString();
        }

        return String.format(
                "Network Context from CAMARA API:\n" +
                        "- User Location: %s, %s\n" +
                        "- Roaming Status: %s\n" +
                        "- Location Verified: %s",
                response.getCity(),
                response.getCountry(),
                response.isRoaming() ? "Yes (international roaming)" : "No (home network)",
                response.isVerified() ? "Yes (network verified)" : "No");
    }

    public String getNetworkContext() {
        return getNetworkContext("+34612345678");
    }

    // ==================== QoD CONTEXT ====================

    public String getQodContext() {
        if (isRealCamaraConfigured()) {
            return getQodContextReal();
        }
        return getQodContextMock();
    }

    private String getQodContextReal() {
        StringBuilder context = new StringBuilder();
        context.append("QoD (Quality on Demand) Status - CAMARA API:\n");
        context.append("Mode: REAL CAMARA API\n\n");

        if (currentSessionId != null) {
            try {
                currentSession = camaraClient.getSession(currentSessionId);
                this.lastEndpointCalled = camaraClient.getLastEndpointCalled();
                this.lastLatencyMs = camaraClient.getLastLatencyMs();
                this.lastRawResponse = camaraClient.getLastRawResponse();

                context.append("ACTIVE SESSION:\n");
                context.append(String.format("- Session ID: %s\n", currentSession.getSessionId()));
                context.append(String.format("- QoS Profile: %s\n", currentSession.getQosProfile()));
                context.append(String.format("- Status: %s\n", currentSession.getQosStatus()));
                context.append(String.format("- Duration: %d seconds\n", currentSession.getDuration()));

                if (currentSession.getStartedAt() != null) {
                    context.append(String.format("- Started At: %s\n", currentSession.getStartedAt()));
                }
                if (currentSession.getExpiresAt() != null) {
                    context.append(String.format("- Expires At: %s\n", currentSession.getExpiresAt()));
                    context.append(String.format("- Remaining: %d seconds\n", currentSession.getRemainingSeconds()));
                }

                if (currentSession.getQosStatus() == QosStatus.UNAVAILABLE) {
                    context.append(String.format("- End Reason: %s\n", currentSession.getStatusInfo()));
                    currentSessionId = null;
                    currentSession = null;
                }

            } catch (Exception e) {
                log.warn("Failed to get session status: {}", e.getMessage());
                currentSessionId = null;
                currentSession = null;
                context.append("- Active Session: None (previous session may have expired)\n");
            }
        } else {
            context.append("- Active Session: None\n");
        }

        context.append("\nAVAILABLE QoS PROFILES:\n");
        context.append(QosProfile.getAllProfilesDescription());

        return context.toString();
    }

    @SuppressWarnings("unchecked")
    private String getQodContextMock() {
        String url = networkMockBaseUrl + "/camara/qod/status";
        this.lastEndpointCalled = url;

        long start = System.currentTimeMillis();
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        this.lastLatencyMs = System.currentTimeMillis() - start;

        if (response == null) {
            this.lastRawResponse = "null";
            return "QoD context unavailable";
        }

        try {
            this.lastRawResponse = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            this.lastRawResponse = response.toString();
        }

        StringBuilder context = new StringBuilder();
        context.append("QoD (Quality on Demand) Status - MOCK MODE:\n");
        context.append("Mode: MOCK (CAMARA API not configured)\n\n");
        context.append(String.format("- Current Bandwidth: %d Mbps\n", response.get("currentBandwidthMbps")));
        context.append(String.format("- Minimum Available: %d Mbps\n", response.get("minAvailableMbps")));
        context.append(String.format("- Maximum Available: %d Mbps\n", response.get("maxAvailableMbps")));
        context.append("\nNote: This is mock data. Configure CAMARA API for real network control.");

        return context.toString();
    }

    // ==================== QoS SESSION OPERATIONS ====================

    public SessionInfo createQosSession(String qosProfile, String phoneNumber, int duration) {
        if (!isRealCamaraConfigured()) {
            throw new IllegalStateException("CAMARA API is not configured. Cannot create real QoS session.");
        }

        String phone = (phoneNumber != null && !phoneNumber.isBlank()) ? phoneNumber : defaultPhoneNumber;
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException(
                    "Phone number is required. Set camara.api.default-phone or provide one.");
        }

        log.info("Creating QoS session: profile={}, phone={}, duration={}s", qosProfile, phone, duration);

        CreateSessionRequest request = new CreateSessionRequest()
                .device(Device.withPhoneNumber(phone))
                .applicationServer(ApplicationServer.allTraffic())
                .qosProfile(qosProfile)
                .duration(duration);

        SessionInfo session = camaraClient.createSession(request);

        this.currentSessionId = session.getSessionId();
        this.currentSession = session;
        this.lastEndpointCalled = camaraClient.getLastEndpointCalled();
        this.lastLatencyMs = camaraClient.getLastLatencyMs();
        this.lastRawResponse = camaraClient.getLastRawResponse();

        return session;
    }

    public SessionInfo createQosSession(String qosProfile) {
        return createQosSession(qosProfile, null, defaultDuration);
    }

    public SessionInfo waitForSessionAvailable(int maxWaitSeconds) throws InterruptedException {
        if (currentSessionId == null) {
            throw new IllegalStateException("No active session to wait for");
        }

        currentSession = camaraClient.waitForSessionAvailable(currentSessionId, maxWaitSeconds);
        this.lastEndpointCalled = camaraClient.getLastEndpointCalled();
        this.lastLatencyMs = camaraClient.getLastLatencyMs();
        this.lastRawResponse = camaraClient.getLastRawResponse();

        return currentSession;
    }

    public void endQosSession() {
        if (!isRealCamaraConfigured()) {
            throw new IllegalStateException("CAMARA API is not configured");
        }

        if (currentSessionId == null) {
            log.warn("No active session to end");
            return;
        }

        log.info("Ending QoS session: {}", currentSessionId);
        camaraClient.deleteSession(currentSessionId);

        this.lastEndpointCalled = camaraClient.getLastEndpointCalled();
        this.lastLatencyMs = camaraClient.getLastLatencyMs();
        this.lastRawResponse = camaraClient.getLastRawResponse();

        currentSessionId = null;
        currentSession = null;
    }

    public SessionInfo extendQosSession(int additionalSeconds) {
        if (!isRealCamaraConfigured()) {
            throw new IllegalStateException("CAMARA API is not configured");
        }

        if (currentSessionId == null) {
            throw new IllegalStateException("No active session to extend");
        }

        log.info("Extending session {} by {} seconds", currentSessionId, additionalSeconds);

        ExtendSessionRequest request = new ExtendSessionRequest(additionalSeconds);
        currentSession = camaraClient.extendSession(currentSessionId, request);

        this.lastEndpointCalled = camaraClient.getLastEndpointCalled();
        this.lastLatencyMs = camaraClient.getLastLatencyMs();
        this.lastRawResponse = camaraClient.getLastRawResponse();

        return currentSession;
    }

    public List<SessionInfo> getDeviceSessions(String phoneNumber) {
        if (!isRealCamaraConfigured()) {
            throw new IllegalStateException("CAMARA API is not configured");
        }

        RetrieveSessionsRequest request = new RetrieveSessionsRequest(Device.withPhoneNumber(phoneNumber));
        return camaraClient.retrieveSessions(request);
    }

    // ==================== MOCK MODE OPERATIONS ====================

    /**
     * Set bandwidth via network-mock service
     * Supports async notification by passing notificationUrl and taskId
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> setBandwidth(int bandwidthMbps, String notificationUrl, String taskId) {
        if (isRealCamaraConfigured()) {
            log.warn("setBandwidth called in REAL mode - use createQosSession instead");
            throw new IllegalStateException("Use createQosSession for real CAMARA API");
        }

        String url = networkMockBaseUrl + "/camara/qod/set-bandwidth";
        this.lastEndpointCalled = url;

        // Build request with optional notification params
        java.util.Map<String, Object> request = new java.util.HashMap<>();
        request.put("bandwidthMbps", bandwidthMbps);
        if (notificationUrl != null && taskId != null) {
            request.put("notificationUrl", notificationUrl);
            request.put("taskId", taskId);
            log.info("setBandwidth with async notification: taskId={}", taskId);
        }

        long start = System.currentTimeMillis();
        Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
        this.lastLatencyMs = System.currentTimeMillis() - start;

        try {
            this.lastRawResponse = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            this.lastRawResponse = response != null ? response.toString() : "null";
        }

        return response;
    }

    /**
     * Set bandwidth (simple overload without async notification)
     */
    public Map<String, Object> setBandwidth(int bandwidthMbps) {
        return setBandwidth(bandwidthMbps, null, null);
    }

    // ==================== SESSION STATE GETTERS ====================

    public String getCurrentSessionId() {
        return currentSessionId;
    }

    public SessionInfo getCurrentSession() {
        return currentSession;
    }

    public boolean hasActiveSession() {
        return currentSessionId != null && currentSession != null
                && currentSession.getQosStatus() != QosStatus.UNAVAILABLE;
    }

    // ==================== METRICS GETTERS ====================

    public String getLastEndpointCalled() {
        return lastEndpointCalled;
    }

    public String getLastRawResponse() {
        return lastRawResponse;
    }

    public long getLastLatencyMs() {
        return lastLatencyMs;
    }
}
