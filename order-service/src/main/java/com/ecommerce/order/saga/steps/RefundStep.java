package com.ecommerce.order.saga.steps;

import com.ecommerce.common.event.ReturnSagaCommand;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.ReturnSaga;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.saga.SagaStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Saga step that initiates a refund via the payment-service.
 * Publishes a command to the return-saga-commands topic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefundStep implements SagaStep {

    private static final String SAGA_COMMANDS_TOPIC = "return-saga-commands";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;

    @Override
    public void execute(ReturnSaga saga) {
        log.info("RefundStep.execute: sagaId={}, orderId={}", saga.getId(), saga.getOrderId());

        Order order = orderRepository.findById(saga.getOrderId()).orElseThrow();

        ReturnSagaCommand command = ReturnSagaCommand.builder()
                .sagaId(saga.getId())
                .orderId(saga.getOrderId())
                .userId(saga.getUserId())
                .step(ReturnSagaCommand.Step.INITIATE_REFUND)
                .amount(order.getTotalAmount())
                .build();

        publishCommand(command);
    }

    @Override
    public void compensate(ReturnSaga saga) {
        log.info("RefundStep.compensate: sagaId={}, orderId={}", saga.getId(), saga.getOrderId());

        ReturnSagaCommand command = ReturnSagaCommand.builder()
                .sagaId(saga.getId())
                .orderId(saga.getOrderId())
                .userId(saga.getUserId())
                .step(ReturnSagaCommand.Step.COMPENSATE_REFUND)
                .build();

        publishCommand(command);
    }

    private void publishCommand(ReturnSagaCommand command) {
        try {
            String payload = objectMapper.writeValueAsString(command);
            kafkaTemplate.send(SAGA_COMMANDS_TOPIC, command.getOrderId().toString(), payload);
            log.info("Published {} command for sagaId={}", command.getStep(), command.getSagaId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish saga command", e);
        }
    }
}
