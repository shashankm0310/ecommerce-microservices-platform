package com.ecommerce.order.controller;

import com.ecommerce.common.dto.ReturnRequest;
import com.ecommerce.common.dto.ReturnResponse;
import com.ecommerce.order.entity.ReturnSaga;
import com.ecommerce.order.saga.ReturnSagaOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Returns", description = "Order return/refund endpoints")
public class ReturnController {

    private final ReturnSagaOrchestrator sagaOrchestrator;

    @PostMapping("/{orderId}/returns")
    @Operation(summary = "Initiate a return for an order",
               description = "Starts the orchestrator-based return saga: refund → inventory restore → notification")
    public ResponseEntity<ReturnResponse> initiateReturn(
            @PathVariable UUID orderId,
            @Valid @RequestBody ReturnRequest request,
            @RequestHeader("X-User-Id") String userId) {

        log.info("Initiating return for orderId={}, userId={}", orderId, userId);

        ReturnSaga saga = sagaOrchestrator.startSaga(orderId, UUID.fromString(userId), request.getReason());

        ReturnResponse response = ReturnResponse.builder()
                .sagaId(saga.getId())
                .orderId(saga.getOrderId())
                .status(saga.getStatus())
                .reason(saga.getReason())
                .createdAt(saga.getCreatedAt())
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
