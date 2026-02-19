package com.ecommerce.order.saga;

import com.ecommerce.order.saga.steps.InventoryRestoreStep;
import com.ecommerce.order.saga.steps.NotificationStep;
import com.ecommerce.order.saga.steps.RefundStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Factory Pattern: creates the appropriate SagaStep instance based on step name.
 * Centralizes step resolution to avoid scattered switch statements.
 */
@Component
@RequiredArgsConstructor
public class SagaStepFactory {

    private final RefundStep refundStep;
    private final InventoryRestoreStep inventoryRestoreStep;
    private final NotificationStep notificationStep;

    public SagaStep getStep(String stepName) {
        return switch (stepName) {
            case "REFUND" -> refundStep;
            case "INVENTORY_RESTORE" -> inventoryRestoreStep;
            case "NOTIFICATION" -> notificationStep;
            default -> throw new IllegalArgumentException("Unknown saga step: " + stepName);
        };
    }
}
