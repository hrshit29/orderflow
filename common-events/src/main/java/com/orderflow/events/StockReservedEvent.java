package com.orderflow.events;

import java.math.BigDecimal;
import java.time.Instant;

/** Published by Inventory Service (Phase 2). Order Service already consumes it. */
public record StockReservedEvent(Long orderId, Long userId, BigDecimal totalAmount, Instant occurredAt) {
}
