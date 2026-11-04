package com.orderflow.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcOperations;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Plain JDBC, so services need no extra JPA entity. JdbcTemplate joins the surrounding
 * JPA transaction (same DataSource), which is what makes the write atomic with the business change.
 */
public class JdbcOutboxStore implements OutboxStore {

    private final JdbcOperations jdbc;
    private final ObjectMapper objectMapper;

    public JdbcOutboxStore(JdbcOperations jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String topic, String key, Object event) {
        try {
            jdbc.update("INSERT INTO outbox_events (topic, message_key, event_type, payload, created_at) VALUES (?, ?, ?, ?, ?)",
                    topic, key, event.getClass().getName(), objectMapper.writeValueAsString(event), Timestamp.from(Instant.now()));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialise " + event.getClass().getName(), e);
        }
    }

    @Override
    public List<OutboxMessage> lockPending(int limit) {
        // FOR UPDATE (not SKIP LOCKED): a second replica waits instead of grabbing the next batch,
        // which keeps events for the same key in order. One relay at a time is plenty for this volume.
        return jdbc.query("SELECT id, topic, message_key, event_type, payload FROM outbox_events "
                        + "WHERE published_at IS NULL ORDER BY id LIMIT " + Math.max(1, limit) + " FOR UPDATE",
                (rs, n) -> new OutboxMessage(rs.getLong("id"), rs.getString("topic"), rs.getString("message_key"),
                        rs.getString("event_type"), rs.getString("payload")));
    }

    @Override
    public void markPublished(long id) {
        jdbc.update("UPDATE outbox_events SET published_at = ? WHERE id = ?", Timestamp.from(Instant.now()), id);
    }

    @Override
    public void markFailed(long id, String error) {
        jdbc.update("UPDATE outbox_events SET attempts = attempts + 1, last_error = ? WHERE id = ?",
                error == null ? null : error.substring(0, Math.min(error.length(), 500)), id);
    }

    @Override
    public int deletePublishedBefore(Instant cutoff) {
        return jdbc.update("DELETE FROM outbox_events WHERE published_at IS NOT NULL AND published_at < ? LIMIT 1000",
                Timestamp.from(cutoff));
    }

    @Override
    public long countPending() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE published_at IS NULL", Long.class);
        return n == null ? 0 : n;
    }
}
