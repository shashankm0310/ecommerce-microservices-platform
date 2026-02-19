package com.ecommerce.payment.kafka;

import com.ecommerce.common.event.ReturnSagaCommand;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.service.PaymentService;
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
 * Processes INITIATE_REFUND and COMPENSATE_REFUND steps,
 * then publishes replies back to the orchestrator via return-saga-replies topic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReturnSagaCommandConsumer {

    private static final String SAGA_REPLIES_TOPIC = "return-saga-replies";

    private final PaymentService paymentService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "return-saga-commands", groupId = "payment-service-saga-group")
    public void handleCommand(String message) {
        ReturnSagaCommand command = deserialize(message);

        if (command.getStep() == null) {
            log.warn("Received saga command with null step: {}", message);
            return;
        }

        switch (command.getStep()) {
            case INITIATE_REFUND -> handleRefund(command);
            case COMPENSATE_REFUND -> handleCompensateRefund(command);
            default -> log.debug("Ignoring command step {} (not for payment-service)", command.getStep());
        }
    }

    private void handleRefund(ReturnSagaCommand command) {
        log.info("Processing INITIATE_REFUND: sagaId={}, orderId={}", command.getSagaId(), command.getOrderId());

        try {
            Payment payment = paymentService.processRefund(command.getOrderId());
            publishReply(command, "REFUND_COMPLETED", payment.getRefundTransactionId());
        } catch (Exception e) {
            log.error("Refund failed for sagaId={}: {}", command.getSagaId(), e.getMessage());
            publishReply(command, "REFUND_FAILED", null);
        }
    }

    private void handleCompensateRefund(ReturnSagaCommand command) {
        log.warn("COMPENSATE_REFUND received for sagaId={} — refund compensation is a no-op (manual review needed)",
                command.getSagaId());
    }

    private void publishReply(ReturnSagaCommand command, String replyType, String refundTransactionId) {
        Map<String, Object> reply = new HashMap<>();
        reply.put("sagaId", command.getSagaId().toString());
        reply.put("orderId", command.getOrderId().toString());
        reply.put("replyType", replyType);
        if (refundTransactionId != null) {
            reply.put("refundTransactionId", refundTransactionId);
        }

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
