package com.mcp.network.controller;

import com.mcp.common.model.CamaraLocationResponse;
import com.mcp.network.service.NotificationSender;
import com.mcp.network.service.QodState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Mock CAMARA API - Simulates telco network API
 *
 * LAYER: NETWORK INFRASTRUCTURE (Mock)
 *
 * In real scenario, this would be actual CAMARA API from network provider.
 *
 * Includes:
 * - Device Location API (for reasoning mode)
 * - QoD (Quality on Demand) API (for action mode)
 *
 * Supports async notifications via notificationUrl parameter.
 */
@RestController
@RequestMapping("/camara")
public class NetworkMockController {

    private static final Logger log = LoggerFactory.getLogger(NetworkMockController.class);

    private final QodState qodState;
    private final NotificationSender notificationSender;

    public NetworkMockController(QodState qodState, NotificationSender notificationSender) {
        this.qodState = qodState;
        this.notificationSender = notificationSender;
    }

    // ==================== DEVICE LOCATION API ====================

    @GetMapping("/device-location")
    public CamaraLocationResponse getDeviceLocation(
            @RequestParam(required = false, defaultValue = "+34612345678") String msisdn) {

        // Mock response - simulating Barcelona roaming scenario
        CamaraLocationResponse response = new CamaraLocationResponse();
        response.setCountry("Spain");
        response.setCity("Barcelona");
        response.setRoaming(true);
        response.setVerified(true);

        return response;
    }

    // ==================== QoD (Quality on Demand) API ====================

    /**
     * Get current QoD status - bandwidth information
     */
    @GetMapping("/qod/status")
    public Map<String, Object> getQodStatus() {
        return Map.of(
                "currentBandwidthMbps", qodState.getBandwidth(),
                "maxAvailableMbps", qodState.getMaxBandwidth(),
                "minAvailableMbps", qodState.getMinBandwidth(),
                "unit", "Mbps");
    }

    /**
     * Set new bandwidth - QoD action endpoint
     * Supports async notification via notificationUrl and taskId parameters
     */
    @PostMapping("/qod/set-bandwidth")
    public Map<String, Object> setBandwidth(@RequestBody Map<String, Object> request) {
        int requestedBandwidth = request.containsKey("bandwidthMbps")
                ? ((Number) request.get("bandwidthMbps")).intValue()
                : qodState.getBandwidth();
        int oldBandwidth = qodState.setBandwidth(requestedBandwidth);

        // Check for async notification
        String notificationUrl = (String) request.get("notificationUrl");
        String taskId = (String) request.get("taskId");

        if (notificationUrl != null && taskId != null) {
            log.info("Scheduling async notification for bandwidth change: taskId={}", taskId);
            notificationSender.sendBandwidthNotification(
                    notificationUrl, taskId, oldBandwidth, qodState.getBandwidth());
        }

        return Map.of(
                "success", true,
                "previousBandwidthMbps", oldBandwidth,
                "newBandwidthMbps", qodState.getBandwidth(),
                "message", "Bandwidth updated successfully",
                "async", notificationUrl != null);
    }

    /**
     * Reset QoD state to default
     */
    @PostMapping("/qod/reset")
    public Map<String, Object> resetQod() {
        int oldBandwidth = qodState.getBandwidth();
        qodState.reset();

        return Map.of(
                "success", true,
                "previousBandwidthMbps", oldBandwidth,
                "newBandwidthMbps", qodState.getBandwidth(),
                "message", "QoD state reset to default");
    }

    // ==================== HEALTH CHECK ====================

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "network-mock-service",
                "port", 8085,
                "currentBandwidth", qodState.getBandwidth());
    }
}
