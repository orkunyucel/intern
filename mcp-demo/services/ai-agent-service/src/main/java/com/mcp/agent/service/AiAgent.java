package com.mcp.agent.service;

import com.mcp.common.protocol.Tool;
import com.mcp.common.protocol.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * AI Agent with Real MCP Protocol
 *
 * LAYER: CLIENT (Agent)
 *
 * This is the main agent that:
 * 1. Gets tool list from MCP Server (tool discovery)
 * 2. Sends user query + tools to LLM Service (with function calling)
 * 3. If LLM wants to call a tool, calls it via MCP Client
 * 4. Returns result back to LLM
 * 5. Repeats until LLM gives final response
 *
 * This implements the ReAct (Reasoning + Acting) pattern.
 */
@Service
public class AiAgent {

    private static final Logger log = LoggerFactory.getLogger(AiAgent.class);
    private static final int MAX_ITERATIONS = 5;

    private final McpClient mcpClient;
    private final RestTemplate restTemplate;

    @Value("${llm-service.url:http://localhost:8082}")
    private String llmServiceUrl;

    private List<AgentStep> executionTrace = new ArrayList<>();

    public AiAgent(McpClient mcpClient, RestTemplate restTemplate) {
        this.mcpClient = mcpClient;
        this.restTemplate = restTemplate;
    }

    /**
     * Main agent execution method
     */
    public AgentResponse run(String userQuery, boolean includeStatusInPrompt) {
        log.info("Agent starting with query: {} (includeStatus={})", userQuery, includeStatusInPrompt);
        executionTrace = new ArrayList<>();

        try {
            // Step 1: Get available tools from MCP Server
            log.info("Step 1: Fetching tools from MCP Server");
            List<Tool> tools = mcpClient.listTools();
            executionTrace.add(AgentStep.toolDiscovery(tools.size()));

            // Step 2: Build conversation
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> systemInstruction = buildSystemInstruction(includeStatusInPrompt);

            messages.add(buildUserMessage(userQuery));

            // Convert tools to function declarations
            List<Map<String, Object>> functionDeclarations = tools.stream()
                    .map(this::toolToFunctionDeclaration)
                    .toList();

            executionTrace.add(AgentStep.prompt(messages, tools, systemInstruction));

            // Step 3: Agent loop (ReAct pattern)
            String finalResponse = null;
            int iteration = 0;

            while (iteration < MAX_ITERATIONS) {
                iteration++;
                log.info("Agent iteration {}", iteration);

                // Call LLM Service
                Map<String, Object> llmResponse = callLlmService(messages, functionDeclarations, systemInstruction);

                // Check if LLM wants to call a function
                Map<String, Object> functionCall = extractFunctionCall(llmResponse);

                if (functionCall != null) {
                    String toolName = (String) functionCall.get("name");
                    Map<String, Object> args = parseArguments(functionCall.get("args"));

                    log.info("LLM wants to call tool: {} with args: {}", toolName, args);
                    executionTrace.add(AgentStep.llmToolCall(toolName, args));

                    // Call tool via MCP Client
                    ToolResult toolResult = mcpClient.callTool(toolName, args);
                    String resultText = getResultText(toolResult);

                    log.info("Tool result: {}", resultText);
                    executionTrace.add(AgentStep.toolResult(toolName, resultText));

                    // Add to conversation
                    messages.add(buildAssistantFunctionCall(toolName, args));
                    messages.add(buildFunctionResult(toolName, resultText));

                } else {
                    // LLM gave final response
                    finalResponse = extractText(llmResponse);
                    log.info("LLM final response received");
                    executionTrace.add(AgentStep.finalResponse(finalResponse));
                    break;
                }
            }

            if (finalResponse == null) {
                finalResponse = "Agent maksimum iterasyon sayısına ulaştı.";
            }

            return new AgentResponse(finalResponse, executionTrace, true);

        } catch (Exception e) {
            log.error("Agent execution failed: {}", e.getMessage(), e);
            executionTrace.add(AgentStep.error(e.getMessage()));
            return new AgentResponse("Hata: " + e.getMessage(), executionTrace, false);
        }
    }

