package com.orderflow.paymentservice.kafka;

import com.orderflow.events.PaymentFailedEvent;
import com.orderflow.events.PaymentSuccessEvent;
import com.orderflow.events.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Publishes payment outcomes after the DB commit, keyed by orderId. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentSuccess(PaymentSuccessEvent event) {
        send(Topics.PAYMENT_SUCCESS, event.orderId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(PaymentFailedEvent event) {
        send(Topics.PAYMENT_FAILED, event.orderId(), event);
    }

    private void send(String topic, Long orderId, Object event) {
        kafkaTemplate.send(topic, String.valueOf(orderId), event).whenComplete((r, ex) -> {
            if (ex != null) log.error("Failed to publish {} for order {}", topic, orderId, ex);
            else log.info("Published {} for order {}", topic, orderId);
        });
    }
}
