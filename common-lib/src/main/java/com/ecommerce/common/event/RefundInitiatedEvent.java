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
public class RefundInitiatedEvent implements DomainEvent {
    private UUID eventId;
    private UUID orderId;
    private UUID userId;
    private BigDecimal amount;
    private Instant timestamp;

    @Override
    public String getEventType() {
        return "REFUND_INITIATED";
    }

    @Override
    public String getAggregateId() {
        return orderId.toString();
    }
}
