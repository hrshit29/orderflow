package com.orderflow.userservice.kafka;

import com.orderflow.events.Topics;
import com.orderflow.events.UserRegisteredEvent;
import com.orderflow.outbox.OutboxStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * BEFORE_COMMIT: the event row goes into outbox_events inside the registration's own transaction.
 * Both commit or neither does; OutboxRelay publishes it to Kafka afterwards.
 */
@Component
@RequiredArgsConstructor
public class UserEventProducer {

    private final OutboxStore outbox;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {
        outbox.save(Topics.USER_REGISTERED, String.valueOf(event.userId()), event);
    }
}
