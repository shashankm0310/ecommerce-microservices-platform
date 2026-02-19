package com.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent implements DomainEvent {
    private UUID eventId;
    private UUID orderId;
    private UUID userId;
    private BigDecimal totalAmount;
    private List<OrderItemPayload> items;
    private Instant timestamp;

    @Override
    public String getEventType() {
        return "ORDER_CREATED";
    }

    @Override
    public String getAggregateId() {
        return orderId.toString();
    }
}
