package com.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnRequestedEvent implements DomainEvent {
    private UUID eventId;
    private UUID orderId;
    private UUID userId;
    private String reason;
    private Instant timestamp;

    @Override
    public String getEventType() {
        return "RETURN_REQUESTED";
    }

    @Override
    public String getAggregateId() {
        return orderId.toString();
    }
}
