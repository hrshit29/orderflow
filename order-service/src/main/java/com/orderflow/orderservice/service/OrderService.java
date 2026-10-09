package com.orderflow.orderservice.service;

import com.orderflow.events.OrderCancelledEvent;
import com.orderflow.events.OrderCreatedEvent;
import com.orderflow.events.PageResponse;
import com.orderflow.orderservice.dto.OrderDtos.*;
import com.orderflow.orderservice.exception.Exceptions.*;
import com.orderflow.orderservice.model.Order;
import com.orderflow.orderservice.model.OrderItem;
import com.orderflow.orderservice.model.OrderStatus;
import com.orderflow.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.orderflow.orderservice.repository.OrderRepository.Specs.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderResponse create(OrderRequest req) {
        Order order = new Order();
        order.setUserId(req.userId());
        req.items().forEach(i -> order.addItem(new OrderItem(i.productId(), i.quantity(), i.unitPrice())));
        Order saved = orderRepository.save(order);

        eventPublisher.publishEvent(new OrderCreatedEvent(
                saved.getId(), saved.getUserId(), saved.getTotalAmount(),
                saved.getItems().stream()
                        .map(i -> new OrderCreatedEvent.OrderLine(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                        .toList(),
                Instant.now()));
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return OrderResponse.from(orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> list(Long userId, OrderStatus status, Instant from, Instant to, Pageable pageable) {
        Specification<Order> spec = Specification.allOf(userId(userId), status(status), createdFrom(from), createdTo(to));
        return PageResponse.from(orderRepository.findAll(spec, pageable), OrderSummary::from);
    }

    @Transactional
    public OrderResponse cancel(Long id, String reason) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        if (!order.isCancellable()) {
            throw new InvalidOrderStateException("Order " + id + " cannot be cancelled in status " + order.getStatus());
        }
        cancelAndPublish(order, reason == null || reason.isBlank() ? "Cancelled by user" : reason);
        return OrderResponse.from(order);
    }

    // ---- Event-driven transitions (called by Kafka consumers). All are idempotent. ----

    @Transactional
    public void markConfirmed(Long orderId) {
        orderRepository.findById(orderId).ifPresentOrElse(order -> {
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.CONFIRMED);
                log.info("Order {} confirmed (stock reserved)", orderId);
            } else {
                log.info("Ignoring stock-reserved for order {} in status {}", orderId, order.getStatus());
            }
        }, () -> log.warn("stock-reserved for unknown order {}", orderId));
    }

    @Transactional
    public void markPaid(Long orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.CONFIRMED) {
                order.setStatus(OrderStatus.PAID);
                log.info("Order {} paid", orderId);
            }
        });
    }

    @Transactional
    public void cancelFromEvent(Long orderId, String reason) {
        orderRepository.findById(orderId).ifPresent(order -> {
            if (order.isCancellable()) {
                cancelAndPublish(order, reason);
            }
        });
    }

    private void cancelAndPublish(Order order, String reason) {
        order.cancel(reason);
        eventPublisher.publishEvent(new OrderCancelledEvent(order.getId(), order.getUserId(), reason, Instant.now()));
        log.info("Order {} cancelled: {}", order.getId(), reason);
    }
}
