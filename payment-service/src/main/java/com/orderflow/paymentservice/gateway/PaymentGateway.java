package com.orderflow.paymentservice.gateway;

import java.math.BigDecimal;

/**
 * The external card processor (Razorpay, Stripe, ...). Calls are made outside any DB transaction:
 * a slow gateway must never hold row locks or a DB connection.
 */
public interface PaymentGateway {

    /**
     * Charges once per idempotency key: calling again with the same key returns the original
     * result instead of charging again. Real gateways support this (Stripe's Idempotency-Key header).
     *
     * @throws GatewayUnavailableException on timeout / 5xx — the outcome is unknown, so the caller retries
     */
    Result charge(String idempotencyKey, BigDecimal amount);

    /** Returns the money for a successful charge. */
    void refund(String reference, BigDecimal amount);

    record Result(boolean approved, String reference, String declineReason) {
        public static Result approved(String reference) {
            return new Result(true, reference, null);
        }

        public static Result declined(String reason) {
            return new Result(false, null, reason);
        }
    }

    class GatewayUnavailableException extends RuntimeException {
        public GatewayUnavailableException(String message) {
            super(message);
        }
    }
}
