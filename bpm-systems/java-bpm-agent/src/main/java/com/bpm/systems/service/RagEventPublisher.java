package com.bpm.systems.service;

import com.bpm.systems.event.RagEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * RAG Event Publisher
 * <p>
 * RAG pipeline'daki her adımı WebSocket üzerinden frontend'e gönderir.
 */
@Service
public class RagEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RagEventPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public RagEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * RAG event'i WebSocket'e gönder
     *
     * @param event RAG event
     */
    public void publish(RagEvent event) {
        try {
            // Set timestamp if not set
            if (event.getTimestamp() == null) {
                event.setTimestamp(LocalDateTime.now());
            }

            // Send to WebSocket topic
            messagingTemplate.convertAndSend("/topic/rag-events", event);

            log.debug("Published RAG event: {} - {} - {}",
                    event.getRequestId(),
                    event.getNode(),
                    event.getStatus());

        } catch (Exception e) {
            log.error("Failed to publish RAG event", e);
        }
    }

    /**
     * Convenience method: Create and publish event
     */
    public void publishNodeStatus(String requestId,
                                   RagEvent.RagNode node,
                                   RagEvent.NodeStatus status,
                                   String message) {

        publish(RagEvent.builder()
                .requestId(requestId)
                .node(node)
                .status(status)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build());
    }

    /**
     * Publish with duration
     */
    public void publishNodeCompleted(String requestId,
                                      RagEvent.RagNode node,
                                      String message,
                                      long durationMs,
                                      Map<String, Object> data) {

        publish(RagEvent.builder()
                .requestId(requestId)
                .node(node)
                .status(RagEvent.NodeStatus.COMPLETED)
                .message(message)
                .durationMs(durationMs)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build());
    }

    /**
     * Publish error
     */
    public void publishNodeError(String requestId,
                                  RagEvent.RagNode node,
                                  String message,
                                  String error) {

        publish(RagEvent.builder()
                .requestId(requestId)
                .node(node)
                .status(RagEvent.NodeStatus.ERROR)
                .message(message)
                .error(error)
                .timestamp(LocalDateTime.now())
                .build());
    }
}
