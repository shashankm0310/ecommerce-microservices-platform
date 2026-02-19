package com.ecommerce.notification.kafka.streams;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
@Slf4j
public class OrderSagaStreamsTopology {

    private static final String ORDER_EVENTS = "order-events";
    private static final String INVENTORY_EVENTS = "inventory-events";
    private static final String PAYMENT_EVENTS = "payment-events";
    private static final String NOTIFICATION_EVENTS = "notification-events";
    public static final String SAGA_STORE = "order-saga-store";

    private final ObjectMapper objectMapper;

    public OrderSagaStreamsTopology() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Autowired
    public void buildTopology(StreamsBuilder streamsBuilder) {
        // Consume from all three event topics
        KStream<String, String> orderEvents = streamsBuilder.stream(
                ORDER_EVENTS, Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> inventoryEvents = streamsBuilder.stream(
                INVENTORY_EVENTS, Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> paymentEvents = streamsBuilder.stream(
                PAYMENT_EVENTS, Consumed.with(Serdes.String(), Serdes.String()));

        // Merge all event streams
        KStream<String, String> allEvents = orderEvents
                .merge(inventoryEvents)
                .merge(paymentEvents);

        // Re-key by orderId (extracted from event payload)
        KStream<String, String> keyedEvents = allEvents.selectKey((key, value) -> {
            try {
                Map<String, Object> event = objectMapper.readValue(value, Map.class);
                Object orderId = event.get("orderId");
                return orderId != null ? orderId.toString() : key;
            } catch (JsonProcessingException e) {
                log.error("Failed to parse event for key extraction: {}", e.getMessage());
                return key != null ? key : "unknown";
            }
        });

        // Aggregate into KTable (CQRS read model)
        KTable<String, String> sagaStateTable = keyedEvents.groupByKey(
                Grouped.with(Serdes.String(), Serdes.String())
        ).aggregate(
                // Initializer: empty saga state
                () -> {
                    Map<String, Object> initial = new HashMap<>();
                    initial.put("eventHistory", new ArrayList<String>());
                    initial.put("currentStatus", "UNKNOWN");
                    initial.put("createdAt", Instant.now().toString());
                    try {
                        return objectMapper.writeValueAsString(initial);
                    } catch (JsonProcessingException e) {
                        return "{}";
                    }
                },
                // Aggregator: update saga state based on incoming event
                (orderId, eventJson, currentStateJson) -> {
                    try {
                        Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
                        Map<String, Object> state = objectMapper.readValue(currentStateJson, Map.class);

                        String eventType = (String) event.get("eventType");
                        if (eventType == null) {
                            eventType = deriveEventType(event);
                        }

                        state.put("orderId", orderId);
                        state.putIfAbsent("userId", event.get("userId"));
                        state.put("updatedAt", Instant.now().toString());

                        // Update status based on event type
                        switch (eventType) {
                            case "ORDER_CREATED":
                                state.put("currentStatus", "PENDING");
                                state.put("totalAmount", event.get("totalAmount"));
                                state.put("userId", event.get("userId"));
                                break;
                            case "INVENTORY_RESERVED":
                                state.put("currentStatus", "INVENTORY_RESERVED");
                                break;
                            case "INVENTORY_RESERVATION_FAILED":
                                state.put("currentStatus", "CANCELLED");
                                state.put("failureReason", event.get("reason"));
                                break;
                            case "PAYMENT_COMPLETED":
                                state.put("currentStatus", "CONFIRMED");
                                state.put("transactionId", event.get("transactionId"));
                                break;
                            case "PAYMENT_FAILED":
                                state.put("currentStatus", "CANCELLED");
                                state.put("failureReason", event.get("reason"));
                                break;
                            default:
                                log.warn("Unknown event type: {}", eventType);
                        }

                        // Append to event history
                        List<String> history = (List<String>) state.getOrDefault("eventHistory", new ArrayList<>());
                        history.add(eventType + " at " + Instant.now());
                        state.put("eventHistory", history);

                        return objectMapper.writeValueAsString(state);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to aggregate saga state: {}", e.getMessage());
                        return currentStateJson;
                    }
                },
                // Materialized store for Interactive Queries
                Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as(SAGA_STORE)
                        .withKeySerde(Serdes.String())
                        .withValueSerde(Serdes.String())
        );

        // Filter terminal states and produce notification events
        sagaStateTable.toStream()
                .filter((orderId, stateJson) -> {
                    try {
                        Map<String, Object> state = objectMapper.readValue(stateJson, Map.class);
                        String status = (String) state.get("currentStatus");
                        return "CONFIRMED".equals(status) || "CANCELLED".equals(status);
                    } catch (JsonProcessingException e) {
                        return false;
                    }
                })
                .mapValues((orderId, stateJson) -> {
                    try {
                        Map<String, Object> state = objectMapper.readValue(stateJson, Map.class);
                        String status = (String) state.get("currentStatus");

                        Map<String, Object> notification = new HashMap<>();
                        notification.put("eventId", UUID.randomUUID().toString());
                        notification.put("orderId", orderId);
                        notification.put("userId", state.get("userId"));
                        notification.put("timestamp", Instant.now().toString());

                        if ("CONFIRMED".equals(status)) {
                            notification.put("notificationType", "ORDER_CONFIRMED");
                            notification.put("message", String.format(
                                    "Your order %s has been confirmed. Transaction: %s",
                                    orderId, state.get("transactionId")));
                        } else {
                            notification.put("notificationType", "ORDER_CANCELLED");
                            notification.put("message", String.format(
                                    "Your order %s has been cancelled. Reason: %s",
                                    orderId, state.get("failureReason")));
                        }

                        return objectMapper.writeValueAsString(notification);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to create notification event: {}", e.getMessage());
                        return stateJson;
                    }
                })
                // Re-key by userId for the notification-events topic
                .selectKey((orderId, notificationJson) -> {
                    try {
                        Map<String, Object> notification = objectMapper.readValue(notificationJson, Map.class);
                        Object userId = notification.get("userId");
                        return userId != null ? userId.toString() : orderId;
                    } catch (JsonProcessingException e) {
                        return orderId;
                    }
                })
                .to(NOTIFICATION_EVENTS, Produced.with(Serdes.String(), Serdes.String()));

        log.info("Kafka Streams topology built: merging order/inventory/payment events → saga KTable → notification events");
    }

    private String deriveEventType(Map<String, Object> event) {
        if (event.containsKey("transactionId") && event.containsKey("amount")) {
            return "PAYMENT_COMPLETED";
        }
        if (event.containsKey("reason") && event.containsKey("orderId")) {
            if (event.toString().toLowerCase().contains("payment")) {
                return "PAYMENT_FAILED";
            }
            return "INVENTORY_RESERVATION_FAILED";
        }
        if (event.containsKey("items")) {
            return "ORDER_CREATED";
        }
        if (event.containsKey("totalAmount") && !event.containsKey("items")) {
            return "INVENTORY_RESERVED";
        }
        return "UNKNOWN";
    }
}
