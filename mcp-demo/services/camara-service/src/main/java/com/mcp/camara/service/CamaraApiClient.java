package com.mcp.camara.service;

import com.mcp.common.model.camara.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * CAMARA QoD API Client
 *
 * LAYER: CAMARA API
 *
 * Implements the CAMARA Quality on Demand API v1.1.0 specification.
 * Handles OAuth 2.0 authentication and all QoS session operations.
 *
 * Configuration required in application.yml:
 * - camara.api.base-url
 * - camara.api.token-url
 * - camara.api.client-id
 * - camara.api.client-secret
 *
 * @see https://github.com/camaraproject/QualityOnDemand
 */
@Component
public class CamaraApiClient {

    private static final Logger log = LoggerFactory.getLogger(CamaraApiClient.class);

    private final RestTemplate restTemplate;

    // ==================== CONFIGURATION ====================

    @Value("${camara.api.base-url:}")
    private String baseUrl;

    @Value("${camara.api.token-url:}")
    private String tokenUrl;

    @Value("${camara.api.client-id:}")
    private String clientId;

    @Value("${camara.api.client-secret:}")
    private String clientSecret;

    @Value("${camara.api.scope:qod:sessions:create qod:sessions:read qod:sessions:delete}")
    private String scope;

    // Token caching
    private String cachedToken;
    private long tokenExpiry;

    // Metrics
    private long lastLatencyMs;
    private String lastEndpointCalled;
    private String lastRawResponse;

