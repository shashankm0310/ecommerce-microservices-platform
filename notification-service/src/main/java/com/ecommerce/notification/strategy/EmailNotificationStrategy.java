package com.ecommerce.notification.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Concrete Strategy: sends notifications via email.
 * In production, this would integrate with an SMTP provider or service like SendGrid/SES.
 */
@Component
@Slf4j
public class EmailNotificationStrategy implements NotificationStrategy {

    @Override
    public void send(UUID userId, String subject, String message) {
        log.info("Sending EMAIL notification to userId={}: subject='{}', body='{}'",
                userId, subject, message);
        // Simulate email delivery
        log.info("Email sent successfully to userId={}", userId);
    }

    @Override
    public String getChannel() {
        return "EMAIL";
    }
}
