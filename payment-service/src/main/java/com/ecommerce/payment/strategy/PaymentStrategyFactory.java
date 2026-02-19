package com.ecommerce.payment.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory Pattern: resolves the correct PaymentStrategy based on the payment method name.
 * Uses Spring's dependency injection to collect all PaymentStrategy beans automatically,
 * so adding a new strategy only requires creating a new @Component — no factory changes needed.
 */
@Component
public class PaymentStrategyFactory {

    private final Map<String, PaymentStrategy> strategies;

    public PaymentStrategyFactory(List<PaymentStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(PaymentStrategy::getPaymentMethod, Function.identity()));
    }

    public PaymentStrategy getStrategy(String paymentMethod) {
        PaymentStrategy strategy = strategies.get(paymentMethod);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported payment method: " + paymentMethod
                    + ". Available: " + strategies.keySet());
        }
        return strategy;
    }
}
