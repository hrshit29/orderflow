package com.orderflow.orderservice.service;

import com.orderflow.events.OrderCancelledEvent;
import com.orderflow.events.OrderCreatedEvent;
import com.orderflow.orderservice.dto.OrderDtos.*;
import com.orderflow.orderservice.exception.Exceptions.InvalidOrderStateException;
import com.orderflow.orderservice.model.Order;
import com.orderflow.orderservice.model.OrderStatus;
import com.orderflow.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceTest {

    private OrderRepository repo;
    private ApplicationEventPublisher publisher;
    private OrderService service;

    @BeforeEach
    void setUp() {
        repo = mock(OrderRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new OrderService(repo, publisher);
    }

    @Test
    void createComputesTotalAndPublishesEvent() {
        when(repo.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(10L);
            return o;
        });

        OrderResponse res = service.create(new OrderRequest(1L, List.of(
                new OrderItemRequest(100L, 2, new BigDecimal("250.00")),
                new OrderItemRequest(101L, 1, new BigDecimal("99.50")))));

        assertThat(res.totalAmount()).isEqualByComparingTo("599.50");
        assertThat(res.status()).isEqualTo("PENDING");
        verify(publisher).publishEvent(any(OrderCreatedEvent.class));
    }

    @Test
    void cannotCancelPaidOrder() {
        Order paid = order(OrderStatus.PAID);
        when(repo.findWithItemsById(1L)).thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> service.cancel(1L, null)).isInstanceOf(InvalidOrderStateException.class);
        verifyNoInteractions(publisher);
    }

    @Test
    void paymentFailedCancelsConfirmedOrderOnce() {
        Order confirmed = order(OrderStatus.CONFIRMED);
        when(repo.findById(1L)).thenReturn(Optional.of(confirmed));

        service.cancelFromEvent(1L, "card declined");
        service.cancelFromEvent(1L, "card declined"); // redelivered event: must be a no-op

        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(publisher, times(1)).publishEvent(any(OrderCancelledEvent.class));
    }

    @Test
    void stockReservedOnlyConfirmsPendingOrders() {
        Order cancelled = order(OrderStatus.CANCELLED);
        when(repo.findById(1L)).thenReturn(Optional.of(cancelled));

        service.markConfirmed(1L);

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    private static Order order(OrderStatus status) {
        Order o = new Order();
        o.setId(1L);
        o.setUserId(1L);
        o.setStatus(status);
        return o;
    }
}
