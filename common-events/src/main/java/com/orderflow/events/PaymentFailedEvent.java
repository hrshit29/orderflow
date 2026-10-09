package com.orderflow.events;

import java.time.Instant;

/** Published by Payment Service (Phase 2). Order Service already consumes it. */
public record PaymentFailedEvent(Long orderId, Long userId, String reason, Instant occurredAt) {
}
