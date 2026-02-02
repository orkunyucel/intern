package com.mcp.llm.controller;

import com.mcp.llm.service.GeminiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * LLM Service REST Controller
 *
 * LAYER: LLM
 *
 * Exposes Gemini API operations for AI Agent.
 */
@RestController
@RequestMapping("/api/llm")
@CrossOrigin(origins = "*")
public class LlmController {

    private final GeminiService geminiService;

    public LlmController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    /**
     * Generate content with function calling support
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(@RequestBody Map<String, Object> request) {
        List<Map<String, Object>> messages = (List<Map<String, Object>>) request.get("messages");
        List<Map<String, Object>> tools = (List<Map<String, Object>>) request.get("tools");
        Map<String, Object> systemInstruction = (Map<String, Object>) request.get("systemInstruction");

        try {
            Map<String, Object> response = geminiService.generateWithFunctions(messages, tools, systemInstruction);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage(),
                    "configured", geminiService.isConfigured()));
        }
    }

    /**
     * Simple text generation
     */
    @PostMapping("/text")
    public ResponseEntity<Map<String, Object>> generateText(@RequestBody Map<String, String> request) {
        String prompt = request.get("prompt");

        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Prompt is required"));
        }

        try {
            String response = geminiService.generateText(prompt);
            return ResponseEntity.ok(Map.of("response", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Extract function call from Gemini response
     */
    @PostMapping("/extract-function-call")
    public ResponseEntity<Map<String, Object>> extractFunctionCall(@RequestBody Map<String, Object> response) {
        Map<String, Object> functionCall = geminiService.extractFunctionCall(response);

        if (functionCall != null) {
            return ResponseEntity.ok(Map.of(
                    "hasFunctionCall", true,
                    "functionCall", functionCall));
        } else {
            return ResponseEntity.ok(Map.of(
                    "hasFunctionCall", false,
                    "text", geminiService.extractText(response)));
        }
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "llm-service",
                "port", 8082,
                "configured", geminiService.isConfigured()));
    }
}
