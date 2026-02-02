package com.bpm.systems.controller;

import com.bpm.systems.model.QueryRequest;
import com.bpm.systems.model.QueryResponse;
import com.bpm.systems.service.DocumentIndexingService;
import com.bpm.systems.service.RagService;
import com.bpm.systems.service.RagVisualizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * RAG API Controller
 *
 * Endpoints:
 * - POST /api/rag/query - RAG query (standard)
 * - POST /api/rag/query-visual - RAG query with real-time visualization
 * - POST /api/rag/index - Doküman indexleme
 * - POST /api/rag/index/sample - Sample data yükleme
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    private final RagService ragService;
    private final RagVisualizationService ragVisualizationService;
    private final DocumentIndexingService indexingService;

    public RagController(RagService ragService,
                         RagVisualizationService ragVisualizationService,
                         DocumentIndexingService indexingService) {
        this.ragService = ragService;
        this.ragVisualizationService = ragVisualizationService;
        this.indexingService = indexingService;
    }

    /**
     * RAG Query Endpoint
     *
     * POST /api/rag/query
     * {
     *   "question": "Uzaktan çalışma politikası nedir?"
     * }
     */
    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        log.info("📥 RAG query received: {}", request.getQuestion());

        long startTime = System.currentTimeMillis();

        try {
            String answer = ragService.ask(request.getQuestion());
            long duration = System.currentTimeMillis() - startTime;

            QueryResponse response = QueryResponse.builder()
                    .question(request.getQuestion())
                    .answer(answer)
                    .timestamp(LocalDateTime.now())
                    .durationMs(duration)
                    .success(true)
                    .build();

            log.info("✅ RAG query completed in {}ms", duration);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ RAG query failed", e);

            QueryResponse response = QueryResponse.builder()
                    .question(request.getQuestion())
                    .answer(null)
                    .error(e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .durationMs(System.currentTimeMillis() - startTime)
                    .success(false)
                    .build();

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * RAG Query with Real-time Visualization
     *
     * POST /api/rag/query-visual
     * {
     *   "question": "Uzaktan çalışma politikası nedir?"
     * }
     *
     * Bu endpoint RAG pipeline'ını adım adım WebSocket'e gönderir.
     * Frontend real-time olarak her adımı görür.
     */
    @PostMapping("/query-visual")
    public ResponseEntity<QueryResponse> queryWithVisualization(@RequestBody QueryRequest request) {
        log.info("📥 RAG query (visual) received: {}", request.getQuestion());

        long startTime = System.currentTimeMillis();

        try {
            String answer = ragVisualizationService.askWithVisualization(request.getQuestion());
            long duration = System.currentTimeMillis() - startTime;

            QueryResponse response = QueryResponse.builder()
                    .question(request.getQuestion())
                    .answer(answer)
                    .timestamp(LocalDateTime.now())
                    .durationMs(duration)
                    .success(true)
                    .build();

            log.info("✅ RAG query (visual) completed in {}ms", duration);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ RAG query (visual) failed", e);

            QueryResponse response = QueryResponse.builder()
                    .question(request.getQuestion())
                    .answer(null)
                    .error(e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .durationMs(System.currentTimeMillis() - startTime)
                    .success(false)
                    .build();

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Document Indexing Endpoint
     *
     * POST /api/rag/index
     * {
     *   "content": "Şirket politikası metni...",
     *   "metadata": {
     *     "type": "policy",
     *     "category": "hr"
     *   }
     * }
     */
    @PostMapping("/index")
    public ResponseEntity<Map<String, Object>> indexDocument(@RequestBody Map<String, Object> request) {
        log.info("📥 Document indexing request received");

        try {
            String content = (String) request.get("content");
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) request.getOrDefault("metadata", Map.of());

            indexingService.indexText(content, metadata);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Document indexed successfully",
                    "timestamp", LocalDateTime.now()
            ));

        } catch (Exception e) {
            log.error("❌ Document indexing failed", e);

            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "timestamp", LocalDateTime.now()
            ));
        }
    }

    /**
     * Batch Document Indexing Endpoint
     *
     * POST /api/rag/index/batch
     * [
     *   {
     *     "content": "...",
     *     "metadata": {...}
     *   }
     * ]
     */
    @PostMapping("/index/batch")
    public ResponseEntity<Map<String, Object>> indexDocumentsBatch(@RequestBody List<Map<String, Object>> documents) {
        log.info("📥 Batch indexing request received ({} documents)", documents.size());

        try {
            indexingService.indexDocuments(documents);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", String.format("Successfully indexed %d documents", documents.size()),
                    "count", documents.size(),
                    "timestamp", LocalDateTime.now()
            ));

        } catch (Exception e) {
            log.error("❌ Batch indexing failed", e);

            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "timestamp", LocalDateTime.now()
            ));
        }
    }

    /**
     * Load Sample Data Endpoint
     *
     * POST /api/rag/index/sample
     */
    @PostMapping("/index/sample")
    public ResponseEntity<Map<String, Object>> loadSampleData() {
        log.info("📥 Loading sample data");

        try {
            indexingService.loadSampleData();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Sample data loaded successfully",
                    "timestamp", LocalDateTime.now()
            ));

        } catch (Exception e) {
            log.error("❌ Sample data loading failed", e);

            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "timestamp", LocalDateTime.now()
            ));
        }
    }

    /**
     * Health Check Endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "RAG Service",
                "timestamp", LocalDateTime.now()
        ));
    }
}
