package com.orderflow.inventoryservice.kafka;

import com.orderflow.events.StockInsufficientEvent;
import com.orderflow.events.StockReservedEvent;
import com.orderflow.events.Topics;
import com.orderflow.outbox.OutboxStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Stock outcomes go to the outbox in the same transaction as the stock change. */
@Component
@RequiredArgsConstructor
public class InventoryEventProducer {

    private final OutboxStore outbox;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onStockReserved(StockReservedEvent event) {
        outbox.save(Topics.STOCK_RESERVED, String.valueOf(event.orderId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onStockInsufficient(StockInsufficientEvent event) {
        outbox.save(Topics.STOCK_INSUFFICIENT, String.valueOf(event.orderId()), event);
    }
}
