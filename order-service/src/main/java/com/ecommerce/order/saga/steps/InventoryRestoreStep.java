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

import java.util.List;
import java.util.stream.Collectors;

/**
 * Saga step that restores inventory for returned order items.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryRestoreStep implements SagaStep {

    private static final String SAGA_COMMANDS_TOPIC = "return-saga-commands";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;

    @Override
    public void execute(ReturnSaga saga) {
        log.info("InventoryRestoreStep.execute: sagaId={}, orderId={}", saga.getId(), saga.getOrderId());

        Order order = orderRepository.findById(saga.getOrderId()).orElseThrow();

        List<ReturnSagaCommand.ItemPayload> items = order.getItems().stream()
                .map(item -> ReturnSagaCommand.ItemPayload.builder()
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .build())
                .collect(Collectors.toList());

        ReturnSagaCommand command = ReturnSagaCommand.builder()
                .sagaId(saga.getId())
                .orderId(saga.getOrderId())
                .userId(saga.getUserId())
                .step(ReturnSagaCommand.Step.RESTORE_INVENTORY)
                .items(items)
                .build();

        publishCommand(command);
    }

    @Override
    public void compensate(ReturnSaga saga) {
        log.warn("InventoryRestoreStep.compensate: no compensation needed for inventory restore (idempotent)");
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
