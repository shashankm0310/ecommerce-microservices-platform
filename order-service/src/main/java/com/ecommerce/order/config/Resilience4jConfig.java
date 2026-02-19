package com.ecommerce.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Programmatic Resilience4j configuration that logs circuit breaker state transitions.
 * State transitions (CLOSED → OPEN → HALF_OPEN) are critical for monitoring
 * the health of downstream dependencies like Kafka and other microservices.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class Resilience4jConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @PostConstruct
    public void registerStateTransitionListeners() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::addListener);
        circuitBreakerRegistry.getEventPublisher()
                .onEntryAdded(event -> addListener(event.getAddedEntry()));
    }

    private void addListener(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher()
                .onStateTransition(this::onStateTransition);
    }

    private void onStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        log.warn("CircuitBreaker '{}' transitioned from {} to {}",
                event.getCircuitBreakerName(),
                event.getStateTransition().getFromState(),
                event.getStateTransition().getToState());
    }
}
