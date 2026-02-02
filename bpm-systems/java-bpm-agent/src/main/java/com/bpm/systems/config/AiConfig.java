package com.bpm.systems.config;

import com.bpm.systems.gemini.GeminiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI Configuration
 *
 * Gemini modellerini Spring AI ChatClient ve RAG Advisor ile entegre eder.
 */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /**
     * ChatClient Bean - Gemini ChatModel kullanarak oluşturulur
     */
    @Bean
    public ChatClient chatClient(GeminiChatModel geminiChatModel) {
        log.info("Creating ChatClient with GeminiChatModel");
        return ChatClient.builder(geminiChatModel).build();
    }

    /**
     * RAG Advisor Bean - VectorStore ile QuestionAnswerAdvisor oluşturur
     */
    @Bean
    public QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        log.info("Creating QuestionAnswerAdvisor with VectorStore");
        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .similarityThreshold(0.70)
                        .topK(5)
                        .build())
                .build();
    }
}
