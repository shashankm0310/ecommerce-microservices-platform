package com.ecommerce.notification.strategy;

import java.util.UUID;

/**
 * Strategy Pattern: defines a family of notification delivery algorithms (email, SMS, push, etc.)
 * and makes them interchangeable. New channels can be added without modifying existing code.
 */
public interface NotificationStrategy {

    /**
     * Send a notification via this channel.
     */
    void send(UUID userId, String subject, String message);

    /**
     * Returns the channel name this strategy handles.
     */
    String getChannel();
}
