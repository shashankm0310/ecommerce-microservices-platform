package com.ecommerce.common.event;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
    UUID getEventId();
    String getEventType();
    String getAggregateId();
    Instant getTimestamp();
}
