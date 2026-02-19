package com.ecommerce.payment.service;

import com.ecommerce.common.dto.PaymentResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.strategy.PaymentResult;
import com.ecommerce.payment.strategy.PaymentStrategyFactory;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final String DEFAULT_PAYMENT_METHOD = "CREDIT_CARD";

    private final PaymentRepository paymentRepository;
    private final PaymentStrategyFactory paymentStrategyFactory;

    @Transactional
    public Payment processPayment(UUID orderId, UUID userId, BigDecimal amount) {
        return processPayment(orderId, userId, amount, DEFAULT_PAYMENT_METHOD);
    }

    /**
     * Processes a payment using the Strategy Pattern.
     * The PaymentStrategyFactory resolves the correct strategy (credit card, wallet, etc.)
     * based on the paymentMethod parameter, keeping this method closed for modification
     * but open for extension when new payment methods are added.
     */
    @Transactional
    public Payment processPayment(UUID orderId, UUID userId, BigDecimal amount, String paymentMethod) {
        log.info("Processing payment for orderId={}, userId={}, amount={}, method={}",
                orderId, userId, amount, paymentMethod);

        // Idempotency check: if a payment already exists for this order, return it as-is
        var existingPayment = paymentRepository.findByOrderId(orderId);
        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();
            log.info("Payment already exists for orderId={} with status={}", orderId, payment.getStatus());
            return payment;
        }

        Payment payment = Payment.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .status("PENDING")
                .build();

        // Delegate to the appropriate strategy via factory
        PaymentResult result = paymentStrategyFactory.getStrategy(paymentMethod)
                .process(orderId, userId, amount);

        if (result.isSuccess()) {
            payment.setStatus("COMPLETED");
            payment.setTransactionId(result.getTransactionId());
            log.info("Payment completed for orderId={}, txnId={}", orderId, result.getTransactionId());
        } else {
            payment.setStatus("FAILED");
            payment.setFailureReason(result.getFailureReason());
            log.warn("Payment failed for orderId={}: {}", orderId, result.getFailureReason());
        }

        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(UUID orderId) {
        log.info("Fetching payment for orderId={}", orderId);

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found for orderId: " + orderId));

        return mapToPaymentResponse(payment);
    }

    /**
     * Processes a refund for an existing completed payment.
     * Idempotent: if already refunded, returns the existing payment.
     */
    @Transactional
    public Payment processRefund(UUID orderId) {
        log.info("Processing refund for orderId={}", orderId);

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for orderId: " + orderId));

        if ("REFUNDED".equals(payment.getStatus())) {
            log.info("Payment already refunded for orderId={}", orderId);
            return payment;
        }

        if (!"COMPLETED".equals(payment.getStatus())) {
            throw new IllegalStateException("Cannot refund payment with status: " + payment.getStatus());
        }

        String refundTxnId = "REFUND-" + UUID.randomUUID();
        payment.setStatus("REFUNDED");
        payment.setRefundedAmount(payment.getAmount());
        payment.setRefundTransactionId(refundTxnId);
        payment.setRefundedAt(java.time.LocalDateTime.now());

        payment = paymentRepository.save(payment);
        log.info("Refund completed for orderId={}, refundTxnId={}", orderId, refundTxnId);
        return payment;
    }

    private PaymentResponse mapToPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .transactionId(payment.getTransactionId())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
