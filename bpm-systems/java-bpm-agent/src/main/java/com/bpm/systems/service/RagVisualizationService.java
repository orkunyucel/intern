package com.bpm.systems.service;

import com.bpm.systems.event.RagEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RAG Service with Real-time Visualization
 * <p>
 * Her RAG pipeline adımını WebSocket üzerinden frontend'e bildirir.
 * <p>
 * Pipeline:
 * 1. USER_INPUT
 * 2. EMBEDDING_GENERATION
 * 3. VECTOR_SEARCH
 * 4. RETRIEVED_DOCUMENTS
 * 5. LLM_CALL
 * 6. RESPONSE_GENERATION
 */
@Service
public class RagVisualizationService {

    private static final Logger log = LoggerFactory.getLogger(RagVisualizationService.class);

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final RagEventPublisher eventPublisher;

    public RagVisualizationService(EmbeddingModel embeddingModel,
                                    VectorStore vectorStore,
                                    ChatModel chatModel,
                                    RagEventPublisher eventPublisher) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.eventPublisher = eventPublisher;

        log.info("✅ RAG Visualization Service initialized");
    }

    /**
     * RAG Query with Real-time Visualization
     *
     * @param question Kullanıcı sorusu
     * @return LLM cevabı
     */
    public String askWithVisualization(String question) {
        // Generate unique request ID
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        log.info("🚀 RAG query started - Request ID: {}", requestId);

        try {
            // 1. USER_INPUT
            eventPublisher.publishNodeStatus(requestId,
                    RagEvent.RagNode.USER_INPUT,
                    RagEvent.NodeStatus.COMPLETED,
                    "User question received: " + question.substring(0, Math.min(50, question.length())) + "...");

            Thread.sleep(300); // UI için görünürlük

            // 2. EMBEDDING_GENERATION
            long startTime = System.currentTimeMillis();
            eventPublisher.publishNodeStatus(requestId,
                    RagEvent.RagNode.EMBEDDING_GENERATION,
                    RagEvent.NodeStatus.PROCESSING,
                    "Generating embedding vector...");

            EmbeddingResponse embeddingResponse = embeddingModel.call(
                    new EmbeddingRequest(List.of(question), null)
            );

            float[] embeddingArray = embeddingResponse.getResults().get(0).getOutput();
            // Convert float[] to List<Double> for metric display
            int embeddingSize = embeddingArray.length;
            long embeddingDuration = System.currentTimeMillis() - startTime;

            eventPublisher.publishNodeCompleted(requestId,
                    RagEvent.RagNode.EMBEDDING_GENERATION,
                    "Embedding generated (" + embeddingSize + " dimensions)",
                    embeddingDuration,
                    Map.of("dimensions", embeddingSize));

            Thread.sleep(500);

            // 3. VECTOR_SEARCH
            startTime = System.currentTimeMillis();
            eventPublisher.publishNodeStatus(requestId,
                    RagEvent.RagNode.VECTOR_SEARCH,
                    RagEvent.NodeStatus.PROCESSING,
                    "Searching vector database...");

            SearchRequest searchRequest = SearchRequest.builder()
                    .query(question)
                    .topK(5)
                    .similarityThreshold(0.70)
                    .build();

            List<Document> retrievedDocs = vectorStore.similaritySearch(searchRequest);
            long searchDuration = System.currentTimeMillis() - startTime;

            eventPublisher.publishNodeCompleted(requestId,
                    RagEvent.RagNode.VECTOR_SEARCH,
                    "Found " + retrievedDocs.size() + " relevant documents",
                    searchDuration,
                    Map.of("documentCount", retrievedDocs.size()));

            Thread.sleep(500);

            // 4. RETRIEVED_DOCUMENTS
            String context = retrievedDocs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));

            eventPublisher.publishNodeStatus(requestId,
                    RagEvent.RagNode.RETRIEVED_DOCUMENTS,
                    RagEvent.NodeStatus.COMPLETED,
                    "Retrieved " + retrievedDocs.size() + " documents");

            Thread.sleep(300);

            // 5. LLM_CALL
            startTime = System.currentTimeMillis();
            eventPublisher.publishNodeStatus(requestId,
                    RagEvent.RagNode.LLM_CALL,
                    RagEvent.NodeStatus.PROCESSING,
                    "Calling LLM with context...");

            // Build prompt with context
            String promptText = String.format("""
                    You are a helpful assistant. Answer the question based on the following context.

                    Context:
                    %s

                    Question: %s

                    Answer:
                    """, context, question);

            String answer = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();
            long llmDuration = System.currentTimeMillis() - startTime;

            eventPublisher.publishNodeCompleted(requestId,
                    RagEvent.RagNode.LLM_CALL,
                    "LLM response received",
                    llmDuration,
                    Map.of("responseLength", answer.length()));

            Thread.sleep(500);

            // 6. RESPONSE_GENERATION
            eventPublisher.publishNodeStatus(requestId,
                    RagEvent.RagNode.RESPONSE_GENERATION,
                    RagEvent.NodeStatus.COMPLETED,
                    "Answer generated successfully");

            Thread.sleep(300);

            // 7. COMPLETED
            eventPublisher.publishNodeStatus(requestId,
                    RagEvent.RagNode.COMPLETED,
                    RagEvent.NodeStatus.COMPLETED,
                    "RAG pipeline completed successfully");

            log.info("✅ RAG query completed - Request ID: {}", requestId);

            return answer;

        } catch (Exception e) {
            log.error("❌ RAG query failed - Request ID: {}", requestId, e);

            // Publish error event
            eventPublisher.publishNodeError(requestId,
                    RagEvent.RagNode.COMPLETED,
                    "RAG pipeline failed",
                    e.getMessage());

            throw new RuntimeException("RAG query failed: " + e.getMessage(), e);
        }
    }
}
