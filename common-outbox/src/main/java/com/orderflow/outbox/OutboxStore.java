package com.orderflow.outbox;

import java.time.Instant;
import java.util.List;

/**
 * The outbox_events table. {@link #save} must be called inside the business transaction,
 * so the event is committed (or rolled back) together with the data it describes.
 */
public interface OutboxStore {

    void save(String topic, String key, Object event);

    /** Oldest unpublished events, row-locked (SELECT ... FOR UPDATE) until the caller's transaction ends. */
    List<OutboxMessage> lockPending(int limit);

    void markPublished(long id);

    void markFailed(long id, String error);

    int deletePublishedBefore(Instant cutoff);

    /** Unpublished events. Should hover near 0; a rising value means Kafka is unreachable. */
    long countPending();
}
