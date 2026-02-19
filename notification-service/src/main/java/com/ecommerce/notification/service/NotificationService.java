package com.ecommerce.notification.service;

import com.ecommerce.notification.entity.Notification;
import com.ecommerce.notification.entity.OrderSagaView;
import com.ecommerce.notification.repository.NotificationRepository;
import com.ecommerce.notification.repository.OrderSagaViewRepository;
import com.ecommerce.notification.strategy.NotificationStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final OrderSagaViewRepository orderSagaViewRepository;
    private final NotificationStrategyFactory notificationStrategyFactory;

    @Transactional
    public Notification createNotification(UUID orderId, UUID userId, String type, String message) {
        Notification notification = Notification.builder()
                .orderId(orderId)
                .userId(userId)
                .notificationType(type)
                .message(message)
                .status("PENDING")
                .build();

        notification = notificationRepository.save(notification);
        log.info("Created notification for order {} type {}", orderId, type);

        // Dispatch via all registered notification strategies (email, SMS, etc.)
        notificationStrategyFactory.getAllStrategies().forEach((channel, strategy) -> {
            try {
                strategy.send(userId, type, message);
            } catch (Exception e) {
                log.warn("Failed to send notification via {}: {}", channel, e.getMessage());
            }
        });

        notification.setStatus("SENT");
        notification.setSentAt(LocalDateTime.now());
        notification = notificationRepository.save(notification);
        log.info("Sent notification {} for order {}", notification.getId(), orderId);

        return notification;
    }

    @Transactional(readOnly = true)
    public List<Notification> getNotificationsByUserId(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void upsertSagaView(OrderSagaView sagaView) {
        Optional<OrderSagaView> existing = orderSagaViewRepository.findById(sagaView.getOrderId());
        if (existing.isPresent()) {
            OrderSagaView view = existing.get();
            view.setCurrentStatus(sagaView.getCurrentStatus());
            view.setFailureReason(sagaView.getFailureReason());
            view.setTransactionId(sagaView.getTransactionId());
            String history = view.getEventHistory() == null ? "" : view.getEventHistory();
            view.setEventHistory(history + "\n" + sagaView.getEventHistory());
            orderSagaViewRepository.save(view);
        } else {
            orderSagaViewRepository.save(sagaView);
        }
    }

    @Transactional(readOnly = true)
    public Optional<OrderSagaView> getSagaStatus(UUID orderId) {
        return orderSagaViewRepository.findById(orderId);
    }
}
