package com.orderflow.paymentservice.service;

import com.orderflow.events.OrderCancelledEvent;
import com.orderflow.events.PaymentFailedEvent;
import com.orderflow.events.PaymentSuccessEvent;
import com.orderflow.events.StockReservedEvent;
import com.orderflow.paymentservice.gateway.PaymentGateway;
import com.orderflow.paymentservice.gateway.PaymentGateway.GatewayUnavailableException;
import com.orderflow.paymentservice.model.Payment;
import com.orderflow.paymentservice.model.Payment.Status;
import com.orderflow.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    private PaymentGateway gateway;
    private ApplicationEventPublisher publisher;
    private PaymentService service;
    private final Map<Long, Payment> rows = new HashMap<>();

    @BeforeEach
    void setUp() {
        PaymentRepository repo = mock(PaymentRepository.class);
        gateway = mock(PaymentGateway.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new PaymentService(repo, gateway, publisher, TransactionOperations.withoutTransaction());

        when(repo.lockByOrderId(any())).thenAnswer(inv -> Optional.ofNullable(rows.get(inv.<Long>getArgument(0))));
        when(repo.findByOrderId(any())).thenAnswer(inv -> Optional.ofNullable(rows.get(inv.<Long>getArgument(0))));
        when(repo.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) p.setId((long) rows.size() + 1);
            rows.put(p.getOrderId(), p);
            return p;
        });
    }

    @Test
    void approvedChargePublishesPaymentSuccess() {
        when(gateway.charge(eq("order-1"), any())).thenReturn(PaymentGateway.Result.approved("REF1"));

        service.charge(stockReserved(1L, "499.00"));

        assertThat(rows.get(1L).getStatus()).isEqualTo(Status.SUCCEEDED);
        assertThat(rows.get(1L).getGatewayReference()).isEqualTo("REF1");
        verify(publisher).publishEvent(any(PaymentSuccessEvent.class));
    }

    @Test
    void declinedChargePublishesPaymentFailed() {
        when(gateway.charge(any(), any())).thenReturn(PaymentGateway.Result.declined("Card declined"));

        service.charge(stockReserved(1L, "20000.00"));

        assertThat(rows.get(1L).getStatus()).isEqualTo(Status.FAILED);
        verify(publisher).publishEvent(any(PaymentFailedEvent.class));
        verify(publisher, never()).publishEvent(any(PaymentSuccessEvent.class));
    }

    @Test
    void redeliveredStockReservedChargesOnce() {
        when(gateway.charge(any(), any())).thenReturn(PaymentGateway.Result.approved("REF1"));

        service.charge(stockReserved(1L, "499.00"));
        service.charge(stockReserved(1L, "499.00"));

        verify(gateway, times(1)).charge(any(), any());
        verify(publisher, times(1)).publishEvent(any(PaymentSuccessEvent.class));
    }

    @Test
    void gatewayTimeoutLeavesPendingAndRetryResumesWithSameKey() {
        when(gateway.charge(eq("order-1"), any()))
                .thenThrow(new GatewayUnavailableException("timeout"))
                .thenReturn(PaymentGateway.Result.approved("REF1"));

        assertThatThrownBy(() -> service.charge(stockReserved(1L, "499.00")))
                .isInstanceOf(GatewayUnavailableException.class);
        assertThat(rows.get(1L).getStatus()).isEqualTo(Status.PENDING);

        service.charge(stockReserved(1L, "499.00")); // Kafka retry

        assertThat(rows.get(1L).getStatus()).isEqualTo(Status.SUCCEEDED);
        verify(gateway, times(2)).charge(eq("order-1"), any());
    }

    @Test
    void retriesExhaustedFailsThePendingPayment() {
        when(gateway.charge(any(), any())).thenThrow(new GatewayUnavailableException("timeout"));
        assertThatThrownBy(() -> service.charge(stockReserved(1L, "499.00")));

        service.failAfterRetries(stockReserved(1L, "499.00"), "Payment gateway unavailable: timeout");

        assertThat(rows.get(1L).getStatus()).isEqualTo(Status.FAILED);
        verify(publisher).publishEvent(any(PaymentFailedEvent.class));
    }

    @Test
    void cancelBeforeStockReservedMeansNeverCharged() {
        service.cancel(cancelled(1L));
        service.charge(stockReserved(1L, "499.00"));

        assertThat(rows.get(1L).getStatus()).isEqualTo(Status.CANCELLED);
        verifyNoInteractions(gateway);
        verifyNoInteractions(publisher);
    }

    @Test
    void cancelDuringChargeRefundsInsteadOfPublishingSuccess() {
        when(gateway.charge(any(), any())).thenAnswer(inv -> {
            service.cancel(cancelled(1L)); // user cancels while the gateway call is in flight
            return PaymentGateway.Result.approved("REF1");
        });

        service.charge(stockReserved(1L, "499.00"));

        assertThat(rows.get(1L).getStatus()).isEqualTo(Status.REFUNDED);
        verify(gateway).refund(eq("REF1"), any());
        verify(publisher, never()).publishEvent(any(PaymentSuccessEvent.class));
    }

    @Test
    void cancelAfterSuccessRefundsOnce() {
        when(gateway.charge(any(), any())).thenReturn(PaymentGateway.Result.approved("REF1"));
        service.charge(stockReserved(1L, "499.00"));

        service.cancel(cancelled(1L));
        service.cancel(cancelled(1L)); // redelivered

        assertThat(rows.get(1L).getStatus()).isEqualTo(Status.REFUNDED);
        verify(gateway, times(1)).refund(eq("REF1"), any());
    }

    @Test
    void cancelAfterDeclineDoesNothing() {
        when(gateway.charge(any(), any())).thenReturn(PaymentGateway.Result.declined("Card declined"));
        service.charge(stockReserved(1L, "20000.00"));

        service.cancel(cancelled(1L));

        assertThat(rows.get(1L).getStatus()).isEqualTo(Status.FAILED);
        verify(gateway, never()).refund(any(), any());
    }

    private static StockReservedEvent stockReserved(Long orderId, String amount) {
        return new StockReservedEvent(orderId, 1L, new BigDecimal(amount), Instant.now());
    }

    private static OrderCancelledEvent cancelled(Long orderId) {
        return new OrderCancelledEvent(orderId, 1L, "Changed my mind", Instant.now());
    }
}
