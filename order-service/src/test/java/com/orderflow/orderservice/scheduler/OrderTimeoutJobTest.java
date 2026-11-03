package com.orderflow.orderservice.scheduler;

import com.orderflow.events.OrderCancelledEvent;
import com.orderflow.orderservice.model.Order;
import com.orderflow.orderservice.model.OrderStatus;
import com.orderflow.orderservice.repository.OrderRepository;
import com.orderflow.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Uses a real OrderService (only interfaces are mocked), so it runs on any JDK. */
class OrderTimeoutJobTest {

    private final OrderRepository repo = mock(OrderRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final OrderTimeoutJob job =
            new OrderTimeoutJob(repo, new OrderService(repo, publisher), Duration.ofMinutes(15));

    @Test
    void cancelsEveryStaleOrderEvenIfOneFails() {
        Order pending = order(1L, OrderStatus.PENDING);
        Order confirmed = order(2L, OrderStatus.CONFIRMED);
        when(repo.findTop100ByStatusInAndUpdatedAtBefore(anyCollection(), any()))
                .thenReturn(List.of(pending, confirmed));
        when(repo.findById(1L)).thenThrow(new ObjectOptimisticLockingFailureException(Order.class, 1L));
        when(repo.findById(2L)).thenReturn(Optional.of(confirmed));

        job.expireStaleOrders();

        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(confirmed.getCancellationReason()).contains("Timed out in CONFIRMED");
        verify(publisher, times(1)).publishEvent(any(OrderCancelledEvent.class));
    }

    @Test
    void onlyLooksAtInFlightOrders() {
        job.expireStaleOrders();

        verify(repo).findTop100ByStatusInAndUpdatedAtBefore(
                argThat(statuses -> statuses.size() == 2
                        && statuses.contains(OrderStatus.PENDING) && statuses.contains(OrderStatus.CONFIRMED)),
                any());
        verifyNoInteractions(publisher);
    }

    private static Order order(Long id, OrderStatus status) {
        Order o = new Order();
        o.setId(id);
        o.setUserId(1L);
        o.setStatus(status);
        return o;
    }
}
