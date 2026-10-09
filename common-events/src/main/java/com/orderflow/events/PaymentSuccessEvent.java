package com.orderflow.events;

import java.math.BigDecimal;
import java.time.Instant;

/** Published by Payment Service (Phase 2). */
public record PaymentSuccessEvent(Long orderId, Long userId, Long paymentId, BigDecimal amount, Instant occurredAt) {
}
