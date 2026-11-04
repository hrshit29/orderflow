package com.orderflow.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls outbox_events and publishes to Kafka in id order.
 *
 * At-least-once: if the pod dies after Kafka acked but before published_at is committed, the event
 * is sent again on restart. Every consumer in OrderFlow is already idempotent, so that is safe.
 * On a send failure the batch stops, so a later event never overtakes an earlier one.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final String TRUSTED_PACKAGE = "com.orderflow.events.";

    private final OutboxStore store;
    private final KafkaOperations<String, Object> kafka;
    private final ObjectMapper objectMapper;
    private final TransactionOperations tx;
    private final int batchSize;
    private final Duration retention;

    public OutboxRelay(OutboxStore store, KafkaOperations<String, Object> kafka, ObjectMapper objectMapper,
                       TransactionOperations tx, int batchSize, Duration retention) {
        this.store = store;
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.tx = tx;
        this.batchSize = batchSize;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:500}", initialDelayString = "${outbox.initial-delay-ms:5000}")
    public void publishPending() {
        Integer sent = tx.execute(status -> {
            List<OutboxMessage> batch = store.lockPending(batchSize);
            int ok = 0;
            for (OutboxMessage m : batch) {
                try {
                    kafka.send(m.topic(), m.key(), toEvent(m)).get(10, TimeUnit.SECONDS);
                    store.markPublished(m.id());
                    ok++;
                } catch (Exception e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    store.markFailed(m.id(), rootMessage(e));
                    log.warn("Outbox event {} ({} -> {}) not published, will retry: {}", m.id(), m.eventType(), m.topic(), rootMessage(e));
                    break;
                }
            }
            return ok;
        });
        if (sent != null && sent > 0) {
            log.debug("Outbox relayed {} event(s)", sent);
        }
    }

    @Scheduled(cron = "${outbox.cleanup-cron:0 17 * * * *}")
    public void deleteOldPublished() {
        int deleted = store.deletePublishedBefore(Instant.now().minus(retention));
        if (deleted > 0) log.info("Outbox cleanup removed {} published event(s)", deleted);
    }

    /** Rebuilds the original event object so the JsonSerializer adds the same __TypeId__ header consumers expect. */
    private Object toEvent(OutboxMessage m) throws Exception {
        if (!m.eventType().startsWith(TRUSTED_PACKAGE)) {
            throw new IllegalStateException("Refusing to deserialise untrusted type " + m.eventType());
        }
        return objectMapper.readValue(m.payload(), Class.forName(m.eventType()));
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}
