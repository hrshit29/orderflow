package com.orderflow.inventoryservice.dto;

import com.orderflow.inventoryservice.model.Product;
import com.orderflow.inventoryservice.model.StockReservation;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record ProductRequest(
            @NotNull @Positive Long id,
            @NotBlank @Size(max = 120) String name,
            @NotNull @DecimalMin(value = "0.01") BigDecimal price,
            @Min(0) int quantity) {
    }

    /** Positive adds stock (a delivery), negative removes it (damage, correction). */
    public record StockAdjustmentRequest(@NotNull Integer delta) {
    }

    public record ProductResponse(Long id, String name, BigDecimal price,
                                  int availableQuantity, int reservedQuantity, Instant updatedAt) {
        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getName(), p.getPrice(),
                    p.getAvailableQuantity(), p.getReservedQuantity(), p.getUpdatedAt());
        }
    }

    public record ReservationLineResponse(Long productId, int quantity) {
    }

    public record ReservationResponse(Long orderId, Long userId, String status, String reason,
                                      List<ReservationLineResponse> lines, Instant createdAt, Instant updatedAt) {
        public static ReservationResponse from(StockReservation r) {
            return new ReservationResponse(r.getOrderId(), r.getUserId(), r.getStatus().name(), r.getReason(),
                    r.getLines().stream().map(l -> new ReservationLineResponse(l.getProductId(), l.getQuantity())).toList(),
                    r.getCreatedAt(), r.getUpdatedAt());
        }
    }
}
