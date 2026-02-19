package com.ecommerce.common.webclient;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration for a load-balanced WebClient that propagates correlation IDs
 * and user context headers across service-to-service calls.
 */
@AutoConfiguration
@ConditionalOnClass(WebClient.class)
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder()
                .filter(new HeaderPropagationFilter());
    }
}
