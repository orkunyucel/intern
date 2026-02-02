package com.bpm.systems.gemini;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gemini Embedding Model - Spring AI EmbeddingModel implementasyonu
 *
 * Bu sınıf Google Gemini Embedding API'yi Spring AI'ye EmbeddingModel olarak
 * tanıtır.
 * RAG pipeline'ı için kritik olan vector embedding işlemlerini gerçekleştirir.
 */
@Component
public class GeminiEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingModel.class);

    private final WebClient webClient;
    private final String model;

    private final String apiKey;

    public GeminiEmbeddingModel(
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.embedding.model:text-embedding-004}") String model) {

        this.model = model;
        this.apiKey = apiKey;
        this.webClient = WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();

        log.info("✅ GeminiEmbeddingModel initialized with model: {}", model);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        log.debug("Calling Gemini Embedding API...");

        try {
            List<Embedding> embeddings = new ArrayList<>();
            int index = 0;

            for (String text : request.getInstructions()) {
                float[] embedding = embedSingleText(text);
                embeddings.add(new Embedding(embedding, index++));
            }

            log.debug("Gemini embedding completed for {} texts", embeddings.size());
            return new EmbeddingResponse(embeddings);

        } catch (Exception e) {
            log.error("Gemini Embedding API call failed", e);
            throw new RuntimeException("Failed to call Gemini Embedding API: " + e.getMessage(), e);
        }
    }

    @Override
    public float[] embed(String text) {
        return embedSingleText(text);
    }

    @Override
    public float[] embed(Document document) {
        return embedSingleText(document.getText());
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> result = new ArrayList<>();
        for (String text : texts) {
            result.add(embedSingleText(text));
        }
        return result;
    }

    @Override
    public int dimensions() {
        // text-embedding-004 model dimension
        return 768;
    }

    private float[] embedSingleText(String text) {
        // Gemini Embedding API request body
        Map<String, Object> body = Map.of(
                "model", "models/" + model,
                "content", Map.of(
                        "parts", List.of(
                                Map.of("text", text))));

        String uri = "/models/" + model + ":embedContent";
        log.debug("Calling Gemini Embedding API: {}", uri);

        GeminiEmbeddingResponse response = webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(uri)
                        .queryParam("key", apiKey)
                        .build())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(GeminiEmbeddingResponse.class)
                .block();

        if (response == null || response.getEmbedding() == null) {
            throw new RuntimeException("Empty response from Gemini Embedding API");
        }

        List<Float> values = response.getEmbedding().getValues();
        float[] embedding = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            embedding[i] = values.get(i);
        }

        return embedding;
    }
}
