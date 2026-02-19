package com.ecommerce.notification.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory Pattern: resolves the correct NotificationStrategy based on channel name.
 * Uses Spring DI to auto-discover all NotificationStrategy beans — adding a new channel
 * (e.g., push notifications) only requires a new @Component, no factory changes.
 */
@Component
public class NotificationStrategyFactory {

    private final Map<String, NotificationStrategy> strategies;

    public NotificationStrategyFactory(List<NotificationStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(NotificationStrategy::getChannel, Function.identity()));
    }

    public NotificationStrategy getStrategy(String channel) {
        NotificationStrategy strategy = strategies.get(channel);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported notification channel: " + channel
                    + ". Available: " + strategies.keySet());
        }
        return strategy;
    }

    /** Returns all available strategies for broadcasting to all channels. */
    public Map<String, NotificationStrategy> getAllStrategies() {
        return strategies;
    }
}
