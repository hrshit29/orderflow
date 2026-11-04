package com.orderflow.outbox;

/** One stored, not-yet-published event. payload is the event as JSON; eventType its class name. */
public record OutboxMessage(long id, String topic, String key, String eventType, String payload) {
}
