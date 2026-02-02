package com.bpm.systems.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * RAG (Retrieval Augmented Generation) Service
 *
 * Bu servis Spring AI + Gemini kullanarak:
 * 1. Vector Store'dan ilgili dokümanları çeker (retrieval)
 * 2. Dokümanları prompt'a ekler (augmentation)
 * 3. Gemini LLM ile cevap üretir (generation)
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final ChatClient chatClient;
    private final QuestionAnswerAdvisor ragAdvisor;
    private final VectorStore vectorStore;

    public RagService(ChatClient chatClient,
            QuestionAnswerAdvisor ragAdvisor,
            VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.ragAdvisor = ragAdvisor;
        this.vectorStore = vectorStore;

        log.info("✅ RAG Service initialized with Gemini ChatClient and QuestionAnswerAdvisor");
    }

    /**
     * RAG kullanarak soru-cevap
     *
     * @param question Kullanıcı sorusu
     * @return Gemini LLM'den gelen cevap
     */
    public String ask(String question) {
        log.debug("RAG query: {}", question);

        try {
            String answer = chatClient.prompt()
                    .advisors(ragAdvisor)
                    .user(question)
                    .call()
                    .content();

            log.debug("RAG response generated successfully");
            return answer;

        } catch (Exception e) {
            log.error("RAG query failed", e);
            throw new RuntimeException("Failed to process RAG query: " + e.getMessage(), e);
        }
    }

    /**
     * Metadata filtresi ile RAG query
     *
     * @param question         Kullanıcı sorusu
     * @param filterExpression Metadata filtresi (örn: "type == 'policy'")
     * @return Gemini LLM'den gelen cevap
     */
    public String askWithFilter(String question, String filterExpression) {
        log.debug("RAG query with filter: {} | filter: {}", question, filterExpression);

        try {
            String answer = chatClient.prompt()
                    .advisors(ragAdvisor)
                    .advisors(advisor -> advisor
                            .param(QuestionAnswerAdvisor.FILTER_EXPRESSION, filterExpression))
                    .user(question)
                    .call()
                    .content();

            log.debug("RAG response generated successfully with filter");
            return answer;

        } catch (Exception e) {
            log.error("RAG query with filter failed", e);
            throw new RuntimeException("Failed to process filtered RAG query: " + e.getMessage(), e);
        }
    }

    /**
     * Vector Store'daki doküman sayısını döndür (debug için)
     */
    public long getDocumentCount() {
        // Not: VectorStore interface'inde count() metodu yok
        // Qdrant'tan direkt çekmek gerekirse QdrantVectorStore'a cast edilmeli
        log.debug("Document count requested");
        return 0; // Placeholder
    }
}
