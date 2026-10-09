package com.orderflow.orderservice.kafka;

import com.orderflow.events.*;
import com.orderflow.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to downstream services. Inventory and Payment arrive in Phase 2;
 * these listeners are ready for them. All service methods are idempotent,
 * so Kafka's at-least-once redelivery is safe.
 */
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderService orderService;

    @KafkaListener(topics = Topics.STOCK_RESERVED)
    public void onStockReserved(StockReservedEvent event) {
        orderService.markConfirmed(event.orderId());
    }

    @KafkaListener(topics = Topics.STOCK_INSUFFICIENT)
    public void onStockInsufficient(StockInsufficientEvent event) {
        orderService.cancelFromEvent(event.orderId(), "Insufficient stock for product " + event.productId());
    }

    @KafkaListener(topics = Topics.PAYMENT_SUCCESS)
    public void onPaymentSuccess(PaymentSuccessEvent event) {
        orderService.markPaid(event.orderId());
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED)
    public void onPaymentFailed(PaymentFailedEvent event) {
        orderService.cancelFromEvent(event.orderId(), "Payment failed: " + event.reason());
    }
}
