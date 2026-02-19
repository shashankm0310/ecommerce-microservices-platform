package com.ecommerce.order.service;

import com.ecommerce.common.dto.ProductResponse;
import com.ecommerce.order.client.ProductServiceClient;
import com.ecommerce.order.dto.OrderItemRequest;
import com.ecommerce.order.dto.OrderItemResponse;
import com.ecommerce.order.dto.OrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OutboxEvent;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final ProductServiceClient productServiceClient;

    /**
     * Creates an order after validating products via product-service.
     * If product-service is unavailable (circuit breaker open), the order proceeds
     * with client-supplied data — graceful degradation.
     */
    public OrderResponse createOrder(UUID userId, OrderRequest request) {
        log.info("Creating order for user: {}", userId);

        Order order = Order.builder()
                .userId(userId)
                .status("PENDING")
                .shippingAddress(request.getShippingAddress())
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {
            // Validate product via product-service (with circuit breaker fallback)
            Optional<ProductResponse> productOpt = productServiceClient.getProduct(
                    itemRequest.getProductId().toString());

            String productName = itemRequest.getProductName();
            BigDecimal unitPrice = itemRequest.getUnitPrice();

            if (productOpt.isPresent()) {
                ProductResponse product = productOpt.get();
                productName = product.getName();
                unitPrice = product.getPrice();
                log.debug("Validated product {} with price {}", product.getId(), product.getPrice());
            } else {
                log.info("Using client-supplied product info for productId={}", itemRequest.getProductId());
                if (productName == null) {
                    productName = itemRequest.getProductId().toString();
                }
            }

            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

            OrderItem item = OrderItem.builder()
                    .productId(itemRequest.getProductId())
                    .productName(productName)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(unitPrice)
                    .subtotal(subtotal)
                    .build();

            order.addItem(item);
            totalAmount = totalAmount.add(subtotal);
        }

        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);
        log.info("Order created with id: {}", savedOrder.getId());

        saveOutboxEvent(savedOrder, "ORDER_CREATED");

        return mapToResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId, UUID userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order not found with id: " + orderId + " for user: " + userId));
        return mapToResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUserId(UUID userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public void updateOrderStatus(UUID orderId, String status) {
        log.info("Updating order {} status to {}", orderId, status);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order not found with id: " + orderId));

        String previousStatus = order.getStatus();
        order.setStatus(status);
        orderRepository.save(order);

        log.info("Order {} status updated from {} to {}", orderId, previousStatus, status);
    }

    private void saveOutboxEvent(Order order, String eventType) {
        try {
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("orderId", order.getId().toString());
            eventPayload.put("userId", order.getUserId().toString());
            eventPayload.put("status", order.getStatus());
            eventPayload.put("totalAmount", order.getTotalAmount());
            eventPayload.put("shippingAddress", order.getShippingAddress());
            eventPayload.put("eventType", eventType);
            eventPayload.put("timestamp", LocalDateTime.now().toString());

            List<Map<String, Object>> itemPayloads = order.getItems().stream()
                    .map(item -> {
                        Map<String, Object> itemMap = new HashMap<>();
                        itemMap.put("productId", item.getProductId().toString());
                        itemMap.put("productName", item.getProductName());
                        itemMap.put("quantity", item.getQuantity());
                        itemMap.put("unitPrice", item.getUnitPrice());
                        itemMap.put("subtotal", item.getSubtotal());
                        return itemMap;
                    })
                    .collect(Collectors.toList());
            eventPayload.put("items", itemPayloads);

            String payload = objectMapper.writeValueAsString(eventPayload);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("Order")
                    .aggregateId(order.getId().toString())
                    .eventType(eventType)
                    .payload(payload)
                    .build();

            outboxEventRepository.save(outboxEvent);
            log.debug("Outbox event saved for order {}: {}", order.getId(), eventType);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize order event payload for order: {}", order.getId(), e);
            throw new RuntimeException("Failed to serialize order event payload", e);
        }
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .subtotal(item.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .shippingAddress(order.getShippingAddress())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
