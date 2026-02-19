package com.ecommerce.order.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Optional HTTP client for pre-checking inventory availability before order creation.
 * Even if this check fails, the saga will handle reservation asynchronously.
 */
@Component
@Slf4j
public class InventoryServiceClient {

    private final WebClient webClient;

    public InventoryServiceClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("http://inventory-service").build();
    }

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "checkAvailabilityFallback")
    @Retry(name = "inventoryService")
    public boolean checkAvailability(String productId) {
        log.debug("Checking inventory availability for productId={}", productId);

        try {
            webClient.get()
                    .uri("/api/inventory/{productId}", productId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return true;
        } catch (Exception e) {
            log.warn("Inventory check failed for productId={}: {}", productId, e.getMessage());
            return false;
        }
    }

    public boolean checkAvailabilityFallback(String productId, Throwable t) {
        log.warn("Inventory-service unavailable for productId={}. Fallback: assuming available. Error: {}",
                productId, t.getMessage());
        return true;
    }
}
