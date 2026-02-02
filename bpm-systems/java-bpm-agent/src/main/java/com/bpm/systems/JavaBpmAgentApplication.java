package com.bpm.systems;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Java BPM Agent - Spring AI + Flowable BPM Application
 *
 * Features:
 * - Spring AI RAG (Retrieval Augmented Generation)
 * - Qdrant Vector Store
 * - OpenAI GPT-4 & Embeddings
 * - Flowable BPM Workflow Engine
 * - JavaDelegate Service Tasks
 */
@SpringBootApplication
public class JavaBpmAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(JavaBpmAgentApplication.class, args);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("🚀 Java BPM Agent Started Successfully!");
        System.out.println("=".repeat(60));
        System.out.println("📍 API Server: http://localhost:8081");
        System.out.println("📍 H2 Console: http://localhost:8081/h2-console");
        System.out.println("📍 Health Check: http://localhost:8081/actuator/health");
        System.out.println("=".repeat(60) + "\n");
    }
}
