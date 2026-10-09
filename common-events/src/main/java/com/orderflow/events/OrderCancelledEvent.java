package com.orderflow.events;

import java.time.Instant;

public record OrderCancelledEvent(Long orderId, Long userId, String reason, Instant occurredAt) {
}
