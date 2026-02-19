package com.ecommerce.payment.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Immutable result of a payment strategy execution.
 */
@Getter
@Builder
@AllArgsConstructor
public class PaymentResult {
    private final boolean success;
    private final String transactionId;
    private final String failureReason;

    public static PaymentResult success(String transactionId) {
        return PaymentResult.builder().success(true).transactionId(transactionId).build();
    }

    public static PaymentResult failure(String reason) {
        return PaymentResult.builder().success(false).failureReason(reason).build();
    }
}
