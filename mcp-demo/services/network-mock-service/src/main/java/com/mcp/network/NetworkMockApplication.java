package com.mcp.network;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

/**
 * Network Mock Service Application
 *
 * LAYER: NETWORK INFRASTRUCTURE (Mock)
 *
 * This service simulates the network state that can be modified by LLM actions.
 * In a real scenario, this would be the actual network configuration
 * managed by the telco operator's 5G/LTE core network.
 *
 * Port: 8085
 */
@SpringBootApplication
@EnableAsync
public class NetworkMockApplication {

    public static void main(String[] args) {
        SpringApplication.run(NetworkMockApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
