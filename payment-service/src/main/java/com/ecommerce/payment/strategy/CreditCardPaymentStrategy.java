package com.ecommerce.payment.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Concrete Strategy: processes payments via credit card.
 * Simulates card authorization with an amount limit check.
 */
@Component
@Slf4j
public class CreditCardPaymentStrategy implements PaymentStrategy {

    private static final BigDecimal CREDIT_CARD_LIMIT = new BigDecimal("10000");

    @Override
    public PaymentResult process(UUID orderId, UUID userId, BigDecimal amount) {
        log.info("Processing CREDIT_CARD payment: orderId={}, amount={}", orderId, amount);

        if (amount.compareTo(CREDIT_CARD_LIMIT) > 0) {
            log.warn("Credit card payment declined: amount {} exceeds limit {}", amount, CREDIT_CARD_LIMIT);
            return PaymentResult.failure("Credit card limit exceeded");
        }

        String transactionId = "CC-TXN-" + UUID.randomUUID();
        log.info("Credit card payment authorized: orderId={}, txnId={}", orderId, transactionId);
        return PaymentResult.success(transactionId);
    }

    @Override
    public String getPaymentMethod() {
        return "CREDIT_CARD";
    }
}
