package com.ecommerce.order.saga.steps;

import com.ecommerce.common.event.ReturnSagaCommand;
import com.ecommerce.order.entity.ReturnSaga;
import com.ecommerce.order.saga.SagaStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Saga step that sends a notification about the return/refund status.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationStep implements SagaStep {

    private static final String SAGA_COMMANDS_TOPIC = "return-saga-commands";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void execute(ReturnSaga saga) {
        log.info("NotificationStep.execute: sagaId={}, orderId={}", saga.getId(), saga.getOrderId());

        ReturnSagaCommand command = ReturnSagaCommand.builder()
                .sagaId(saga.getId())
                .orderId(saga.getOrderId())
                .userId(saga.getUserId())
                .step(ReturnSagaCommand.Step.SEND_NOTIFICATION)
                .build();

        publishCommand(command);
    }

    @Override
    public void compensate(ReturnSaga saga) {
        log.warn("NotificationStep.compensate: notifications are not compensatable (sent is sent)");
    }

    private void publishCommand(ReturnSagaCommand command) {
        try {
            String payload = objectMapper.writeValueAsString(command);
            kafkaTemplate.send(SAGA_COMMANDS_TOPIC, command.getOrderId().toString(), payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish saga command", e);
        }
    }
}
