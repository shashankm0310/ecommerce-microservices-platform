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
public class NotificationEvent implements DomainEvent {
    private UUID eventId;
    private UUID orderId;
    private UUID userId;
    private String notificationType;
    private String message;
    private Instant timestamp;

    @Override
    public String getEventType() {
        return notificationType;
    }

    @Override
    public String getAggregateId() {
        return userId.toString();
    }
}
