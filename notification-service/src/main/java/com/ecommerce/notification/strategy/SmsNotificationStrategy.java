package com.ecommerce.notification.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Concrete Strategy: sends notifications via SMS.
 * In production, this would integrate with Twilio, AWS SNS, or similar.
 */
@Component
@Slf4j
public class SmsNotificationStrategy implements NotificationStrategy {

    @Override
    public void send(UUID userId, String subject, String message) {
        log.info("Sending SMS notification to userId={}: '{}'", userId, message);
        // Simulate SMS delivery
        log.info("SMS sent successfully to userId={}", userId);
    }

    @Override
    public String getChannel() {
        return "SMS";
    }
}
