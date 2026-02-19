package com.ecommerce.notification.kafka.consumer;

import com.ecommerce.notification.entity.OrderSagaView;
import com.ecommerce.notification.service.NotificationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for notification-events and saga state tracking.
 *
 * Error handling: exceptions propagate to the DLQ error handler for retry + dead-letter.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "notification-events", groupId = "notification-service-group")
    public void handleNotificationEvent(String message) {
        Map<String, Object> event = deserialize(message);
        String notificationType = (String) event.get("notificationType");
        String orderId = (String) event.get("orderId");
        String userId = (String) event.get("userId");
        String notificationMessage = (String) event.get("message");

        log.info("Received notification event: type={}, orderId={}", notificationType, orderId);

        if (orderId != null && userId != null) {
            notificationService.createNotification(
                    UUID.fromString(orderId),
                    UUID.fromString(userId),
                    notificationType,
                    notificationMessage
            );
        }
    }

    @KafkaListener(topics = {"order-events", "inventory-events", "payment-events"},
                    groupId = "notification-saga-view-group")
    public void handleSagaEvent(String message) {
        Map<String, Object> event = deserialize(message);
        String eventType = (String) event.get("eventType");
        String orderId = (String) event.get("orderId");
        String userId = (String) event.get("userId");

        if (orderId == null) return;

        String status = switch (eventType) {
            case "ORDER_CREATED" -> "PENDING";
            case "INVENTORY_RESERVED" -> "INVENTORY_RESERVED";
            case "INVENTORY_RESERVATION_FAILED" -> "CANCELLED";
            case "PAYMENT_COMPLETED" -> "CONFIRMED";
            case "PAYMENT_FAILED" -> "CANCELLED";
            default -> null;
        };

        if (status == null) return;

        OrderSagaView sagaView = OrderSagaView.builder()
                .orderId(UUID.fromString(orderId))
                .userId(userId != null ? UUID.fromString(userId) : null)
                .currentStatus(status)
                .failureReason((String) event.get("reason"))
                .transactionId((String) event.get("transactionId"))
                .eventHistory(eventType + " at " + LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        if (event.get("totalAmount") != null) {
            sagaView.setTotalAmount(new java.math.BigDecimal(event.get("totalAmount").toString()));
        }

        notificationService.upsertSagaView(sagaView);
        log.info("Updated saga view for order {}: status={}", orderId, status);
    }

    private Map<String, Object> deserialize(String message) {
        try {
            return objectMapper.readValue(message, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event", e);
        }
    }
}
