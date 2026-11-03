package com.orderflow.inventoryservice.service;

import com.orderflow.events.OrderCancelledEvent;
import com.orderflow.events.OrderCreatedEvent;
import com.orderflow.events.StockInsufficientEvent;
import com.orderflow.events.StockReservedEvent;
import com.orderflow.inventoryservice.model.Product;
import com.orderflow.inventoryservice.model.StockReservation;
import com.orderflow.inventoryservice.model.StockReservation.ReservationLine;
import com.orderflow.inventoryservice.model.StockReservation.Status;
import com.orderflow.inventoryservice.repository.ProductRepository;
import com.orderflow.inventoryservice.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The inventory step of the order saga.
 *
 * order-created   -> reserve every line or nothing  -> stock-reserved | stock-insufficient
 * order-cancelled -> put reserved stock back          (compensation for user cancel / payment failure)
 *
 * Both handlers are idempotent: the stock_reservations row for the order records what was done.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final StockReservationRepository reservationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void reserve(OrderCreatedEvent event) {
        Long orderId = event.orderId();
        if (reservationRepository.existsById(orderId)) {
            // Redelivered event, or the order was cancelled before we saw it being created.
            log.info("Order {} already handled ({}), skipping order-created",
                    orderId, reservationRepository.findById(orderId).map(StockReservation::getStatus).orElse(null));
            return;
        }

        // Same product can appear on several lines: sum per product. TreeMap keeps lock order stable.
        Map<Long, Integer> wanted = event.items().stream().collect(Collectors.toMap(
                OrderCreatedEvent.OrderLine::productId, OrderCreatedEvent.OrderLine::quantity, Integer::sum, TreeMap::new));

        Map<Long, Product> products = productRepository.lockAllById(wanted.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (Map.Entry<Long, Integer> line : wanted.entrySet()) {
            Product product = products.get(line.getKey());
            if (product == null || !product.canReserve(line.getValue())) {
                String reason = product == null
                        ? "Unknown product " + line.getKey()
                        : "Insufficient stock for product " + line.getKey()
                          + " (wanted " + line.getValue() + ", available " + product.getAvailableQuantity() + ")";
                reservationRepository.save(new StockReservation(orderId, event.userId(), Status.REJECTED, reason));
                eventPublisher.publishEvent(new StockInsufficientEvent(orderId, event.userId(), line.getKey(), Instant.now()));
                log.info("Order {} rejected: {}", orderId, reason);
                return;
            }
        }

        StockReservation reservation = new StockReservation(orderId, event.userId(), Status.RESERVED, null);
        wanted.forEach((productId, qty) -> {
            products.get(productId).reserve(qty);
            reservation.getLines().add(new ReservationLine(productId, qty));
        });
        reservationRepository.save(reservation);
        eventPublisher.publishEvent(new StockReservedEvent(orderId, event.userId(), event.totalAmount(), Instant.now()));
        log.info("Order {} reserved {}", orderId, wanted);
    }

    @Transactional
    public void release(OrderCancelledEvent event) {
        Long orderId = event.orderId();
        StockReservation reservation = reservationRepository.findById(orderId).orElse(null);

        if (reservation == null) {
            // Cancel overtook create (different topics, no cross-topic ordering). Leave a tombstone
            // so the late order-created is ignored instead of reserving stock for a dead order.
            reservationRepository.save(new StockReservation(orderId, event.userId(), Status.RELEASED,
                    "Cancelled before reservation: " + event.reason()));
            log.info("Order {} cancelled before reservation; recorded tombstone", orderId);
            return;
        }
        if (reservation.getStatus() != Status.RESERVED) {
            log.info("Order {} is {}, nothing to release", orderId, reservation.getStatus());
            return;
        }

        Map<Long, Integer> held = new TreeMap<>();
        reservation.getLines().forEach(l -> held.merge(l.getProductId(), l.getQuantity(), Integer::sum));
        Map<Long, Product> products = productRepository.lockAllById(held.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        held.forEach((productId, qty) -> {
            Product p = products.get(productId);
            if (p != null) p.release(qty);
            else log.warn("Product {} no longer exists; cannot return {} units for order {}", productId, qty, orderId);
        });

        reservation.setStatus(Status.RELEASED);
        reservation.setReason(event.reason());
        log.info("Order {} cancelled; released {}", orderId, held);
    }
}
