package com.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Command object published by the Return Saga Orchestrator to tell participant
 * services what step to execute. This is the key difference from choreography:
 * the orchestrator explicitly tells each service what to do.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnSagaCommand {

    public enum Step {
        INITIATE_REFUND,
        RESTORE_INVENTORY,
        SEND_NOTIFICATION,
        COMPENSATE_REFUND
    }

    private UUID sagaId;
    private UUID orderId;
    private UUID userId;
    private Step step;
    private BigDecimal amount;
    private String transactionId;
    private List<ItemPayload> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemPayload {
        private UUID productId;
        private Integer quantity;
    }
}
