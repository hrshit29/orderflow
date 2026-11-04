package com.orderflow.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orderflow.events.OrderCancelledEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Only interfaces are mocked, so this runs on any JDK. */
class OutboxRelayTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final OutboxStore store = mock(OutboxStore.class);
    @SuppressWarnings("unchecked")
    private final KafkaOperations<String, Object> kafka = mock(KafkaOperations.class);
    private final OutboxRelay relay = new OutboxRelay(store, kafka, mapper, TransactionOperations.withoutTransaction(),
            100, Duration.ofDays(7));

    @Test
    void publishesInOrderAndStopsAtFirstFailureSoNothingOvertakesIt() throws Exception {
        when(store.lockPending(100)).thenReturn(List.of(msg(1, 10), msg(2, 11), msg(3, 12)));
        when(kafka.send(eq("order-cancelled"), eq("10"), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(kafka.send(eq("order-cancelled"), eq("11"), any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        relay.publishPending();

        verify(store).markPublished(1);
        verify(store).markFailed(eq(2L), contains("broker down"));
        verify(store, never()).markPublished(2);
        verify(kafka, never()).send(eq("order-cancelled"), eq("12"), any());   // 3 waits for 2
    }

    @Test
    void sendsTheOriginalEventTypeSoConsumersGetTheRightClass() throws Exception {
        when(store.lockPending(100)).thenReturn(List.of(msg(1, 10)));
        when(kafka.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

        relay.publishPending();

        verify(kafka).send(eq("order-cancelled"), eq("10"), isA(OrderCancelledEvent.class));
    }

    @Test
    void refusesTypesOutsideTheEventsPackage() {
        when(store.lockPending(100)).thenReturn(List.of(new OutboxMessage(1, "t", "k", "java.lang.ProcessBuilder", "{}")));

        relay.publishPending();

        verify(store).markFailed(eq(1L), contains("untrusted"));
        verifyNoInteractions(kafka);
    }

    private OutboxMessage msg(long id, long orderId) throws Exception {
        var event = new OrderCancelledEvent(orderId, 1L, "test", Instant.parse("2026-11-04T10:00:00Z"));
        return new OutboxMessage(id, "order-cancelled", String.valueOf(orderId), event.getClass().getName(),
                mapper.writeValueAsString(event));
    }
}
