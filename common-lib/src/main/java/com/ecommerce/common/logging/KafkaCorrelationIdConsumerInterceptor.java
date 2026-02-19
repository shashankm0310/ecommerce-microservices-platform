package com.ecommerce.common.logging;

import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka consumer interceptor that restores the correlation ID from Kafka headers into MDC.
 * Ensures that log entries produced during message consumption are tagged with the
 * same correlation ID that originated the request.
 */
public class KafkaCorrelationIdConsumerInterceptor implements ConsumerInterceptor<String, String> {

    @Override
    public ConsumerRecords<String, String> onConsume(ConsumerRecords<String, String> records) {
        records.forEach(record -> {
            Header header = record.headers().lastHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
            if (header != null) {
                String correlationId = new String(header.value(), StandardCharsets.UTF_8);
                MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, correlationId);
            }
        });
        return records;
    }

    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
        // No-op
    }

    @Override
    public void close() {
        // No-op
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // No-op
    }
}
