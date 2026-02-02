package com.bpm.systems.gemini;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Gemini Chat Model - Spring AI ChatModel implementasyonu
 *
 * Bu sınıf Google Gemini API'yi Spring AI'ye ChatModel olarak tanıtır.
 * Böylece Spring AI'nin tüm RAG ve Chat özellikleri Gemini ile çalışır.
 */
@Component
public class GeminiChatModel implements ChatModel {

        private static final Logger log = LoggerFactory.getLogger(GeminiChatModel.class);

        private final WebClient webClient;
        private final String model;
        private final String apiKey;

        public GeminiChatModel(
                        @Value("${gemini.api.key}") String apiKey,
                        @Value("${gemini.chat.model:gemini-2.5-flash}") String model) {

                this.model = model;
                this.apiKey = apiKey;
                this.webClient = WebClient.builder()
                                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                .build();

                log.info("✅ GeminiChatModel initialized with model: {}", model);
        }

        @Override
        public ChatResponse call(Prompt prompt) {
                log.debug("Calling Gemini API with prompt...");

                try {
                        // Prompt'tan kullanıcı mesajını al
                        StringBuilder userTextBuilder = new StringBuilder();
                        for (var message : prompt.getInstructions()) {
                                userTextBuilder.append(message.getText()).append("\n");
                        }
                        String userText = userTextBuilder.toString().trim();

                        // Gemini API request body
                        Map<String, Object> body = Map.of(
                                        "contents", List.of(
                                                        Map.of("parts", List.of(Map.of("text", userText)))),
                                        "generationConfig", Map.of(
                                                        "temperature", 0.7,
                                                        "maxOutputTokens", 2048,
                                                        "topP", 1.0,
                                                        "topK", 1));

                        // API çağrısı
                        String uri = "/models/" + model + ":generateContent";
                        log.debug("Calling Gemini Chat API: {}", uri);
                        log.debug("Request body: {}", body);

                        GeminiResponse response = webClient.post()
                                        .uri(uriBuilder -> uriBuilder
                                                .path(uri)
                                                .queryParam("key", apiKey)
                                                .build())
                                        .bodyValue(body)
                                        .retrieve()
                                        .bodyToMono(GeminiResponse.class)
                                        .block();

                        if (response == null || response.getCandidates() == null
                                        || response.getCandidates().isEmpty()) {
                                throw new RuntimeException("Empty response from Gemini API");
                        }

                        // Response'dan text'i çıkar
                        String text = response.getCandidates().get(0)
                                        .getContent()
                                        .getParts().get(0)
                                        .getText();

                        log.debug("Gemini response received successfully");

                        // Spring AI ChatResponse oluştur
                        Generation generation = new Generation(new AssistantMessage(text));
                        return new ChatResponse(List.of(generation));

                } catch (Exception e) {
                        log.error("Gemini API call failed", e);
                        throw new RuntimeException("Failed to call Gemini API: " + e.getMessage(), e);
                }
        }
}
