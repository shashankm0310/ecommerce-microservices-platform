package com.ecommerce.order.saga;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.ReturnSaga;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.ReturnSagaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Orchestrator-based Saga for the return/refund flow.
 *
 * State machine: INITIATED → REFUND_PENDING → REFUND_COMPLETED → INVENTORY_RESTORED → COMPLETED
 *                          → FAILED (at any point, with compensation)
 *
 * Contrast with the choreography saga (order creation):
 * - Choreography: each service listens to events and decides what to do next (decentralized)
 * - Orchestration: this orchestrator tells each service what to do via commands (centralized)
 *
 * Trade-offs:
 * - Orchestration adds a single point of coordination but makes the flow explicit and debuggable
 * - Choreography is more decoupled but harder to trace and reason about
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReturnSagaOrchestrator {

    private final ReturnSagaRepository returnSagaRepository;
    private final OrderRepository orderRepository;
    private final SagaStepFactory sagaStepFactory;
    private final ObjectMapper objectMapper;

    /**
     * Starts a new return saga for a confirmed order.
     */
    @Transactional
    public ReturnSaga startSaga(UUID orderId, UUID userId, String reason) {
        log.info("Starting return saga for orderId={}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (!"CONFIRMED".equals(order.getStatus())) {
            throw new IllegalStateException("Order must be CONFIRMED to initiate return. Current: " + order.getStatus());
        }

        if (returnSagaRepository.existsByOrderIdAndStatusNot(orderId, "FAILED")) {
            throw new IllegalStateException("A return saga already exists for order: " + orderId);
        }

        ReturnSaga saga = ReturnSaga.builder()
                .orderId(orderId)
                .userId(userId)
                .status("INITIATED")
                .reason(reason)
                .currentStep("REFUND")
                .build();

        saga = returnSagaRepository.save(saga);
        log.info("Return saga created: sagaId={}, orderId={}", saga.getId(), orderId);

        // Start first step: initiate refund
        sagaStepFactory.getStep("REFUND").execute(saga);

        saga.setStatus("REFUND_PENDING");
        return returnSagaRepository.save(saga);
    }

    /**
     * Handles replies from participant services via the return-saga-replies topic.
     * Advances the saga state machine based on the reply type.
     */
    @KafkaListener(topics = "return-saga-replies", groupId = "order-service-saga-group")
    @Transactional
    public void handleSagaReply(String message) {
        Map<String, Object> reply = deserialize(message);
        String replyType = (String) reply.get("replyType");
        String sagaIdStr = (String) reply.get("sagaId");

        if (sagaIdStr == null || replyType == null) {
            log.warn("Invalid saga reply: {}", message);
            return;
        }

        UUID sagaId = UUID.fromString(sagaIdStr);
        ReturnSaga saga = returnSagaRepository.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("Saga not found: {}", sagaId);
            return;
        }

        log.info("Processing saga reply: sagaId={}, replyType={}, currentStatus={}",
                sagaId, replyType, saga.getStatus());

        switch (replyType) {
            case "REFUND_COMPLETED" -> {
                saga.setStatus("REFUND_COMPLETED");
                saga.setCurrentStep("INVENTORY_RESTORE");
                returnSagaRepository.save(saga);
                sagaStepFactory.getStep("INVENTORY_RESTORE").execute(saga);
            }
            case "REFUND_FAILED" -> {
                saga.setStatus("FAILED");
                saga.setCurrentStep(null);
                returnSagaRepository.save(saga);
                log.error("Return saga FAILED at refund step: sagaId={}", sagaId);
            }
            case "INVENTORY_RESTORED" -> {
                saga.setStatus("INVENTORY_RESTORED");
                saga.setCurrentStep("NOTIFICATION");
                returnSagaRepository.save(saga);
                sagaStepFactory.getStep("NOTIFICATION").execute(saga);

                // Update order status to RETURNED
                orderRepository.findById(saga.getOrderId()).ifPresent(order -> {
                    order.setStatus("RETURNED");
                    orderRepository.save(order);
                });
            }
            case "NOTIFICATION_SENT" -> {
                saga.setStatus("COMPLETED");
                saga.setCurrentStep(null);
                returnSagaRepository.save(saga);
                log.info("Return saga COMPLETED: sagaId={}, orderId={}", sagaId, saga.getOrderId());
            }
            default -> log.warn("Unknown saga reply type: {}", replyType);
        }
    }

    private Map<String, Object> deserialize(String message) {
        try {
            return objectMapper.readValue(message, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize saga reply", e);
        }
    }
}
