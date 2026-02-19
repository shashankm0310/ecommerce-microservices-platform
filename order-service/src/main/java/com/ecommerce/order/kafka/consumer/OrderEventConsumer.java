package com.ecommerce.order.kafka.consumer;

import com.ecommerce.order.service.OrderService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer that listens for inventory and payment events to update order status.
 *
 * Error handling: exceptions propagate to the DLQ error handler for retry + dead-letter.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "inventory-events", groupId = "order-service-group")
    public void handleInventoryEvent(String message) {
        log.info("Received inventory event: {}", message);

        Map<String, Object> event = deserialize(message);
        String eventType = (String) event.get("eventType");
        String orderId = (String) event.get("orderId");

        if (orderId == null || eventType == null) {
            throw new IllegalArgumentException("Received inventory event with missing orderId or eventType");
        }

        switch (eventType) {
            case "INVENTORY_RESERVED" -> {
                log.info("Inventory reserved for order: {}", orderId);
                orderService.updateOrderStatus(UUID.fromString(orderId), "INVENTORY_RESERVED");
            }
            case "INVENTORY_RESERVATION_FAILED" -> {
                log.warn("Inventory reservation failed for order: {}", orderId);
                orderService.updateOrderStatus(UUID.fromString(orderId), "CANCELLED");
            }
            default -> log.warn("Unknown inventory event type: {}", eventType);
        }
    }

    @KafkaListener(topics = "payment-events", groupId = "order-service-group")
    public void handlePaymentEvent(String message) {
        log.info("Received payment event: {}", message);

        Map<String, Object> event = deserialize(message);
        String eventType = (String) event.get("eventType");
        String orderId = (String) event.get("orderId");

        if (orderId == null || eventType == null) {
            throw new IllegalArgumentException("Received payment event with missing orderId or eventType");
        }

        switch (eventType) {
            case "PAYMENT_COMPLETED" -> {
                log.info("Payment completed for order: {}", orderId);
                orderService.updateOrderStatus(UUID.fromString(orderId), "CONFIRMED");
            }
            case "PAYMENT_FAILED" -> {
                log.warn("Payment failed for order: {}", orderId);
                orderService.updateOrderStatus(UUID.fromString(orderId), "CANCELLED");
            }
            default -> log.warn("Unknown payment event type: {}", eventType);
        }
    }

    private Map<String, Object> deserialize(String message) {
        try {
            return objectMapper.readValue(message, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event", e);
        }
    }
}
