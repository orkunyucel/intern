package com.mcp.llm.service;

import com.mcp.llm.config.GeminiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Gemini API Service
 *
 * LAYER: LLM
 *
 * Handles all communication with Gemini API.
 * Supports function calling for agent operations.
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final RestTemplate restTemplate;
    private final GeminiConfig config;

    public GeminiService(RestTemplate restTemplate, GeminiConfig config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    /**
     * Call Gemini API with function calling support
     *
     * @param messages         Conversation messages
     * @param tools            Available tools (function declarations)
     * @param systemInstruction System instruction
     * @return Raw Gemini API response
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> generateWithFunctions(
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            Map<String, Object> systemInstruction) {

        if (!config.isConfigured()) {
            throw new IllegalStateException("Gemini API is not configured. Set gemini.api.key property.");
        }

        String url = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                config.getModel(),
                config.getApiKey());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", messages);

        if (systemInstruction != null) {
            requestBody.put("systemInstruction", systemInstruction);
        }

        if (tools != null && !tools.isEmpty()) {
            requestBody.put("tools", List.of(Map.of("function_declarations", tools)));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        log.info("Calling Gemini API with {} messages and {} tools",
                messages.size(), tools != null ? tools.size() : 0);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage());
            throw new RuntimeException("Gemini API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Simple text generation without function calling
     */
    @SuppressWarnings("unchecked")
    public String generateText(String prompt) {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "parts", List.of(Map.of("text", prompt))));

        Map<String, Object> response = generateWithFunctions(messages, null, null);
        return extractText(response);
    }

    /**
     * Extract function call from Gemini response
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> extractFunctionCall(Map<String, Object> response) {
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

    /**
     * Extract text from Gemini response
     */
    @SuppressWarnings("unchecked")
    public String extractText(Map<String, Object> response) {
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

    /**
     * Check if response contains a function call
     */
    public boolean hasFunctionCall(Map<String, Object> response) {
        return extractFunctionCall(response) != null;
    }

    /**
     * Check if Gemini API is configured
     */
    public boolean isConfigured() {
        return config.isConfigured();
    }
}
