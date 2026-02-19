package com.ecommerce.order.client;

import com.ecommerce.common.dto.ProductResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;

/**
 * HTTP client for calling product-service to validate product existence and prices.
 *
 * Resilience strategy:
 * - @CircuitBreaker: prevents cascading failures when product-service is down.
 * - @Retry: handles transient network issues with 3 attempts.
 * - Fallback: returns Optional.empty() so order creation can proceed with
 *   client-provided product info (graceful degradation).
 */
@Component
@Slf4j
public class ProductServiceClient {

    private final WebClient webClient;

    public ProductServiceClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("http://product-service").build();
    }

    @CircuitBreaker(name = "productService", fallbackMethod = "getProductFallback")
    @Retry(name = "productService")
    public Optional<ProductResponse> getProduct(String productId) {
        log.debug("Fetching product from product-service: {}", productId);

        ProductResponse response = webClient.get()
                .uri("/api/products/{id}", productId)
                .retrieve()
                .bodyToMono(ProductResponse.class)
                .block();

        return Optional.ofNullable(response);
    }

    /**
     * Fallback when product-service is unavailable.
     * Returns empty so order creation can proceed with client-supplied data.
     */
    public Optional<ProductResponse> getProductFallback(String productId, Throwable t) {
        log.warn("Product-service unavailable for productId={}. Fallback: skipping validation. Error: {}",
                productId, t.getMessage());
        return Optional.empty();
    }
}
