package com.ecommerce.notification.kafka.consumer;

import com.ecommerce.common.event.ReturnSagaCommand;
import com.ecommerce.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles return saga commands from the orchestrator.
 * Processes SEND_NOTIFICATION step by creating a return notification,
 * then publishes NOTIFICATION_SENT reply back to the orchestrator.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReturnSagaCommandConsumer {

    private static final String SAGA_REPLIES_TOPIC = "return-saga-replies";

    private final NotificationService notificationService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "return-saga-commands", groupId = "notification-service-saga-group")
    public void handleCommand(String message) {
        ReturnSagaCommand command = deserialize(message);

        if (command.getStep() == null) {
            log.warn("Received saga command with null step: {}", message);
            return;
        }

        if (command.getStep() == ReturnSagaCommand.Step.SEND_NOTIFICATION) {
            handleSendNotification(command);
        } else {
            log.debug("Ignoring command step {} (not for notification-service)", command.getStep());
        }
    }

    private void handleSendNotification(ReturnSagaCommand command) {
        log.info("Processing SEND_NOTIFICATION: sagaId={}, orderId={}", command.getSagaId(), command.getOrderId());

        try {
            notificationService.createNotification(
                    command.getOrderId(),
                    command.getUserId(),
                    "RETURN_COMPLETED",
                    "Your return for order " + command.getOrderId() + " has been processed. Refund will be credited shortly."
            );
            publishReply(command, "NOTIFICATION_SENT");
        } catch (Exception e) {
            log.error("Notification failed for sagaId={}: {}", command.getSagaId(), e.getMessage());
            // Notification failure is non-critical — still mark as sent to avoid blocking the saga
            publishReply(command, "NOTIFICATION_SENT");
        }
    }

    private void publishReply(ReturnSagaCommand command, String replyType) {
        Map<String, Object> reply = new HashMap<>();
        reply.put("sagaId", command.getSagaId().toString());
        reply.put("orderId", command.getOrderId().toString());
        reply.put("replyType", replyType);

        try {
            String payload = objectMapper.writeValueAsString(reply);
            kafkaTemplate.send(SAGA_REPLIES_TOPIC, command.getOrderId().toString(), payload);
            log.info("Published saga reply: sagaId={}, replyType={}", command.getSagaId(), replyType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish saga reply", e);
        }
    }

    private ReturnSagaCommand deserialize(String message) {
        try {
            return objectMapper.readValue(message, ReturnSagaCommand.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize saga command", e);
        }
    }
}
