package com.mcp.network.service;

import com.mcp.common.model.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Notification Sender Service
 *
 * Sends async notifications (webhooks) back to MCP Server
 * after network operations complete.
 *
 * Simulates the telco network notification delay.
 */
@Service
public class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    private final RestTemplate restTemplate;

    // Pending notifications by taskId
    private final ConcurrentHashMap<String, NotificationEvent> pendingNotifications = new ConcurrentHashMap<>();

    // Default delay to simulate network processing time (ms)
    private static final long DEFAULT_DELAY_MS = 2000;

    public NotificationSender(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Schedule a notification to be sent after a delay
     * Simulates async network operation completion
     */
    @Async
    public void sendNotificationAfterDelay(String notificationUrl, String taskId,
            String type, Map<String, Object> data, long delayMs) {
        log.info("Scheduling notification for taskId: {} after {}ms", taskId, delayMs);

        try {
            // Simulate network processing delay
            Thread.sleep(delayMs);

            NotificationEvent event = new NotificationEvent();
            event.setTaskId(taskId);
            event.setType(type);
            event.setData(data);

            if (data.containsKey("sessionId")) {
                event.setSessionId((String) data.get("sessionId"));
                event.setSource("/sessions/" + data.get("sessionId"));
            } else {
                event.setSource("/qod/bandwidth");
            }

            // Send webhook to MCP Server
            log.info("Sending notification to {}: type={}, taskId={}", notificationUrl, type, taskId);

            try {
                restTemplate.postForObject(notificationUrl, event, Map.class);
                log.info("Notification sent successfully for taskId: {}", taskId);
            } catch (Exception e) {
                log.error("Failed to send notification to {}: {}", notificationUrl, e.getMessage());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Notification delay interrupted for taskId: {}", taskId);
        }
    }

    /**
     * Send bandwidth change notification
     */
    public void sendBandwidthNotification(String notificationUrl, String taskId,
            int oldBandwidth, int newBandwidth) {
        Map<String, Object> data = Map.of(
                "previousBandwidthMbps", oldBandwidth,
                "newBandwidthMbps", newBandwidth,
                "status", "AVAILABLE");

        sendNotificationAfterDelay(
                notificationUrl,
                taskId,
                "org.camaraproject.qod.v1.bandwidth-changed",
                data,
                DEFAULT_DELAY_MS);
    }

    /**
     * Send session created notification
     */
    public void sendSessionCreatedNotification(String notificationUrl, String taskId,
            String sessionId, String qosProfile) {
        Map<String, Object> data = Map.of(
                "sessionId", sessionId,
                "qosProfile", qosProfile,
                "qosStatus", "AVAILABLE");

        sendNotificationAfterDelay(
                notificationUrl,
                taskId,
                "org.camaraproject.qod.v1.session-available",
                data,
                DEFAULT_DELAY_MS);
    }

    /**
     * Send session failed notification
     */
    public void sendSessionFailedNotification(String notificationUrl, String taskId,
            String reason) {
        Map<String, Object> data = Map.of(
                "reason", reason,
                "status", "FAILED");

        sendNotificationAfterDelay(
                notificationUrl,
                taskId,
                "org.camaraproject.qod.v1.session-unavailable",
                data,
                500); // Failures are reported faster
    }
}
