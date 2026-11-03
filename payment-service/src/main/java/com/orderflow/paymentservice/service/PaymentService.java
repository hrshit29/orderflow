package com.orderflow.paymentservice.service;

import com.orderflow.events.OrderCancelledEvent;
import com.orderflow.events.PageResponse;
import com.orderflow.events.PaymentFailedEvent;
import com.orderflow.events.PaymentSuccessEvent;
import com.orderflow.events.StockReservedEvent;
import com.orderflow.paymentservice.dto.PaymentResponse;
import com.orderflow.paymentservice.gateway.PaymentGateway;
import com.orderflow.paymentservice.model.Payment;
import com.orderflow.paymentservice.model.Payment.Status;
import com.orderflow.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Instant;
import java.util.Optional;

/**
 * The payment step of the order saga.
 *
 * stock-reserved  -> 1. tx: write PENDING row (unique per order)
 *                    2. no tx: call the gateway (idempotency key = order id)
 *                    3. tx: record outcome -> payment-success | payment-failed
 * order-cancelled -> before charge: tombstone, never charge
 *                    mid-charge:    mark CANCELLED, step 3 refunds instead of publishing success
 *                    after charge:  refund
 *
 * The gateway call sits between two short transactions so a slow gateway never holds a DB lock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository repository;
    private final PaymentGateway gateway;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionOperations tx;

    public void charge(StockReservedEvent event) {
        Long orderId = event.orderId();
        Boolean proceed = tx.execute(s -> begin(event));
        if (!Boolean.TRUE.equals(proceed)) {
            return;
        }
        // Throws GatewayUnavailableException on timeout: the row stays PENDING, Kafka retries,
        // and the retry reuses the same idempotency key, so the customer is charged at most once.
        PaymentGateway.Result result = gateway.charge(idempotencyKey(orderId), event.totalAmount());
        tx.executeWithoutResult(s -> complete(orderId, result));
    }

    public void cancel(OrderCancelledEvent event) {
        tx.executeWithoutResult(s -> {
            Optional<Payment> existing = repository.lockByOrderId(event.orderId());
            if (existing.isEmpty()) {
                repository.save(Payment.cancelledBeforeCharge(event.orderId(), event.userId(),
                        "Order cancelled before payment: " + event.reason()));
                log.info("Order {} cancelled before payment; recorded tombstone", event.orderId());
                return;
            }
            Payment p = existing.get();
            switch (p.getStatus()) {
                case PENDING -> {
                    p.setStatus(Status.CANCELLED);
                    p.setFailureReason("Order cancelled during payment: " + event.reason());
                    log.info("Order {} cancelled while being charged; will refund if the charge goes through", event.orderId());
                }
                case SUCCEEDED -> {
                    gateway.refund(p.getGatewayReference(), p.getAmount());
                    p.refunded(p.getGatewayReference(), "Order cancelled after payment: " + event.reason());
                    log.info("Order {} cancelled after payment; refunded Rs. {}", event.orderId(), p.getAmount());
                }
                default -> log.info("Order {} cancelled; payment already {}", event.orderId(), p.getStatus());
            }
        });
    }

    /** Called by the Kafka recoverer when the gateway stayed unavailable through every retry. */
    public void failAfterRetries(StockReservedEvent event, String reason) {
        tx.executeWithoutResult(s -> {
            Payment p = repository.lockByOrderId(event.orderId())
                    .orElseGet(() -> repository.save(Payment.pending(event.orderId(), event.userId(), event.totalAmount())));
            if (p.getStatus() != Status.PENDING) {
                return;
            }
            // Known limitation: a timed-out charge may have gone through at the gateway.
            // Production fix: a reconciliation job that queries the gateway by idempotency key.
            p.fail(reason);
            eventPublisher.publishEvent(new PaymentFailedEvent(p.getOrderId(), p.getUserId(), reason, Instant.now()));
            log.warn("Order {} payment failed after retries: {}", event.orderId(), reason);
        });
    }

    public PaymentResponse get(Long orderId) {
        return repository.findByOrderId(orderId).map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException("No payment for order " + orderId));
    }

    public PageResponse<PaymentResponse> list(Long userId, Status status, Pageable pageable) {
        var page = userId != null && status != null ? repository.findByUserIdAndStatus(userId, status, pageable)
                : userId != null ? repository.findByUserId(userId, pageable)
                : status != null ? repository.findByStatus(status, pageable)
                : repository.findAll(pageable);
        return PageResponse.from(page, PaymentResponse::from);
    }

    // ---- steps ----

    /** @return true if the gateway should be called (new payment, or a PENDING one being resumed). */
    private boolean begin(StockReservedEvent event) {
        Optional<Payment> existing = repository.lockByOrderId(event.orderId());
        if (existing.isPresent()) {
            Status status = existing.get().getStatus();
            if (status == Status.PENDING) {
                log.info("Resuming interrupted payment for order {}", event.orderId());
                return true;
            }
            log.info("Order {} payment already {}, skipping stock-reserved", event.orderId(), status);
            return false;
        }
        // A concurrent insert for the same order fails on uk_payments_order and is retried by Kafka.
        repository.save(Payment.pending(event.orderId(), event.userId(), event.totalAmount()));
        return true;
    }

    private void complete(Long orderId, PaymentGateway.Result result) {
        Payment p = repository.lockByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("Payment row vanished for order " + orderId));
        switch (p.getStatus()) {
            case PENDING -> {
                if (result.approved()) {
                    p.succeed(result.reference());
                    eventPublisher.publishEvent(new PaymentSuccessEvent(orderId, p.getUserId(), p.getId(), p.getAmount(), Instant.now()));
                    log.info("Order {} paid Rs. {} ref={}", orderId, p.getAmount(), result.reference());
                } else {
                    p.fail(result.declineReason());
                    eventPublisher.publishEvent(new PaymentFailedEvent(orderId, p.getUserId(), result.declineReason(), Instant.now()));
                    log.info("Order {} payment declined: {}", orderId, result.declineReason());
                }
            }
            case CANCELLED -> {
                if (result.approved()) {
                    gateway.refund(result.reference(), p.getAmount());
                    p.refunded(result.reference(), p.getFailureReason());
                    log.info("Order {} was cancelled during payment; refunded Rs. {}", orderId, p.getAmount());
                }
            }
            default -> log.info("Order {} payment already {}", orderId, p.getStatus());
        }
    }

    private static String idempotencyKey(Long orderId) {
        return "order-" + orderId;
    }

    public static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException(String message) {
            super(message);
        }
    }
}
