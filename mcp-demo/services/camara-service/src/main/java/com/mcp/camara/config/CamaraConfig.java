package com.mcp.camara.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * CAMARA Service Configuration
 */
@Configuration
public class CamaraConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