    public CamaraApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ==================== CONFIGURATION CHECK ====================

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank()
            && tokenUrl != null && !tokenUrl.isBlank()
            && clientId != null && !clientId.isBlank()
            && clientSecret != null && !clientSecret.isBlank();
    }

    public String getConfigurationStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("CAMARA API Configuration:\n");
        sb.append("- Base URL: ").append(isSet(baseUrl) ? baseUrl : "NOT SET").append("\n");
        sb.append("- Token URL: ").append(isSet(tokenUrl) ? tokenUrl : "NOT SET").append("\n");
        sb.append("- Client ID: ").append(isSet(clientId) ? "SET (hidden)" : "NOT SET").append("\n");
        sb.append("- Client Secret: ").append(isSet(clientSecret) ? "SET (hidden)" : "NOT SET").append("\n");
        sb.append("- Configured: ").append(isConfigured() ? "YES" : "NO");
        return sb.toString();
    }

    private boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    // ==================== OAUTH 2.0 AUTHENTICATION ====================

    private String getAccessToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiry - 60000) {
            return cachedToken;
        }

        if (!isConfigured()) {
            throw new IllegalStateException("CAMARA API is not configured. Please set camara.api.* properties.");
        }

        log.info("Requesting new OAuth token from: {}", tokenUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        String body = "grant_type=client_credentials&scope=" + scope;

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                tokenUrl, request, TokenResponse.class);

            TokenResponse token = response.getBody();
            if (token == null || token.getAccessToken() == null) {
                throw new RuntimeException("Empty token response from OAuth server");
            }

            cachedToken = token.getAccessToken();
            int expiresIn = token.getExpiresIn() != null ? token.getExpiresIn() : 3600;
            tokenExpiry = System.currentTimeMillis() + (expiresIn * 1000L);

            log.info("OAuth token obtained successfully, expires in {} seconds", expiresIn);
            return cachedToken;

        } catch (HttpClientErrorException e) {
            log.error("OAuth token request failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to obtain OAuth token: " + e.getMessage(), e);
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getAccessToken());
        headers.set("x-correlator", UUID.randomUUID().toString());
        return headers;
    }

    // ==================== QoS SESSION OPERATIONS ====================

    public SessionInfo createSession(CreateSessionRequest request) {
        if (!isConfigured()) {
            throw new IllegalStateException("CAMARA API is not configured");
        }

        String url = baseUrl + "/sessions";
        this.lastEndpointCalled = url;

        log.info("Creating QoS session: profile={}, duration={}s",
            request.getQosProfile(), request.getDuration());

        HttpEntity<CreateSessionRequest> entity = new HttpEntity<>(request, createHeaders());

        long start = System.currentTimeMillis();
        try {
            ResponseEntity<SessionInfo> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, SessionInfo.class);

            this.lastLatencyMs = System.currentTimeMillis() - start;
            SessionInfo session = response.getBody();
            this.lastRawResponse = session != null ? session.toString() : "null";

            log.info("Session created: id={}, status={}, latency={}ms",
                session != null ? session.getSessionId() : "null",
                session != null ? session.getQosStatus() : "null",
                lastLatencyMs);

            return session;

        } catch (HttpClientErrorException e) {
            this.lastLatencyMs = System.currentTimeMillis() - start;
            this.lastRawResponse = e.getResponseBodyAsString();
            log.error("Create session failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to create QoS session: " + e.getMessage(), e);
        }
    }

    public SessionInfo getSession(String sessionId) {
        if (!isConfigured()) {
            throw new IllegalStateException("CAMARA API is not configured");
        }

        String url = baseUrl + "/sessions/" + sessionId;
        this.lastEndpointCalled = url;

        HttpEntity<?> entity = new HttpEntity<>(createHeaders());

        long start = System.currentTimeMillis();
        try {
            ResponseEntity<SessionInfo> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, SessionInfo.class);

            this.lastLatencyMs = System.currentTimeMillis() - start;
            SessionInfo session = response.getBody();
            this.lastRawResponse = session != null ? session.toString() : "null";

            return session;

        } catch (HttpClientErrorException e) {
            this.lastLatencyMs = System.currentTimeMillis() - start;
            this.lastRawResponse = e.getResponseBodyAsString();
            log.error("Get session failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to get QoS session: " + e.getMessage(), e);
        }
    }

    public void deleteSession(String sessionId) {
        if (!isConfigured()) {
            throw new IllegalStateException("CAMARA API is not configured");
        }

        String url = baseUrl + "/sessions/" + sessionId;
        this.lastEndpointCalled = url;

        log.info("Deleting QoS session: {}", sessionId);

        HttpEntity<?> entity = new HttpEntity<>(createHeaders());

        long start = System.currentTimeMillis();
        try {
            restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);

            this.lastLatencyMs = System.currentTimeMillis() - start;
            this.lastRawResponse = "204 No Content";

            log.info("Session deleted successfully: {}, latency={}ms", sessionId, lastLatencyMs);

        } catch (HttpClientErrorException e) {
            this.lastLatencyMs = System.currentTimeMillis() - start;
            this.lastRawResponse = e.getResponseBodyAsString();
            log.error("Delete session failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to delete QoS session: " + e.getMessage(), e);
        }
    }

    public SessionInfo extendSession(String sessionId, ExtendSessionRequest request) {
        if (!isConfigured()) {
            throw new IllegalStateException("CAMARA API is not configured");
        }

        String url = baseUrl + "/sessions/" + sessionId + "/extend";
        this.lastEndpointCalled = url;

        log.info("Extending session {} by {} seconds", sessionId, request.getRequestedAdditionalDuration());

        HttpEntity<ExtendSessionRequest> entity = new HttpEntity<>(request, createHeaders());

        long start = System.currentTimeMillis();
        try {
            ResponseEntity<SessionInfo> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, SessionInfo.class);

            this.lastLatencyMs = System.currentTimeMillis() - start;
            SessionInfo session = response.getBody();
            this.lastRawResponse = session != null ? session.toString() : "null";

            log.info("Session extended: {}, new expiry={}",
                sessionId, session != null ? session.getExpiresAt() : "null");

            return session;

        } catch (HttpClientErrorException e) {
            this.lastLatencyMs = System.currentTimeMillis() - start;
            this.lastRawResponse = e.getResponseBodyAsString();
            log.error("Extend session failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to extend QoS session: " + e.getMessage(), e);
        }
    }

    public List<SessionInfo> retrieveSessions(RetrieveSessionsRequest request) {
        if (!isConfigured()) {
            throw new IllegalStateException("CAMARA API is not configured");
        }

        String url = baseUrl + "/retrieve-sessions";
        this.lastEndpointCalled = url;

        HttpEntity<RetrieveSessionsRequest> entity = new HttpEntity<>(request, createHeaders());

        long start = System.currentTimeMillis();
        try {
            ResponseEntity<SessionInfo[]> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, SessionInfo[].class);

            this.lastLatencyMs = System.currentTimeMillis() - start;
            SessionInfo[] sessions = response.getBody();
            this.lastRawResponse = sessions != null ? "Found " + sessions.length + " sessions" : "null";

            return sessions != null ? List.of(sessions) : List.of();

        } catch (HttpClientErrorException e) {
            this.lastLatencyMs = System.currentTimeMillis() - start;
            this.lastRawResponse = e.getResponseBodyAsString();
            log.error("Retrieve sessions failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to retrieve sessions: " + e.getMessage(), e);
        }
    }

    // ==================== POLLING HELPER ====================

    public SessionInfo waitForSessionAvailable(String sessionId, int maxWaitSeconds, int pollIntervalSeconds)
            throws InterruptedException {

        log.info("Waiting for session {} to become AVAILABLE (max {}s)", sessionId, maxWaitSeconds);

        int waited = 0;

        while (waited < maxWaitSeconds) {
            SessionInfo session = getSession(sessionId);

            if (session.getQosStatus() == QosStatus.AVAILABLE) {
                log.info("Session {} is now AVAILABLE after {}s", sessionId, waited);
                return session;
            }

            if (session.getQosStatus() == QosStatus.UNAVAILABLE) {
                log.warn("Session {} became UNAVAILABLE: {}", sessionId, session.getStatusInfo());
                throw new RuntimeException("Session became unavailable: " + session.getStatusInfo());
            }

            log.debug("Session {} still REQUESTED, waiting... ({}/{}s)", sessionId, waited, maxWaitSeconds);
            Thread.sleep(pollIntervalSeconds * 1000L);
            waited += pollIntervalSeconds;
        }

        throw new RuntimeException("Timeout waiting for session " + sessionId + " to become AVAILABLE");
    }

    public SessionInfo waitForSessionAvailable(String sessionId, int maxWaitSeconds)
            throws InterruptedException {
        return waitForSessionAvailable(sessionId, maxWaitSeconds, 2);
    }

    // ==================== METRICS GETTERS ====================

    public long getLastLatencyMs() {
        return lastLatencyMs;
    }

    public String getLastEndpointCalled() {
        return lastEndpointCalled;
    }

    public String getLastRawResponse() {
        return lastRawResponse;
    }
}
