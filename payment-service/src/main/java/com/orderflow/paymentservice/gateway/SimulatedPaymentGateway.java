package com.orderflow.paymentservice.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stand-in for a real card gateway, with two knobs for demos:
 * - amounts above {@code payment.gateway.decline-above} are declined
 * - an outage switch (POST /api/v1/payments/gateway/outage?enabled=true) makes every call time out
 */
@Slf4j
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private final BigDecimal declineAbove;
    private final AtomicBoolean outage = new AtomicBoolean(false);
    private final Map<String, Result> chargesByKey = new ConcurrentHashMap<>();

    public SimulatedPaymentGateway(@Value("${payment.gateway.decline-above:10000}") BigDecimal declineAbove) {
        this.declineAbove = declineAbove;
    }

    @Override
    public Result charge(String idempotencyKey, BigDecimal amount) {
        checkAvailable();
        return chargesByKey.computeIfAbsent(idempotencyKey, key -> {
            if (amount.compareTo(declineAbove) > 0) {
                log.info("[GATEWAY] {} declined: Rs. {} exceeds limit", key, amount);
                return Result.declined("Card declined: amount exceeds limit of Rs. " + declineAbove);
            }
            String reference = "SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            log.info("[GATEWAY] {} charged Rs. {} ref={}", key, amount, reference);
            return Result.approved(reference);
        });
    }

    @Override
    public void refund(String reference, BigDecimal amount) {
        checkAvailable();
        log.info("[GATEWAY] refunded Rs. {} for ref={}", amount, reference);
    }

    public void setOutage(boolean enabled) {
        outage.set(enabled);
        log.warn("[GATEWAY] outage simulation {}", enabled ? "ON" : "OFF");
    }

    public boolean isOutage() {
        return outage.get();
    }

    private void checkAvailable() {
        if (outage.get()) {
            throw new GatewayUnavailableException("Payment gateway timed out");
        }
    }
}
