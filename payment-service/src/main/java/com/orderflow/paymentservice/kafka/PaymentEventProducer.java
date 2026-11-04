package com.orderflow.paymentservice.kafka;

import com.orderflow.events.PaymentFailedEvent;
import com.orderflow.events.PaymentSuccessEvent;
import com.orderflow.events.Topics;
import com.orderflow.outbox.OutboxStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Payment outcomes go to the outbox in the same transaction that records them. */
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final OutboxStore outbox;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPaymentSuccess(PaymentSuccessEvent event) {
        outbox.save(Topics.PAYMENT_SUCCESS, String.valueOf(event.orderId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPaymentFailed(PaymentFailedEvent event) {
        outbox.save(Topics.PAYMENT_FAILED, String.valueOf(event.orderId()), event);
    }
}
