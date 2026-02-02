package com.bpm.systems.gemini;

import java.util.List;

/**
 * Gemini Embedding API Response DTO
 */
public class GeminiEmbeddingResponse {
    private Embedding embedding;

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }

    public static class Embedding {
        private List<Float> values;

        public List<Float> getValues() {
            return values;
        }

        public void setValues(List<Float> values) {
            this.values = values;
        }
    }
}
