package com.orderflow.orderservice.model;

/**
 * PENDING   -> created, waiting for stock reservation
 * CONFIRMED -> stock reserved, waiting for payment
 * PAID      -> payment succeeded
 * CANCELLED -> cancelled by user, insufficient stock, or failed payment
 */
public enum OrderStatus {
    PENDING, CONFIRMED, PAID, CANCELLED
}
