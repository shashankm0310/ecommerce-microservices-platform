package com.ecommerce.inventory.kafka;

import com.ecommerce.common.event.ReturnSagaCommand;
import com.ecommerce.inventory.service.InventoryService;
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
 * Processes RESTORE_INVENTORY step by restoring stock for each item,
 * then publishes INVENTORY_RESTORED reply back to the orchestrator.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReturnSagaCommandConsumer {

    private static final String SAGA_REPLIES_TOPIC = "return-saga-replies";

    private final InventoryService inventoryService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "return-saga-commands", groupId = "inventory-service-saga-group")
    public void handleCommand(String message) {
        ReturnSagaCommand command = deserialize(message);

        if (command.getStep() == null) {
            log.warn("Received saga command with null step: {}", message);
            return;
        }

        if (command.getStep() == ReturnSagaCommand.Step.RESTORE_INVENTORY) {
            handleRestoreInventory(command);
        } else {
            log.debug("Ignoring command step {} (not for inventory-service)", command.getStep());
        }
    }

    private void handleRestoreInventory(ReturnSagaCommand command) {
        log.info("Processing RESTORE_INVENTORY: sagaId={}, orderId={}, items={}",
                command.getSagaId(), command.getOrderId(),
                command.getItems() != null ? command.getItems().size() : 0);

        try {
            if (command.getItems() != null) {
                for (ReturnSagaCommand.ItemPayload item : command.getItems()) {
                    inventoryService.restoreStock(
                            command.getOrderId(),
                            item.getProductId(),
                            item.getQuantity()
                    );
                }
            }
            publishReply(command, "INVENTORY_RESTORED");
        } catch (Exception e) {
            log.error("Inventory restore failed for sagaId={}: {}", command.getSagaId(), e.getMessage());
            publishReply(command, "INVENTORY_RESTORE_FAILED");
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
