package com.ecommerce.payment.strategy;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Strategy Pattern: defines a family of payment algorithms (credit card, wallet, etc.)
 * and makes them interchangeable. New payment methods can be added without modifying
 * existing code (Open/Closed Principle).
 */
public interface PaymentStrategy {

    /**
     * Process a payment using this strategy's implementation.
     *
     * @return a transaction ID on success
     * @throws PaymentProcessingException if the payment cannot be processed
     */
    PaymentResult process(UUID orderId, UUID userId, BigDecimal amount);

    /**
     * Returns the payment method name this strategy handles.
     */
    String getPaymentMethod();
}
