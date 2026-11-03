package com.orderflow.paymentservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * At most one payment per order (unique order_id). The row is written as PENDING before the
 * gateway is called, so a crash or redelivery mid-charge resumes the same payment instead of
 * charging twice, and a cancel that arrives mid-charge can see it.
 */
@Entity
@Table(name = "payments",
        uniqueConstraints = @UniqueConstraint(name = "uk_payments_order", columnNames = "order_id"),
        indexes = {
                @Index(name = "idx_payments_user", columnList = "user_id"),
                @Index(name = "idx_payments_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    public enum Status {
        /** Row written, gateway call in progress (or interrupted and awaiting redelivery). */
        PENDING,
        SUCCEEDED,
        FAILED,
        /** Charged, then money returned because the order was cancelled. */
        REFUNDED,
        /** Order was cancelled before it was charged; nothing will be charged. */
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Null only for a CANCELLED tombstone written before the amount was known. */
    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(length = 64)
    private String gatewayReference;

    @Column(length = 255)
    private String failureReason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public static Payment pending(Long orderId, Long userId, BigDecimal amount) {
        Payment p = new Payment();
        p.orderId = orderId;
        p.userId = userId;
        p.amount = amount;
        p.status = Status.PENDING;
        return p;
    }

    public static Payment cancelledBeforeCharge(Long orderId, Long userId, String reason) {
        Payment p = new Payment();
        p.orderId = orderId;
        p.userId = userId;
        p.status = Status.CANCELLED;
        p.failureReason = reason;
        return p;
    }

    public void succeed(String reference) {
        status = Status.SUCCEEDED;
        gatewayReference = reference;
    }

    public void fail(String reason) {
        status = Status.FAILED;
        failureReason = reason;
    }

    public void refunded(String reference, String reason) {
        status = Status.REFUNDED;
        gatewayReference = reference;
        failureReason = reason;
    }
}
