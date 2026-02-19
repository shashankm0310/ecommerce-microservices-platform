package com.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSagaState {
    private UUID orderId;
    private UUID userId;
    private String currentStatus;
    private BigDecimal totalAmount;
    private String failureReason;
    private String transactionId;
    @Builder.Default
    private List<String> eventHistory = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    public void addEvent(String eventType) {
        if (eventHistory == null) {
            eventHistory = new ArrayList<>();
        }
        eventHistory.add(eventType + " at " + Instant.now());
    }
}
