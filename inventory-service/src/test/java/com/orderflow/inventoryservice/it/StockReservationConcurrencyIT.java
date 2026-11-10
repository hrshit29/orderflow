package com.orderflow.inventoryservice.it;

import com.orderflow.events.OrderCancelledEvent;
import com.orderflow.events.OrderCreatedEvent;
import com.orderflow.events.OrderCreatedEvent.OrderLine;
import com.orderflow.inventoryservice.model.Product;
import com.orderflow.inventoryservice.model.StockReservation.Status;
import com.orderflow.inventoryservice.repository.ProductRepository;
import com.orderflow.inventoryservice.repository.StockReservationRepository;
import com.orderflow.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pessimistic row lock (SELECT ... FOR UPDATE) only means something against a real database:
 * 10 orders race for 5 units, and exactly 5 must win. Mocked repositories can't show this.
 */
@Testcontainers
@SpringBootTest(properties = "inventory.seed-demo-products=false")
class StockReservationConcurrencyIT {

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

    @Autowired InventoryService inventory;
    @Autowired ProductRepository products;
    @Autowired StockReservationRepository reservations;

    @Test
    void tenConcurrentOrdersForFiveUnitsReserveExactlyFive() throws Exception {
        products.save(new Product(9001L, "Limited edition", BigDecimal.TEN, 5));

        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (long orderId = 1001; orderId <= 1010; orderId++) {
            OrderCreatedEvent event = order(orderId, 9001L, 1);
            results.add(pool.submit(() -> {
                go.await();
                inventory.reserve(event);
                return null;
            }));
        }
        go.countDown();                               // release all 10 at once
        for (Future<?> f : results) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        Product p = products.findById(9001L).orElseThrow();
        assertThat(p.getAvailableQuantity()).isZero();
        assertThat(p.getReservedQuantity()).isEqualTo(5);
        assertThat(countFor(1001, 1010, Status.RESERVED)).isEqualTo(5);
        assertThat(countFor(1001, 1010, Status.REJECTED)).isEqualTo(5);
    }

    @Test
    void cancellingAReservedOrderPutsStockBack() {
        products.save(new Product(9002L, "Cable", BigDecimal.ONE, 10));
        inventory.reserve(order(2001L, 9002L, 3));
        assertThat(products.findById(9002L).orElseThrow().getAvailableQuantity()).isEqualTo(7);

        inventory.release(new OrderCancelledEvent(2001L, 1L, "Payment failed", Instant.now()));
        inventory.release(new OrderCancelledEvent(2001L, 1L, "Payment failed", Instant.now()));   // redelivered

        Product p = products.findById(9002L).orElseThrow();
        assertThat(p.getAvailableQuantity()).isEqualTo(10);
        assertThat(p.getReservedQuantity()).isZero();
        assertThat(reservations.findById(2001L).orElseThrow().getStatus()).isEqualTo(Status.RELEASED);
    }

    private long countFor(long fromOrder, long toOrder, Status status) {
        return reservations.findAll().stream()
                .filter(r -> r.getOrderId() >= fromOrder && r.getOrderId() <= toOrder && r.getStatus() == status)
                .count();
    }

    private static OrderCreatedEvent order(long orderId, long productId, int qty) {
        return new OrderCreatedEvent(orderId, 1L, BigDecimal.TEN, List.of(new OrderLine(productId, qty, BigDecimal.TEN)), Instant.now());
    }
}
