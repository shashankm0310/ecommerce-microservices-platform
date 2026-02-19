package com.ecommerce.payment.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Concrete Strategy: processes payments via digital wallet.
 * Simulates wallet balance check with a lower limit than credit cards.
 */
@Component
@Slf4j
public class WalletPaymentStrategy implements PaymentStrategy {

    private static final BigDecimal WALLET_BALANCE_LIMIT = new BigDecimal("5000");

    @Override
    public PaymentResult process(UUID orderId, UUID userId, BigDecimal amount) {
        log.info("Processing WALLET payment: orderId={}, userId={}, amount={}", orderId, userId, amount);

        if (amount.compareTo(WALLET_BALANCE_LIMIT) > 0) {
            log.warn("Wallet payment declined: amount {} exceeds balance {}", amount, WALLET_BALANCE_LIMIT);
            return PaymentResult.failure("Insufficient wallet balance");
        }

        String transactionId = "WALLET-TXN-" + UUID.randomUUID();
        log.info("Wallet payment completed: orderId={}, txnId={}", orderId, transactionId);
        return PaymentResult.success(transactionId);
    }

    @Override
    public String getPaymentMethod() {
        return "WALLET";
    }
}
