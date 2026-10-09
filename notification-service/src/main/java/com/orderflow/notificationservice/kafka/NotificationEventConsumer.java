package com.orderflow.notificationservice.kafka;

import com.orderflow.events.*;
import com.orderflow.notificationservice.service.NotificationService;
import com.orderflow.notificationservice.service.NotificationTemplates;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumer group "notification-service": run 3 replicas and Kafka splits the partitions
 * between them, so each event is handled by exactly one replica (no double-sends).
 * Failures propagate to the error handler in KafkaConfig: retry with backoff, then notification-dlq.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = Topics.USER_REGISTERED)
    public void onUserRegistered(UserRegisteredEvent e) {
        notificationService.saveContact(e.userId(), e.email(), e.fullName());
        notificationService.notify(key(Topics.USER_REGISTERED, e.userId()), Topics.USER_REGISTERED, e.userId(),
                name -> NotificationTemplates.welcome(e));
    }

    @KafkaListener(topics = Topics.ORDER_CREATED)
    public void onOrderCreated(OrderCreatedEvent e) {
        notificationService.notify(key(Topics.ORDER_CREATED, e.orderId()), Topics.ORDER_CREATED, e.userId(),
                name -> NotificationTemplates.orderCreated(e, name));
    }

    @KafkaListener(topics = Topics.ORDER_CANCELLED)
    public void onOrderCancelled(OrderCancelledEvent e) {
        notificationService.notify(key(Topics.ORDER_CANCELLED, e.orderId()), Topics.ORDER_CANCELLED, e.userId(),
                name -> NotificationTemplates.orderCancelled(e, name));
    }

    @KafkaListener(topics = Topics.PAYMENT_SUCCESS)
    public void onPaymentSuccess(PaymentSuccessEvent e) {
        notificationService.notify(key(Topics.PAYMENT_SUCCESS, e.orderId()), Topics.PAYMENT_SUCCESS, e.userId(),
                name -> NotificationTemplates.paymentSuccess(e, name));
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED)
    public void onPaymentFailed(PaymentFailedEvent e,
                                @Header(name = KafkaHeaders.RECEIVED_TOPIC) String topic) {
        notificationService.notify(key(topic, e.orderId()), topic, e.userId(),
                name -> NotificationTemplates.paymentFailed(e, name));
    }

    /** Idempotency key: same event redelivered => same key. */
    static String key(String topic, Long id) {
        return topic + ":" + id;
    }

    /** Pulls identifiers out of any event, for the dead-letter recoverer. */
    static Long userIdOf(Object event) {
        return switch (event) {
            case UserRegisteredEvent e -> e.userId();
            case OrderCreatedEvent e -> e.userId();
            case OrderCancelledEvent e -> e.userId();
            case PaymentSuccessEvent e -> e.userId();
            case PaymentFailedEvent e -> e.userId();
            case null, default -> null;
        };
    }

    static Long entityIdOf(Object event) {
        return switch (event) {
            case UserRegisteredEvent e -> e.userId();
            case OrderCreatedEvent e -> e.orderId();
            case OrderCancelledEvent e -> e.orderId();
            case PaymentSuccessEvent e -> e.orderId();
            case PaymentFailedEvent e -> e.orderId();
            case null, default -> null;
        };
    }
}
