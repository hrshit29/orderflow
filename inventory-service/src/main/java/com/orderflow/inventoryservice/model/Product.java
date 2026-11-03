package com.orderflow.inventoryservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A catalogue product and its stock. The id is the catalogue id (assigned by the caller,
 * not generated), so order line items can reference it directly.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Units that can still be sold. Reserved units are already subtracted. */
    @Column(nullable = false)
    private int availableQuantity;

    /** Units held by orders that are not yet cancelled. */
    @Column(nullable = false)
    private int reservedQuantity;

    /** Optimistic locking for admin updates over REST. Reservations use a pessimistic row lock. */
    @Version
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public Product(Long id, String name, BigDecimal price, int availableQuantity) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.availableQuantity = availableQuantity;
    }

    public boolean canReserve(int quantity) {
        return availableQuantity >= quantity;
    }

    public void reserve(int quantity) {
        if (!canReserve(quantity)) {
            throw new IllegalStateException("Not enough stock for product " + id);
        }
        availableQuantity -= quantity;
        reservedQuantity += quantity;
    }

    public void release(int quantity) {
        availableQuantity += quantity;
        reservedQuantity = Math.max(0, reservedQuantity - quantity);
    }
}
