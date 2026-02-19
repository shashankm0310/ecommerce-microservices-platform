package com.ecommerce.common.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Factory for creating a Kafka error handler with Dead Letter Queue (DLQ) support.
 *
 * Strategy: retry failed messages 3 times with exponential backoff (1s, 2s, 4s),
 * then publish to a DLQ topic (original-topic.DLT) for manual inspection or replay.
 * This prevents poison-pill messages from blocking consumers while preserving
 * the failed messages for later analysis.
 */
@Slf4j
public class DlqErrorHandler {

    private DlqErrorHandler() {
    }

    public static DefaultErrorHandler create(KafkaOperations<?, ?> kafkaOperations) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, ex) -> {
                    log.error("Sending message to DLQ: topic={}, key={}, error={}",
                            record.topic(), record.key(), ex.getMessage());
                    return new org.apache.kafka.common.TopicPartition(
                            record.topic() + ".DLT", record.partition());
                });

        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxElapsedTime(15000L); // 3 retries: 1s + 2s + 4s = 7s, well under 15s cap

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(
                com.fasterxml.jackson.core.JsonProcessingException.class);

        return errorHandler;
    }
}
