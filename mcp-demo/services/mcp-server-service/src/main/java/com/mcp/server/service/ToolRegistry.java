package com.mcp.server.service;

import com.mcp.common.protocol.Tool;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * MCP Tool Registry
 *
 * Manages available tools and their definitions.
 * Tools are registered at startup and can be queried via tools/list.
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        registerCamaraTools();
    }

    private void registerCamaraTools() {

        // 1. get_network_context - Device location from CAMARA
        tools.put("get_network_context", Tool.builder()
                .name("get_network_context")
                .description("CAMARA API'den cihaz konum ve ağ bilgisini alır. " +
                        "Kullanıcının şehir, ülke, roaming durumu ve location doğrulama bilgisini verir.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "msisdn", Map.of(
                                        "type", "string",
                                        "description",
                                        "Telefon numarası E.164 formatında (örn: +905551234567)")),
                        "required", List.of()))
                .build());

        // 2. get_qod_context - QoD session status
        tools.put("get_qod_context", Tool.builder()
                .name("get_qod_context")
                .description("Mevcut bandwidth durumunu ve QoS bilgisini gösterir. " +
                        "'Bandwidth durumum nedir?', 'Hızım ne kadar?', 'Mevcut Mbps değerim?' gibi sorular için kullanılır. " +
                        "Aktif session varsa detaylarını, yoksa kullanılabilir profilleri listeler.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()))
                .build());

        // 3. create_qos_session - Create QoS session via CAMARA
        tools.put("create_qos_session", Tool.builder()
                .name("create_qos_session")
                .description("Yeni bir QoS session'ı oluşturur. Kullanıcı hız artırmak, kalite yükseltmek veya gecikmeyi düşürmek istediğinde kullanılır.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "qosProfile", Map.of(
                                        "type", "string",
                                        "enum", List.of("QOS_S", "QOS_M", "QOS_L", "QOS_E"),
                                        "description",
                                        "QoS profili: QOS_S=standart, QOS_M=orta, QOS_L=yüksek hız/düşük gecikme, QOS_E=premium"),
                                "duration", Map.of(
                                        "type", "integer",
                                        "description",
                                        "Session süresi saniye cinsinden (örn: 3600 = 1 saat)",
                                        "default", 3600),
                                "phoneNumber", Map.of(
                                        "type", "string",
                                        "description",
                                        "Telefon numarası E.164 formatında (opsiyonel, varsayılan kullanılabilir)")),
                        "required", List.of("qosProfile")))
                .build());

        // 4. end_qos_session - End current QoS session
        tools.put("end_qos_session", Tool.builder()
                .name("end_qos_session")
                .description("Mevcut aktif QoS session'ı sonlandırır. " +
                        "Artırılmış hız/kalite devre dışı kalır, normal ağ durumuna dönülür.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()))
                .build());

        // 5. extend_qos_session - Extend session duration
        tools.put("extend_qos_session", Tool.builder()
                .name("extend_qos_session")
                .description("Mevcut aktif QoS session süresini uzatır.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "additionalSeconds", Map.of(
                                        "type", "integer",
                                        "description",
                                        "Eklenecek süre saniye cinsinden (örn: 1800 = 30 dakika)")),
                        "required", List.of("additionalSeconds")))
                .build());

        // 6. set_bandwidth - Direct bandwidth control (MOCK mode only)
        tools.put("set_bandwidth", Tool.builder()
                .name("set_bandwidth")
                .description("MOCK MODE: Doğrudan bandwidth değeri ayarlar. " +
                        "Kullanıcı '700 Mbps yap' veya 'hızımı 500 yap' gibi komutlar verdiğinde kullanılır. " +
                        "Değer 100-1000 Mbps aralığında olmalıdır.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "bandwidthMbps", Map.of(
                                        "type", "integer",
                                        "description",
                                        "Ayarlanacak bandwidth değeri (Mbps). Örn: 700, 500, 1000",
                                        "minimum", 100,
                                        "maximum", 1000)),
                        "required", List.of("bandwidthMbps")))
                .build());
    }

    public List<Tool> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    public Optional<Tool> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    public int getToolCount() {
        return tools.size();
    }
}
