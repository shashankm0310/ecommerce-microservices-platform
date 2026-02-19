package com.ecommerce.payment.kafka;

import com.ecommerce.common.event.PaymentCompletedEvent;
import com.ecommerce.common.event.PaymentFailedEvent;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.service.PaymentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer that listens for inventory-events and triggers payment processing.
 *
 * Error handling: exceptions propagate to the DLQ error handler for retry + dead-letter.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {

    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    private final PaymentService paymentService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "inventory-events", groupId = "payment-service-group")
    public void handleInventoryEvent(String message) {
        log.info("Received inventory event: {}", message);

        Map<String, Object> event = deserialize(message);
        String eventType = (String) event.get("eventType");

        if ("INVENTORY_RESERVED".equals(eventType)) {
            handleInventoryReserved(event);
        } else {
            log.info("Ignoring inventory event of type: {}", eventType);
        }
    }

    private void handleInventoryReserved(Map<String, Object> event) {
        UUID orderId = UUID.fromString((String) event.get("orderId"));
        UUID userId = UUID.fromString((String) event.get("userId"));
        BigDecimal totalAmount = new BigDecimal(event.get("totalAmount").toString());

        log.info("Processing payment for reserved inventory: orderId={}, userId={}, totalAmount={}",
                orderId, userId, totalAmount);

        Payment payment = paymentService.processPayment(orderId, userId, totalAmount);

        if ("COMPLETED".equals(payment.getStatus())) {
            publishPaymentCompletedEvent(payment);
        } else if ("FAILED".equals(payment.getStatus())) {
            publishPaymentFailedEvent(payment);
        }
    }

    private void publishPaymentCompletedEvent(Payment payment) {
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .transactionId(payment.getTransactionId())
                .timestamp(java.time.Instant.now())
                .build();

        String payload = serialize(event);
        kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, payment.getOrderId().toString(), payload);
        log.info("Published PAYMENT_COMPLETED event for orderId={}", payment.getOrderId());
    }

    private void publishPaymentFailedEvent(Payment payment) {
        PaymentFailedEvent event = PaymentFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .reason(payment.getFailureReason())
                .timestamp(java.time.Instant.now())
                .build();

        String payload = serialize(event);
        kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, payment.getOrderId().toString(), payload);
        log.info("Published PAYMENT_FAILED event for orderId={}", payment.getOrderId());
    }

    private Map<String, Object> deserialize(String message) {
        try {
            return objectMapper.readValue(message, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize inventory event", e);
        }
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize payment event", e);
        }
    }
}
