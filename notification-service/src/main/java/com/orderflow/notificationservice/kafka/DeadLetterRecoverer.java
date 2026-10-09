package com.orderflow.notificationservice.kafka;

import com.orderflow.events.Topics;
import com.orderflow.notificationservice.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.stereotype.Component;

/**
 * Runs after all retries are exhausted: nothing fails silently.
 * 1. Records a FAILED entry in notification_log (visible via GET /api/v1/notifications?status=FAILED)
 * 2. Publishes the original record to notification-dlq, with exception details in headers,
 *    for manual review / replay.
 */
@Slf4j
@Component
public class DeadLetterRecoverer implements ConsumerRecordRecoverer {

    private final NotificationService notificationService;
    private final DeadLetterPublishingRecoverer dlqPublisher;

    public DeadLetterRecoverer(NotificationService notificationService,
                               @Qualifier("deadLetterTemplate") KafkaTemplate<Object, Object> deadLetterTemplate) {
        this.notificationService = notificationService;
        this.dlqPublisher = new DeadLetterPublishingRecoverer(deadLetterTemplate,
                (record, ex) -> new TopicPartition(Topics.NOTIFICATION_DLQ, -1)); // -1: let Kafka pick the partition
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception ex) {
        Object event = record.value();
        Long userId = NotificationEventConsumer.userIdOf(event);
        Long entityId = NotificationEventConsumer.entityIdOf(event);
        log.error("Giving up on {} offset {} after retries; sending to {}",
                record.topic(), record.offset(), Topics.NOTIFICATION_DLQ, ex);

        if (userId != null && entityId != null) {
            try {
                notificationService.recordFailure(NotificationEventConsumer.key(record.topic(), entityId),
                        record.topic(), userId, rootMessage(ex));
            } catch (Exception logEx) {
                log.warn("Could not record failure in notification_log", logEx);
            }
        }
        dlqPublisher.accept(record, ex);
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}
