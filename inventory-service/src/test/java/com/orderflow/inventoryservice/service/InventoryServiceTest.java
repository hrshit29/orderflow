package com.orderflow.inventoryservice.service;

import com.orderflow.events.OrderCancelledEvent;
import com.orderflow.events.OrderCreatedEvent;
import com.orderflow.events.OrderCreatedEvent.OrderLine;
import com.orderflow.events.StockInsufficientEvent;
import com.orderflow.events.StockReservedEvent;
import com.orderflow.inventoryservice.model.Product;
import com.orderflow.inventoryservice.model.StockReservation;
import com.orderflow.inventoryservice.model.StockReservation.Status;
import com.orderflow.inventoryservice.repository.ProductRepository;
import com.orderflow.inventoryservice.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class InventoryServiceTest {

    private ProductRepository products;
    private StockReservationRepository reservations;
    private ApplicationEventPublisher publisher;
    private InventoryService service;

    private final Map<Long, Product> catalogue = new HashMap<>();
    private final Map<Long, StockReservation> stored = new HashMap<>();

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        reservations = mock(StockReservationRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new InventoryService(products, reservations, publisher);

        catalogue.put(101L, new Product(101L, "Mouse", new BigDecimal("499.00"), 5));
        catalogue.put(205L, new Product(205L, "Keyboard", new BigDecimal("1299.00"), 1));

        when(products.lockAllById(anyCollection())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return ids.stream().map(catalogue::get).filter(Objects::nonNull).toList();
        });
        when(reservations.existsById(any())).thenAnswer(inv -> stored.containsKey(inv.<Long>getArgument(0)));
        when(reservations.findById(any())).thenAnswer(inv -> Optional.ofNullable(stored.get(inv.<Long>getArgument(0))));
        when(reservations.save(any(StockReservation.class))).thenAnswer(inv -> {
            StockReservation r = inv.getArgument(0);
            stored.put(r.getOrderId(), r);
            return r;
        });
    }

    @Test
    void reservesAllLinesAndPublishesStockReserved() {
        service.reserve(order(1L, line(101L, 2), line(205L, 1)));

        assertThat(catalogue.get(101L).getAvailableQuantity()).isEqualTo(3);
        assertThat(catalogue.get(101L).getReservedQuantity()).isEqualTo(2);
        assertThat(catalogue.get(205L).getAvailableQuantity()).isZero();
        assertThat(stored.get(1L).getStatus()).isEqualTo(Status.RESERVED);
        verify(publisher).publishEvent(any(StockReservedEvent.class));
    }

    @Test
    void oneShortLineRejectsWholeOrderAndReservesNothing() {
        service.reserve(order(1L, line(101L, 2), line(205L, 2))); // only 1 keyboard

        assertThat(catalogue.get(101L).getAvailableQuantity()).isEqualTo(5);
        assertThat(catalogue.get(205L).getAvailableQuantity()).isEqualTo(1);
        assertThat(stored.get(1L).getStatus()).isEqualTo(Status.REJECTED);
        verify(publisher).publishEvent(any(StockInsufficientEvent.class));
        verify(publisher, never()).publishEvent(any(StockReservedEvent.class));
    }

    @Test
    void unknownProductIsRejected() {
        service.reserve(order(1L, line(999L, 1)));

        assertThat(stored.get(1L).getStatus()).isEqualTo(Status.REJECTED);
        verify(publisher).publishEvent(any(StockInsufficientEvent.class));
    }

    @Test
    void sameProductOnTwoLinesIsSummed() {
        service.reserve(order(1L, line(205L, 1), line(205L, 1))); // 2 keyboards wanted, 1 in stock

        assertThat(catalogue.get(205L).getAvailableQuantity()).isEqualTo(1);
        assertThat(stored.get(1L).getStatus()).isEqualTo(Status.REJECTED);
    }

    @Test
    void redeliveredOrderCreatedDoesNotReserveTwice() {
        OrderCreatedEvent e = order(1L, line(101L, 2));
        service.reserve(e);
        service.reserve(e);

        assertThat(catalogue.get(101L).getAvailableQuantity()).isEqualTo(3);
        verify(publisher, times(1)).publishEvent(any(StockReservedEvent.class));
    }

    @Test
    void cancelReleasesStockOnlyOnce() {
        service.reserve(order(1L, line(101L, 2)));
        OrderCancelledEvent cancel = new OrderCancelledEvent(1L, 1L, "Payment failed", Instant.now());

        service.release(cancel);
        service.release(cancel); // redelivered

        assertThat(catalogue.get(101L).getAvailableQuantity()).isEqualTo(5);
        assertThat(catalogue.get(101L).getReservedQuantity()).isZero();
        assertThat(stored.get(1L).getStatus()).isEqualTo(Status.RELEASED);
    }

    @Test
    void cancelOfRejectedOrderReturnsNothing() {
        service.reserve(order(1L, line(205L, 5)));
        service.release(new OrderCancelledEvent(1L, 1L, "Insufficient stock", Instant.now()));

        assertThat(catalogue.get(205L).getAvailableQuantity()).isEqualTo(1);
    }

    @Test
    void cancelArrivingBeforeCreateBlocksTheLateReservation() {
        service.release(new OrderCancelledEvent(1L, 1L, "Changed my mind", Instant.now()));
        service.reserve(order(1L, line(101L, 2)));

        assertThat(catalogue.get(101L).getAvailableQuantity()).isEqualTo(5);
        verifyNoInteractions(publisher);
    }

    private static OrderLine line(Long productId, int qty) {
        return new OrderLine(productId, qty, BigDecimal.TEN);
    }

    private static OrderCreatedEvent order(Long orderId, OrderLine... lines) {
        return new OrderCreatedEvent(orderId, 1L, BigDecimal.TEN, List.of(lines), Instant.now());
    }
}
