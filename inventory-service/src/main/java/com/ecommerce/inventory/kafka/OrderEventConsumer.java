package com.ecommerce.inventory.kafka;

import com.ecommerce.inventory.service.InventoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer that listens for order-events and reserves inventory.
 *
 * Error handling strategy: exceptions are NOT caught here — they propagate to the
 * DLQ error handler (DefaultErrorHandler with DeadLetterPublishingRecoverer) which
 * retries 3 times with exponential backoff, then publishes to order-events.DLT.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final InventoryService inventoryService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String INVENTORY_EVENTS_TOPIC = "inventory-events";

    @KafkaListener(topics = "order-events", groupId = "inventory-service-group")
    public void handleOrderEvent(String message) {
        log.info("Received order event: {}", message);

        Map<String, Object> event = deserialize(message);

        String eventType = (String) event.get("eventType");
        String orderId = (String) event.get("orderId");
        String userId = (String) event.get("userId");
        Object totalAmount = event.get("totalAmount");

        if ("ORDER_CREATED".equals(eventType)) {
            handleOrderCreated(orderId, userId, totalAmount, event);
        } else {
            log.debug("Ignoring event type: {}", eventType);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleOrderCreated(String orderId, String userId, Object totalAmount, Map<String, Object> event) {
        UUID orderUuid = UUID.fromString(orderId);

        List<Map<String, Object>> items = (List<Map<String, Object>>) event.get("items");
        if (items == null || items.isEmpty()) {
            log.warn("ORDER_CREATED event has no items: orderId={}", orderId);
            return;
        }

        List<Map<String, Object>> reservedItems = new ArrayList<>();
        boolean allReserved = true;

        for (Map<String, Object> item : items) {
            UUID productId = UUID.fromString((String) item.get("productId"));
            Integer quantity = (Integer) item.get("quantity");

            boolean reserved = inventoryService.reserveStock(orderUuid, productId, quantity);

            if (reserved) {
                reservedItems.add(item);
            } else {
                log.warn("Failed to reserve stock: orderId={}, productId={}, quantity={}",
                        orderId, productId, quantity);
                allReserved = false;
                break;
            }
        }

        if (allReserved) {
            publishInventoryReservedEvent(orderId, userId, totalAmount, items);
        } else {
            inventoryService.releaseStock(orderUuid);
            publishInventoryReservationFailedEvent(orderId, userId, items);
        }
    }

    private void publishInventoryReservedEvent(String orderId, String userId, Object totalAmount,
                                                List<Map<String, Object>> items) {
        Map<String, Object> inventoryEvent = new HashMap<>();
        inventoryEvent.put("eventType", "INVENTORY_RESERVED");
        inventoryEvent.put("orderId", orderId);
        inventoryEvent.put("userId", userId);
        inventoryEvent.put("totalAmount", totalAmount);
        inventoryEvent.put("items", items);
        inventoryEvent.put("timestamp", System.currentTimeMillis());

        String payload = serialize(inventoryEvent);
        kafkaTemplate.send(INVENTORY_EVENTS_TOPIC, orderId, payload);
        log.info("Published INVENTORY_RESERVED event: orderId={}", orderId);
    }

    private void publishInventoryReservationFailedEvent(String orderId, String userId,
                                                         List<Map<String, Object>> items) {
        Map<String, Object> inventoryEvent = new HashMap<>();
        inventoryEvent.put("eventType", "INVENTORY_RESERVATION_FAILED");
        inventoryEvent.put("orderId", orderId);
        inventoryEvent.put("userId", userId);
        inventoryEvent.put("items", items);
        inventoryEvent.put("reason", "Insufficient stock for one or more items");
        inventoryEvent.put("timestamp", System.currentTimeMillis());

        String payload = serialize(inventoryEvent);
        kafkaTemplate.send(INVENTORY_EVENTS_TOPIC, orderId, payload);
        log.info("Published INVENTORY_RESERVATION_FAILED event: orderId={}", orderId);
    }

    private Map<String, Object> deserialize(String message) {
        try {
            return objectMapper.readValue(message, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize order event", e);
        }
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize inventory event", e);
        }
    }
}
