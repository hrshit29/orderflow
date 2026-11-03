package com.orderflow.paymentservice.dto;

import com.orderflow.paymentservice.model.Payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(Long id, Long orderId, Long userId, BigDecimal amount, String status,
                              String gatewayReference, String failureReason, Instant createdAt, Instant updatedAt) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getOrderId(), p.getUserId(), p.getAmount(), p.getStatus().name(),
                p.getGatewayReference(), p.getFailureReason(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
