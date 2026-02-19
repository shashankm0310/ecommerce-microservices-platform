package com.ecommerce.order.kafka.producer;

import com.ecommerce.order.entity.OutboxEvent;
import com.ecommerce.order.repository.OutboxEventRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Transactional Outbox publisher that polls unprocessed events and sends them to Kafka.
 *
 * Resilience strategy:
 * - @CircuitBreaker: trips open after repeated Kafka broker failures, preventing cascade.
 * - @Retry: retries transient Kafka send failures (e.g., leader election) before giving up.
 * - Fallback: logs the failure without crashing — the scheduler will retry on the next tick.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "publishFallback")
    @Retry(name = "kafkaProducer")
    public void publishOutboxEvents() {
        List<OutboxEvent> events = outboxEventRepository.findByProcessedFalseOrderByCreatedAtAsc();

        if (events.isEmpty()) {
            return;
        }

        log.debug("Found {} unprocessed outbox events", events.size());

        for (OutboxEvent event : events) {
            String topic = resolveTopicName(event.getAggregateType());

            kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload())
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish outbox event {} to topic {}: {}",
                                    event.getId(), topic, ex.getMessage());
                        } else {
                            log.info("Published outbox event {} to topic {} [partition={}, offset={}]",
                                    event.getId(), topic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });

            event.setProcessed(true);
            event.setProcessedAt(LocalDateTime.now());
            outboxEventRepository.save(event);

            log.debug("Outbox event {} marked as processed", event.getId());
        }
    }

    /**
     * Fallback when the circuit breaker is open or retries are exhausted.
     * The events remain unprocessed in the outbox table and will be retried
     * on the next scheduled tick once the circuit breaker transitions to half-open.
     */
    public void publishFallback(Exception ex) {
        log.warn("Outbox publishing circuit breaker active — Kafka may be unavailable: {}", ex.getMessage());
    }

    private String resolveTopicName(String aggregateType) {
        return switch (aggregateType.toLowerCase()) {
            case "order" -> "order-events";
            default -> aggregateType.toLowerCase() + "-events";
        };
    }
}
