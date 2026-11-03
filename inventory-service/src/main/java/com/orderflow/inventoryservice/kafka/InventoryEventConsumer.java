package com.orderflow.inventoryservice.kafka;

import com.orderflow.events.OrderCancelledEvent;
import com.orderflow.events.OrderCreatedEvent;
import com.orderflow.events.Topics;
import com.orderflow.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final InventoryService inventoryService;

    @KafkaListener(topics = Topics.ORDER_CREATED)
    public void onOrderCreated(OrderCreatedEvent event) {
        inventoryService.reserve(event);
    }

    @KafkaListener(topics = Topics.ORDER_CANCELLED)
    public void onOrderCancelled(OrderCancelledEvent event) {
        inventoryService.release(event);
    }
}
