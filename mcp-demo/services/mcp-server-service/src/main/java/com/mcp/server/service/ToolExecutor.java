package com.mcp.server.service;

import com.mcp.common.protocol.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * MCP Tool Executor
 *
 * Executes tools by calling the CAMARA service.
 * Acts as a bridge between MCP protocol and CAMARA operations.
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final ToolRegistry toolRegistry;
    private final RestTemplate restTemplate;

    @Value("${camara-service.base-url:http://localhost:8084}")
    private String camaraServiceBaseUrl;

    private static final List<String> VALID_PROFILES = List.of("QOS_S", "QOS_M", "QOS_L", "QOS_E");

    public ToolExecutor(ToolRegistry toolRegistry, RestTemplate restTemplate) {
        this.toolRegistry = toolRegistry;
        this.restTemplate = restTemplate;
    }

    /**
     * Execute a tool by name with given arguments
     */
    public ToolResult execute(String toolName, Map<String, Object> arguments) {
        log.info("Executing tool: {} with arguments: {}", toolName, arguments);

        if (!toolRegistry.hasTool(toolName)) {
            log.warn("Tool not found: {}", toolName);
            return ToolResult.error("Tool not found: " + toolName);
        }

        try {
            return switch (toolName) {
                case "get_network_context" -> executeGetNetworkContext(arguments);
                case "get_qod_context" -> executeGetQodContext(arguments);
                case "create_qos_session" -> executeCreateQosSession(arguments);
                case "end_qos_session" -> executeEndQosSession(arguments);
                case "extend_qos_session" -> executeExtendQosSession(arguments);
                case "set_bandwidth" -> executeSetBandwidth(arguments);
                default -> ToolResult.error("Unknown tool: " + toolName);
            };
        } catch (Exception e) {
            log.error("Tool execution failed: {} - {}", toolName, e.getMessage(), e);
            return ToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private ToolResult executeGetNetworkContext(Map<String, Object> arguments) {
        String msisdn = getStringArg(arguments, "msisdn", "+34612345678");
        String url = camaraServiceBaseUrl + "/api/camara/network-context?msisdn=" + msisdn;

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        String context = response != null ? (String) response.get("context") : "Network context unavailable";

        log.info("get_network_context result: {} chars", context.length());
        return ToolResult.text(context);
    }

    @SuppressWarnings("unchecked")
    private ToolResult executeGetQodContext(Map<String, Object> arguments) {
        String url = camaraServiceBaseUrl + "/api/camara/qod-context";

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        String context = response != null ? (String) response.get("context") : "QoD context unavailable";

        log.info("get_qod_context result: {} chars", context.length());
        return ToolResult.text(context);
    }

    @SuppressWarnings("unchecked")
    private ToolResult executeCreateQosSession(Map<String, Object> arguments) {
        String qosProfile = getStringArg(arguments, "qosProfile", "QOS_M");
        int duration = getIntArg(arguments, "duration", 3600);
        String phoneNumber = getStringArg(arguments, "phoneNumber", null);

        // Validation
        if (!VALID_PROFILES.contains(qosProfile)) {
            return ToolResult.error("Invalid qosProfile: " + qosProfile + ". Valid values: " + VALID_PROFILES);
        }

        if (duration <= 0) {
            return ToolResult.error("Invalid duration: " + duration + ". Must be positive.");
        }

        String url = camaraServiceBaseUrl + "/api/camara/session";

        Map<String, Object> request = new java.util.HashMap<>();
        request.put("qosProfile", qosProfile);
        request.put("duration", duration);
        if (phoneNumber != null) {
            request.put("phoneNumber", phoneNumber);
        }

        try {
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                String result = String.format(
                        "QoS Session oluşturuldu!\n\n" +
                                "Session ID: %s\n" +
                                "Profil: %s\n" +
                                "Durum: %s\n" +
                                "Süre: %d saniye",
                        response.get("sessionId"),
                        response.get("qosProfile"),
                        response.get("qosStatus"),
                        response.get("duration"));

                log.info("create_qos_session: Session created with ID {}", response.get("sessionId"));
                return ToolResult.text(result);
            } else {
                String error = response != null ? (String) response.get("error") : "Unknown error";
                return ToolResult.error("QoS session oluşturulamadı: " + error);
            }
        } catch (Exception e) {
            return ToolResult.error("CAMARA API yapılandırılmamış veya hata oluştu: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private ToolResult executeEndQosSession(Map<String, Object> arguments) {
        // First get current session
        String statusUrl = camaraServiceBaseUrl + "/api/camara/status";
        Map<String, Object> status = restTemplate.getForObject(statusUrl, Map.class);

        if (status == null || !Boolean.TRUE.equals(status.get("hasActiveSession"))) {
            return ToolResult.text("Aktif bir QoS session bulunmuyor.");
        }

        String sessionId = (String) status.get("currentSessionId");
        String url = camaraServiceBaseUrl + "/api/camara/session/" + sessionId;

        try {
            restTemplate.delete(url);

            String result = String.format(
                    "QoS Session sonlandırıldı!\n\n" +
                            "Session ID: %s\n" +
                            "Durum: Sonlandırıldı",
                    sessionId);

            log.info("end_qos_session: Session {} ended", sessionId);
            return ToolResult.text(result);
        } catch (Exception e) {
            return ToolResult.error("Session sonlandırılamadı: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private ToolResult executeExtendQosSession(Map<String, Object> arguments) {
        // First get current session
        String statusUrl = camaraServiceBaseUrl + "/api/camara/status";
        Map<String, Object> status = restTemplate.getForObject(statusUrl, Map.class);

        if (status == null || !Boolean.TRUE.equals(status.get("hasActiveSession"))) {
            return ToolResult.error("Uzatılacak aktif bir QoS session bulunmuyor.");
        }

        int additionalSeconds = getIntArg(arguments, "additionalSeconds", 1800);

        if (additionalSeconds <= 0) {
            return ToolResult.error("Invalid additionalSeconds: " + additionalSeconds + ". Must be positive.");
        }

        String sessionId = (String) status.get("currentSessionId");
        String url = camaraServiceBaseUrl + "/api/camara/session/" + sessionId + "/extend";

        Map<String, Object> request = Map.of("additionalSeconds", additionalSeconds);

        try {
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                String result = String.format(
                        "QoS Session uzatıldı!\n\n" +
                                "Session ID: %s\n" +
                                "Eklenen Süre: %d saniye\n" +
                                "Yeni Süre: %s saniye\n" +
                                "Yeni Bitiş: %s",
                        response.get("sessionId"),
                        additionalSeconds,
                        response.get("newDuration"),
                        response.get("expiresAt"));

                log.info("extend_qos_session: Session {} extended by {} seconds", sessionId, additionalSeconds);
                return ToolResult.text(result);
            } else {
                String error = response != null ? (String) response.get("error") : "Unknown error";
                return ToolResult.error("Session uzatılamadı: " + error);
            }
        } catch (Exception e) {
            return ToolResult.error("Session uzatılamadı: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private ToolResult executeSetBandwidth(Map<String, Object> arguments) {
        int bandwidthMbps = getIntArg(arguments, "bandwidthMbps", 500);

        if (bandwidthMbps < 100 || bandwidthMbps > 1000) {
            return ToolResult.error("Bandwidth must be between 100-1000 Mbps. Requested: " + bandwidthMbps);
        }

        String url = camaraServiceBaseUrl + "/api/camara/bandwidth";
        Map<String, Object> request = Map.of("bandwidthMbps", bandwidthMbps);

        try {
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                String result = String.format(
                        "Bandwidth değiştirildi!\n\n" +
                                "Önceki Değer: %d Mbps\n" +
                                "Yeni Değer: %d Mbps\n\n" +
                                "Durum: %s",
                        response.get("previousBandwidthMbps"),
                        response.get("newBandwidthMbps"),
                        response.get("message"));

                log.info("set_bandwidth: {} -> {} Mbps",
                        response.get("previousBandwidthMbps"), response.get("newBandwidthMbps"));
                return ToolResult.text(result);
            } else {
                String error = response != null ? (String) response.get("error") : "Unknown error";
                return ToolResult.error("Bandwidth değiştirilemedi: " + error);
            }
        } catch (Exception e) {
            return ToolResult.error("Bandwidth değiştirilemedi: " + e.getMessage());
        }
    }

    // Helper methods
    private String getStringArg(Map<String, Object> args, String key, String defaultValue) {
        if (args == null || !args.containsKey(key)) {
            return defaultValue;
        }
        Object value = args.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private int getIntArg(Map<String, Object> args, String key, int defaultValue) {
        if (args == null || !args.containsKey(key)) {
            return defaultValue;
        }
        Object value = args.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Execute tool asynchronously - for operations that send notifications back
     * This method starts the tool execution in a new thread.
     * The actual result will come via webhook notification.
     */
    public void executeAsync(String toolName, Map<String, Object> arguments) {
        log.info("Starting async execution of tool: {} with taskId: {}",
                toolName, arguments.get("taskId"));

        new Thread(() -> {
            try {
                // Execute the tool normally - it will handle sending notifications
                ToolResult result = execute(toolName, arguments);
                log.info("Async tool {} completed: isError={}", toolName, result.getIsError());
            } catch (Exception e) {
                log.error("Async tool {} failed: {}", toolName, e.getMessage(), e);
            }
        }).start();
    }
}
