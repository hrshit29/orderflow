package com.orderflow.events;

import java.time.Instant;

/** Published by Inventory Service (Phase 2). */
public record StockInsufficientEvent(Long orderId, Long userId, Long productId, Instant occurredAt) {
}
