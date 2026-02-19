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
public class InventoryRestoredEvent implements DomainEvent {
    private UUID eventId;
    private UUID orderId;
    private Instant timestamp;

    @Override
    public String getEventType() {
        return "INVENTORY_RESTORED";
    }

    @Override
    public String getAggregateId() {
        return orderId.toString();
    }
}
