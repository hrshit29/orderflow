package com.orderflow.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderCreatedEvent(Long orderId, Long userId, BigDecimal totalAmount,
                                List<OrderLine> items, Instant occurredAt) {

    public record OrderLine(Long productId, int quantity, BigDecimal unitPrice) {
    }
}
