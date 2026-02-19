package com.ecommerce.order.controller;

import com.ecommerce.order.dto.OrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Orders", description = "Order management endpoints")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Create a new order", description = "Places a new order for the authenticated user")
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody OrderRequest request,
            @RequestHeader("X-User-Id") String userId) {

        log.info("Received create order request for user: {}", userId);
        OrderResponse response = orderService.createOrder(UUID.fromString(userId), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    @Operation(summary = "Get all orders", description = "Retrieves all orders for the authenticated user")
    public ResponseEntity<List<OrderResponse>> getOrders(
            @RequestHeader("X-User-Id") String userId) {

        log.debug("Fetching orders for user: {}", userId);
        List<OrderResponse> orders = orderService.getOrdersByUserId(UUID.fromString(userId));
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID", description = "Retrieves a specific order by its ID")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String userId) {

        log.debug("Fetching order {} for user: {}", id, userId);
        OrderResponse order = orderService.getOrderById(id, UUID.fromString(userId));
        return ResponseEntity.ok(order);
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Get order status", description = "Retrieves the current status of an order")
    public ResponseEntity<String> getOrderStatus(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String userId) {

        log.debug("Fetching status for order {} for user: {}", id, userId);
        OrderResponse order = orderService.getOrderById(id, UUID.fromString(userId));
        return ResponseEntity.ok(order.getStatus());
    }
}
