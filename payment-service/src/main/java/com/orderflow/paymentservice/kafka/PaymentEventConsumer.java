package com.orderflow.paymentservice.kafka;

import com.orderflow.events.OrderCancelledEvent;
import com.orderflow.events.StockReservedEvent;
import com.orderflow.events.Topics;
import com.orderflow.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final PaymentService paymentService;

    @KafkaListener(topics = Topics.STOCK_RESERVED)
    public void onStockReserved(StockReservedEvent event) {
        paymentService.charge(event);
    }

    @KafkaListener(topics = Topics.ORDER_CANCELLED)
    public void onOrderCancelled(OrderCancelledEvent event) {
        paymentService.cancel(event);
    }
}
