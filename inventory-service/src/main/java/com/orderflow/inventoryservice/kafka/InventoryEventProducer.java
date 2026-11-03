package com.orderflow.inventoryservice.kafka;

import com.orderflow.events.StockInsufficientEvent;
import com.orderflow.events.StockReservedEvent;
import com.orderflow.events.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Publishes stock outcomes after the DB commit, keyed by orderId. */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockReserved(StockReservedEvent event) {
        send(Topics.STOCK_RESERVED, event.orderId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockInsufficient(StockInsufficientEvent event) {
        send(Topics.STOCK_INSUFFICIENT, event.orderId(), event);
    }

    private void send(String topic, Long orderId, Object event) {
        kafkaTemplate.send(topic, String.valueOf(orderId), event).whenComplete((r, ex) -> {
            if (ex != null) log.error("Failed to publish {} for order {}", topic, orderId, ex);
            else log.info("Published {} for order {}", topic, orderId);
        });
    }
}
