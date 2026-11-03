package com.orderflow.orderservice.scheduler;

import com.orderflow.orderservice.model.Order;
import com.orderflow.orderservice.model.OrderStatus;
import com.orderflow.orderservice.repository.OrderRepository;
import com.orderflow.orderservice.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Saga timeout. Kafka is at-least-once, but an event can still never arrive: a downstream service
 * down longer than topic retention (7 days by default), a bug, a dropped publish. Without this,
 * such an order would sit in PENDING/CONFIRMED forever.
 *
 * Every minute, orders with no state change for {@code orders.saga-timeout} are cancelled. That
 * publishes order-cancelled, so Inventory returns any held stock and Payment refunds or never charges.
 * Safe with several replicas: cancelFromEvent is idempotent and Order has optimistic locking.
 */
@Slf4j
@Component
public class OrderTimeoutJob {

    private static final List<OrderStatus> IN_FLIGHT = List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED);

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final Duration timeout;

    public OrderTimeoutJob(OrderRepository orderRepository, OrderService orderService,
                           @Value("${orders.saga-timeout:15m}") Duration timeout) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.timeout = timeout;
    }

    @Scheduled(fixedDelayString = "${orders.timeout-check-interval-ms:60000}", initialDelay = 30_000)
    public void expireStaleOrders() {
        List<Order> stale = orderRepository.findTop100ByStatusInAndUpdatedAtBefore(IN_FLIGHT, Instant.now().minus(timeout));
        for (Order order : stale) {
            try {
                orderService.cancelFromEvent(order.getId(),
                        "Timed out in " + order.getStatus() + " after " + timeout.toMinutes() + " min");
                log.warn("Order {} timed out in {}; cancelled", order.getId(), order.getStatus());
            } catch (Exception e) {
                // e.g. a Kafka consumer updated it at the same moment (optimistic lock). Next run re-checks.
                log.warn("Could not time out order {}: {}", order.getId(), e.getMessage());
            }
        }
    }
}
