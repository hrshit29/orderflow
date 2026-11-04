package com.orderflow.orderservice.dto;

import com.orderflow.orderservice.model.Order;
import com.orderflow.orderservice.model.OrderItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record OrderItemRequest(
            @NotNull Long productId,
            @Min(1) @Max(100) int quantity,
            // Phase 2: price comes from Inventory Service, not the client.
            @NotNull @DecimalMin(value = "0.01") BigDecimal unitPrice) {
    }

    /** userId is taken from the JWT when called through the gateway; only internal callers must send it. */
    public record OrderRequest(
            Long userId,
            @NotEmpty @Size(max = 50) List<@Valid OrderItemRequest> items) {

        public OrderRequest withUserId(Long id) {
            return new OrderRequest(id, items);
        }
    }

    public record CancelRequest(@Size(max = 255) String reason) {
    }

    public record OrderItemResponse(Long productId, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        static OrderItemResponse from(OrderItem i) {
            return new OrderItemResponse(i.getProductId(), i.getQuantity(), i.getUnitPrice(), i.lineTotal());
        }
    }

    /** Full order including line items (single-order endpoints). */
    public record OrderResponse(Long id, Long userId, String status, BigDecimal totalAmount,
                                String cancellationReason, List<OrderItemResponse> items,
                                Instant createdAt, Instant updatedAt) {
        public static OrderResponse from(Order o) {
            return new OrderResponse(o.getId(), o.getUserId(), o.getStatus().name(), o.getTotalAmount(),
                    o.getCancellationReason(), o.getItems().stream().map(OrderItemResponse::from).toList(),
                    o.getCreatedAt(), o.getUpdatedAt());
        }
    }

    /** Lightweight row for list pages — avoids loading items (N+1) for every order. */
    public record OrderSummary(Long id, Long userId, String status, BigDecimal totalAmount, Instant createdAt) {
        public static OrderSummary from(Order o) {
            return new OrderSummary(o.getId(), o.getUserId(), o.getStatus().name(), o.getTotalAmount(), o.getCreatedAt());
        }
    }
}
