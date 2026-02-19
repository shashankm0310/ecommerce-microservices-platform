package com.ecommerce.common.kafka;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;

/**
 * Auto-configuration that registers the DLQ error handler for all Kafka consumers.
 * Only activates when spring-kafka is on the classpath (ConditionalOnClass).
 */
@AutoConfiguration
@ConditionalOnClass(KafkaOperations.class)
public class KafkaErrorAutoConfiguration {

    @Bean
    public DefaultErrorHandler kafkaDlqErrorHandler(KafkaOperations<String, String> kafkaOperations) {
        return DlqErrorHandler.create(kafkaOperations);
    }
}
