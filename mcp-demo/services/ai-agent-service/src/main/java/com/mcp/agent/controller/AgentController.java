package com.mcp.agent.controller;

import com.mcp.agent.service.AiAgent;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * AI Agent Controller
 *
 * LAYER: CLIENT (Agent)
 *
 * REST endpoints for the MCP-based AI Agent.
 * Uses real MCP protocol with tool discovery and function calling.
 */
@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*")
public class AgentController {

    private final AiAgent aiAgent;

    public AgentController(AiAgent aiAgent) {
        this.aiAgent = aiAgent;
    }

    /**
     * Run agent with SSE streaming
     *
     * This endpoint:
     * 1. Gets tools from MCP Server (tool discovery)
     * 2. Sends user query + tools to LLM Service
     * 3. Executes tool calls via MCP protocol
     * 4. Returns result with execution trace
     *
     * SSE Events: step, tool-call, tool-result, llm-response, final-response, done
     */
    @GetMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runWithStream(@RequestParam String question,
            @RequestParam(defaultValue = "false") boolean includeStatus) {
        SseEmitter emitter = new SseEmitter(120000L); // 2 min timeout

        new Thread(() -> {
            try {
                // Send start event
                sendEvent(emitter, "step", Map.of(
                        "step", "started",
                        "message", "Agent started - MCP tool discovery in progress..."));

                // Run agent
                AiAgent.AgentResponse response = aiAgent.run(question, includeStatus);

                // Send trace events with appropriate event types
                for (AiAgent.AgentStep step : response.getTrace()) {
                    String eventType = mapStepTypeToEventType(step.getType());

                    Map<String, Object> traceData = new HashMap<>();
                    traceData.put("type", step.getType());
                    traceData.put("step", step.getType().toLowerCase().replace("_", "-"));
                    traceData.put("description", step.getDescription());
                    traceData.put("timestamp", step.getTimestamp());

                    if (step.getData() != null) {
                        traceData.put("data", step.getData());

                        // Extract specific fields for UI
                        if (step.getData() instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> dataMap = (Map<String, Object>) step.getData();
                            if (dataMap.containsKey("name")) {
                                traceData.put("name", dataMap.get("name"));
                            }
                            if (dataMap.containsKey("arguments")) {
                                traceData.put("arguments", dataMap.get("arguments"));
                            }
                            if (dataMap.containsKey("result")) {
                                traceData.put("result", dataMap.get("result"));
                            }
                            if (dataMap.containsKey("content")) {
                                traceData.put("content", dataMap.get("content"));
                            }
                            if (dataMap.containsKey("toolCount")) {
                                traceData.put("toolCount", dataMap.get("toolCount"));
                            }
                            if (dataMap.containsKey("tools")) {
                                traceData.put("tools", dataMap.get("tools"));
                            }
                        }
                    }

                    sendEvent(emitter, eventType, traceData);
                    Thread.sleep(100); // Small delay for UI
                }

                // Send final result
                sendEvent(emitter, "final-response", Map.of(
                        "success", response.isSuccess(),
                        "response", response.getResponse(),
                        "traceCount", response.getTrace().size()));

                // Send done event
                sendEvent(emitter, "done", Map.of("status", "completed"));
                emitter.complete();

            } catch (Exception e) {
                try {
                    sendEvent(emitter, "error-event", Map.of("message", e.getMessage()));
                    sendEvent(emitter, "done", Map.of("status", "error"));
                } catch (IOException ignored) {
                }
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    private String mapStepTypeToEventType(String stepType) {
        if (stepType == null)
            return "step";
        return switch (stepType.toUpperCase()) {
            case "TOOL_DISCOVERY", "TOOLS_LIST", "MCP_INITIALIZE" -> "step";
            case "TOOL_CALL", "TOOL_EXECUTION" -> "tool-call";
            case "TOOL_RESULT", "TOOL_RESPONSE" -> "tool-result";
            case "LLM_CALL", "LLM_RESPONSE", "LLM_THINKING" -> "llm-response";
            case "FINAL_ANSWER", "COMPLETE" -> "final-response";
            default -> "step";
        };
    }

    /**
     * Run agent without streaming (simple mode)
     */
    @PostMapping("/run")
    public Map<String, Object> runSimple(@RequestBody Map<String, Object> request) {
        String question = (String) request.get("question");
        boolean includeStatus = Boolean.TRUE.equals(request.get("includeStatus"));

        if (question == null || question.isBlank()) {
            return Map.of("success", false, "error", "Question is required");
        }

        AiAgent.AgentResponse response = aiAgent.run(question, includeStatus);

        return Map.of(
                "success", response.isSuccess(),
                "response", response.getResponse(),
                "traceCount", response.getTrace().size());
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "ai-agent-service",
                "port", 8081,
                "agent", "AiAgent with MCP",
                "features", "Tool Discovery, Function Calling, ReAct Loop");
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data));
    }
}
