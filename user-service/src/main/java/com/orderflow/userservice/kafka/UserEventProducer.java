package com.orderflow.userservice.kafka;

import com.orderflow.events.Topics;
import com.orderflow.events.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes to Kafka only AFTER the DB transaction commits, so a rolled-back
 * registration never emits a "user-registered" event.
 * (Next level: transactional outbox pattern for guaranteed delivery.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {
        kafkaTemplate.send(Topics.USER_REGISTERED, String.valueOf(event.userId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish user-registered for user {}", event.userId(), ex);
                    } else {
                        log.info("Published user-registered for user {} at offset {}",
                                event.userId(), result.getRecordMetadata().offset());
                    }
                });
    }
}
