package com.orderflow.orderservice.it;

import com.orderflow.events.Caller;
import com.orderflow.events.PaymentSuccessEvent;
import com.orderflow.events.StockReservedEvent;
import com.orderflow.events.Topics;
import com.orderflow.orderservice.model.OrderStatus;
import com.orderflow.orderservice.repository.OrderRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Real MySQL + real Kafka in Docker. Covers what the unit tests mock away:
 * Flyway migrations + Hibernate schema validation, the outbox table and relay, Kafka (de)serialisation
 * of the shared event records, the consumers driving the saga, and ownership checks over HTTP.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "outbox.initial-delay-ms=0",
        "outbox.poll-interval-ms=200"
})
class OrderFlowIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired TestRestTemplate http;
    @Autowired KafkaTemplate<String, Object> kafka;
    @Autowired OrderRepository orders;
    @Autowired JdbcTemplate jdbc;

    @Test
    void flywayAppliedBaselineAndOutboxMigrations() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class);
        assertThat(versions).containsExactly("1", "2");
    }

    @Test
    void newOrderIsPublishedToKafkaThroughTheOutbox() {
        long orderId = placeOrder(7L);

        try (KafkaConsumer<String, String> consumer = rawConsumer()) {
            consumer.subscribe(List.of(Topics.ORDER_CREATED));
            List<ConsumerRecord<String, String>> seen = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(seen::add);
                return seen.stream().anyMatch(r -> r.key().equals(String.valueOf(orderId)));
            });
            ConsumerRecord<String, String> rec = seen.stream()
                    .filter(r -> r.key().equals(String.valueOf(orderId))).findFirst().orElseThrow();
            assertThat(rec.value()).contains("\"orderId\":" + orderId).contains("\"userId\":7");
            assertThat(new String(rec.headers().lastHeader("__TypeId__").value()))
                    .isEqualTo("com.orderflow.events.OrderCreatedEvent");
        }

        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE message_key = ? AND published_at IS NOT NULL",
                Long.class, String.valueOf(orderId)) == 1L);
    }

    @Test
    void sagaEventsMoveOrderToConfirmedThenPaid() {
        long orderId = placeOrder(7L);
        BigDecimal total = new BigDecimal("20.00");

        kafka.send(Topics.STOCK_RESERVED, String.valueOf(orderId), new StockReservedEvent(orderId, 7L, total, Instant.now()));
        await().atMost(Duration.ofSeconds(30)).until(() -> status(orderId) == OrderStatus.CONFIRMED);

        kafka.send(Topics.PAYMENT_SUCCESS, String.valueOf(orderId), new PaymentSuccessEvent(orderId, 7L, 1L, total, Instant.now()));
        await().atMost(Duration.ofSeconds(30)).until(() -> status(orderId) == OrderStatus.PAID);
    }

    @Test
    void customerCannotSeeAnotherCustomersOrder() {
        long orderId = placeOrder(7L);

        assertThat(getAs(orderId, 7L).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getAs(orderId, 8L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- helpers ----

    /** Internal call (no gateway headers), so userId comes from the body. */
    @SuppressWarnings("unchecked")
    private long placeOrder(long userId) {
        Map<String, Object> body = Map.of("userId", userId,
                "items", List.of(Map.of("productId", 7, "quantity", 2, "unitPrice", 10)));
        ResponseEntity<Map> res = http.postForEntity("/api/v1/orders", body, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) res.getBody().get("id")).longValue();
    }

    private ResponseEntity<String> getAs(long orderId, long callerId) {
        HttpHeaders h = new HttpHeaders();
        h.set(Caller.USER_ID_HEADER, String.valueOf(callerId));
        h.set(Caller.ROLE_HEADER, "CUSTOMER");
        return http.exchange("/api/v1/orders/" + orderId, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    private OrderStatus status(long orderId) {
        return orders.findById(orderId).orElseThrow().getStatus();
    }

    private static KafkaConsumer<String, String> rawConsumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }
}
