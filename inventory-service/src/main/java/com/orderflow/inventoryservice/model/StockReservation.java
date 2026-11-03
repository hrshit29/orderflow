package com.orderflow.inventoryservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One row per order: the record of what Inventory did for it. Keyed by orderId,
 * which makes both consumers idempotent (a redelivered event finds the existing row).
 */
@Entity
@Table(name = "stock_reservations")
@Getter
@Setter
@NoArgsConstructor
public class StockReservation {

    public enum Status {
        /** Stock is held for the order. */
        RESERVED,
        /** Not enough stock; nothing was held. */
        REJECTED,
        /** Order was cancelled; held stock (if any) went back on the shelf. */
        RELEASED
    }

    @Id
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(length = 255)
    private String reason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "stock_reservation_lines", joinColumns = @JoinColumn(name = "order_id"))
    private List<ReservationLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public StockReservation(Long orderId, Long userId, Status status, String reason) {
        this.orderId = orderId;
        this.userId = userId;
        this.status = status;
        this.reason = reason;
    }

    @Embeddable
    @Getter
    @NoArgsConstructor
    public static class ReservationLine {
        @Column(nullable = false)
        private Long productId;

        @Column(nullable = false)
        private int quantity;

        public ReservationLine(Long productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }
    }
}
