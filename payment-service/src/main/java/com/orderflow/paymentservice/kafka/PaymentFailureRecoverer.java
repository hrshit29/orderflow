package com.orderflow.paymentservice.kafka;

import com.orderflow.events.StockReservedEvent;
import com.orderflow.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.stereotype.Component;

/**
 * Runs once retries are exhausted. For a charge, the saga must not stall with the order stuck
 * in CONFIRMED forever, so the payment is marked FAILED and payment-failed is published;
 * Order Service then cancels the order and Inventory returns the stock.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFailureRecoverer implements ConsumerRecordRecoverer {

    private final PaymentService paymentService;

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception ex) {
        log.error("Giving up on {} offset {} after retries", record.topic(), record.offset(), ex);
        if (record.value() instanceof StockReservedEvent event) {
            paymentService.failAfterRetries(event, "Payment gateway unavailable: " + rootMessage(ex));
        }
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage();
    }
}
