package com.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundCompletedEvent implements DomainEvent {
    private UUID eventId;
    private UUID orderId;
    private UUID userId;
    private BigDecimal refundAmount;
    private String refundTransactionId;
    private Instant timestamp;

    @Override
    public String getEventType() {
        return "REFUND_COMPLETED";
    }

    @Override
    public String getAggregateId() {
        return orderId.toString();
    }
}
