package com.ecommerce.notification.controller;

import com.ecommerce.notification.entity.Notification;
import com.ecommerce.notification.entity.OrderSagaView;
import com.ecommerce.notification.kafka.streams.SagaStateQueryService;
import com.ecommerce.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification and saga status queries")
public class NotificationController {

    private final NotificationService notificationService;
    private final SagaStateQueryService sagaStateQueryService;

    @GetMapping
    @Operation(summary = "Get notifications for current user")
    public ResponseEntity<List<Notification>> getNotifications(
            @RequestHeader("X-User-Id") String userId) {
        List<Notification> notifications = notificationService
                .getNotificationsByUserId(UUID.fromString(userId));
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/order/{orderId}/saga-status")
    @Operation(summary = "Get saga status for an order (CQRS read model from Kafka Streams KTable)")
    public ResponseEntity<?> getSagaStatus(@PathVariable UUID orderId) {
        // First try Kafka Streams Interactive Query (real-time)
        Optional<Map<String, Object>> streamsState = sagaStateQueryService
                .getSagaState(orderId.toString());
        if (streamsState.isPresent()) {
            return ResponseEntity.ok(streamsState.get());
        }

        // Fallback to database view
        Optional<OrderSagaView> dbState = notificationService.getSagaStatus(orderId);
        if (dbState.isPresent()) {
            return ResponseEntity.ok(dbState.get());
        }

        return ResponseEntity.notFound().build();
    }
}