    public AgentResponse run(String userQuery) {
        return run(userQuery, false);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callLlmService(
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            Map<String, Object> systemInstruction) {

        String url = llmServiceUrl + "/api/llm/generate";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("messages", messages);
        requestBody.put("tools", tools);
        requestBody.put("systemInstruction", systemInstruction);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        return response.getBody();
    }

    private Map<String, Object> toolToFunctionDeclaration(Tool tool) {
        Map<String, Object> fn = new HashMap<>();
        fn.put("name", tool.getName());
        fn.put("description", tool.getDescription());

        Map<String, Object> schema = tool.getInputSchema();
        if (schema != null) {
            fn.put("parameters", schema);
        } else {
            fn.put("parameters", Map.of("type", "object", "properties", Map.of()));
        }

        return fn;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractFunctionCall(Map<String, Object> response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) return null;

            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");

            for (Map<String, Object> part : parts) {
                if (part.containsKey("functionCall")) {
                    return (Map<String, Object>) part.get("functionCall");
                }
            }

            return null;
        } catch (Exception e) {
            log.warn("Failed to extract function call: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) return "Yanıt alınamadı.";

            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");

            StringBuilder text = new StringBuilder();
            for (Map<String, Object> part : parts) {
                if (part.containsKey("text")) {
                    text.append(part.get("text"));
                }
            }

            return text.toString();
        } catch (Exception e) {
            log.warn("Failed to extract text: {}", e.getMessage());
            return "Yanıt işlenemedi.";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(Object args) {
        if (args == null) return Map.of();
        if (args instanceof Map) return (Map<String, Object>) args;
        return Map.of();
    }

    private String getResultText(ToolResult result) {
        if (result.getContent() == null || result.getContent().isEmpty()) {
            return "Sonuç alınamadı.";
        }
        return result.getContent().get(0).getText();
    }

    private Map<String, Object> buildSystemInstruction(boolean includeStatus) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Sen bir ağ asistanısın.\n");
        prompt.append("CAMARA API üzerinden QoS (Quality on Demand) session'larını yönetebilirsin.\n\n");

        prompt.append("Elindeki yetenekler tools listesinde tanımlıdır.\n");
        prompt.append("Kullanıcı isteği bir işlem gerektiriyorsa uygun tool'u seç ve functionCall üret.\n");
        prompt.append("Sadece bilgi gerekiyorsa tool çağırmadan cevapla.\n\n");

        prompt.append("ÖNEMLİ: Sadece tools listesinde tanımlı yetenekleri kullanabilirsin.\n");
        prompt.append("Liste dışı işlem üretme ve varsayım yapma.\n\n");

        prompt.append("Yanıtlarını Türkçe ver.\n");

        if (includeStatus) {
            // TODO: Get actual status from network-mock-service
            prompt.append("\nMEVCUT SİSTEM DURUMU:\n");
            prompt.append("- Status injection will be added\n");
        }

        return Map.of("parts", List.of(Map.of("text", prompt.toString())));
    }

    private Map<String, Object> buildUserMessage(String query) {
        return Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", query)));
    }

    private Map<String, Object> buildAssistantFunctionCall(String name, Map<String, Object> args) {
        return Map.of(
                "role", "model",
                "parts", List.of(Map.of(
                        "functionCall", Map.of(
                                "name", name,
                                "args", args))));
    }

    private Map<String, Object> buildFunctionResult(String name, String result) {
        return Map.of(
                "role", "function",
                "parts", List.of(Map.of(
                        "functionResponse", Map.of(
                                "name", name,
                                "response", Map.of("result", result)))));
    }

    // ==================== INNER CLASSES ====================

    public static class AgentStep {
        private final String type;
        private final String description;
        private final Object data;
        private final long timestamp;

        public AgentStep(String type, String description, Object data) {
            this.type = type;
            this.description = description;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public static AgentStep toolDiscovery(int toolCount) {
            return new AgentStep("TOOL_DISCOVERY",
                    "MCP Server'dan " + toolCount + " tool alındı", toolCount);
        }

        public static AgentStep llmToolCall(String toolName, Map<String, Object> args) {
            return new AgentStep("LLM_TOOL_CALL",
                    "LLM tool çağırmak istiyor: " + toolName,
                    Map.of("tool", toolName, "args", args));
        }

        public static AgentStep toolResult(String toolName, String result) {
            return new AgentStep("TOOL_RESULT",
                    "Tool sonucu alındı: " + toolName, result);
        }

        public static AgentStep finalResponse(String response) {
            return new AgentStep("FINAL_RESPONSE",
                    "LLM final yanıt verdi", response);
        }

        public static AgentStep error(String message) {
            return new AgentStep("ERROR", message, null);
        }

        public static AgentStep prompt(List<Map<String, Object>> messages, List<Tool> tools,
                Map<String, Object> systemInstruction) {
            Map<String, Object> data = new HashMap<>();
            data.put("messages", messages);
            data.put("tools", tools);
            data.put("systemInstruction", systemInstruction);

            return new AgentStep("PROMPT",
                    "LLM'e gönderilen prompt (System + Messages + Tools)", data);
        }

        public String getType() { return type; }
        public String getDescription() { return description; }
        public Object getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }

    public static class AgentResponse {
        private final String response;
        private final List<AgentStep> trace;
        private final boolean success;

        public AgentResponse(String response, List<AgentStep> trace, boolean success) {
            this.response = response;
            this.trace = trace;
            this.success = success;
        }

        public String getResponse() { return response; }
        public List<AgentStep> getTrace() { return trace; }
        public boolean isSuccess() { return success; }
    }
}
