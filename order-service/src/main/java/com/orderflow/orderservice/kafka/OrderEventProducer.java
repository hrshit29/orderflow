package com.orderflow.orderservice.kafka;

import com.orderflow.events.OrderCancelledEvent;
import com.orderflow.events.OrderCreatedEvent;
import com.orderflow.events.Topics;
import com.orderflow.outbox.OutboxStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes order events to the outbox inside the order's own transaction (BEFORE_COMMIT).
 * Keyed by orderId so every event for one order lands on the same partition, in order.
 */
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final OutboxStore outbox;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        outbox.save(Topics.ORDER_CREATED, String.valueOf(event.orderId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        outbox.save(Topics.ORDER_CANCELLED, String.valueOf(event.orderId()), event);
    }
}
