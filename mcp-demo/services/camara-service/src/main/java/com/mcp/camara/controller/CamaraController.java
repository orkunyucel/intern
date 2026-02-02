package com.mcp.camara.controller;

import com.mcp.camara.service.CamaraAdapter;
import com.mcp.common.model.camara.SessionInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * CAMARA Service REST Controller
 *
 * LAYER: CAMARA API
 *
 * Exposes CAMARA operations as REST endpoints for MCP Server.
 */
@RestController
@RequestMapping("/api/camara")
public class CamaraController {

    private final CamaraAdapter camaraAdapter;

    public CamaraController(CamaraAdapter camaraAdapter) {
        this.camaraAdapter = camaraAdapter;
    }

    // ==================== NETWORK CONTEXT ====================

    @GetMapping("/network-context")
    public ResponseEntity<Map<String, Object>> getNetworkContext(
            @RequestParam(required = false, defaultValue = "+34612345678") String msisdn) {
        String context = camaraAdapter.getNetworkContext(msisdn);
        return ResponseEntity.ok(Map.of(
                "context", context,
                "mode", camaraAdapter.getModeDescription()));
    }

    // ==================== QoD CONTEXT ====================

    @GetMapping("/qod-context")
    public ResponseEntity<Map<String, Object>> getQodContext() {
        String context = camaraAdapter.getQodContext();
        return ResponseEntity.ok(Map.of(
                "context", context,
                "mode", camaraAdapter.getModeDescription(),
                "hasActiveSession", camaraAdapter.hasActiveSession()));
    }

    // ==================== QoS SESSION OPERATIONS ====================

    @PostMapping("/session")
    public ResponseEntity<Map<String, Object>> createSession(@RequestBody Map<String, Object> request) {
        String qosProfile = (String) request.getOrDefault("qosProfile", "QOS_M");
        String phoneNumber = (String) request.get("phoneNumber");
        int duration = request.containsKey("duration")
                ? ((Number) request.get("duration")).intValue()
                : 3600;

        try {
            SessionInfo session = camaraAdapter.createQosSession(qosProfile, phoneNumber, duration);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "sessionId", session.getSessionId(),
                    "qosProfile", session.getQosProfile(),
                    "qosStatus", session.getQosStatus().name(),
                    "duration", session.getDuration()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> endSession(@PathVariable String sessionId) {
        try {
            camaraAdapter.endQosSession();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Session ended: " + sessionId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    @PostMapping("/session/{sessionId}/extend")
    public ResponseEntity<Map<String, Object>> extendSession(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request) {
        int additionalSeconds = request.containsKey("additionalSeconds")
                ? ((Number) request.get("additionalSeconds")).intValue()
                : 1800;

        try {
            SessionInfo session = camaraAdapter.extendQosSession(additionalSeconds);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "sessionId", session.getSessionId(),
                    "newDuration", session.getDuration(),
                    "expiresAt", session.getExpiresAt() != null ? session.getExpiresAt().toString() : "unknown"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    // ==================== MOCK MODE: BANDWIDTH ====================

    @PostMapping("/bandwidth")
    public ResponseEntity<Map<String, Object>> setBandwidth(@RequestBody Map<String, Object> request) {
        int bandwidthMbps = request.containsKey("bandwidthMbps")
                ? ((Number) request.get("bandwidthMbps")).intValue()
                : 500;

        // Extract async notification params
        String notificationUrl = (String) request.get("notificationUrl");
        String taskId = (String) request.get("taskId");

        try {
            Map<String, Object> result = camaraAdapter.setBandwidth(bandwidthMbps, notificationUrl, taskId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    // ==================== STATUS ====================

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "mode", camaraAdapter.getModeDescription(),
                "realCamaraConfigured", camaraAdapter.isRealCamaraConfigured(),
                "hasActiveSession", camaraAdapter.hasActiveSession(),
                "currentSessionId", camaraAdapter.getCurrentSessionId() != null
                        ? camaraAdapter.getCurrentSessionId()
                        : "none"));
    }

    // ==================== HEALTH ====================

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "camara-service",
                "port", 8084,
                "mode", camaraAdapter.getModeDescription()));
    }
}
