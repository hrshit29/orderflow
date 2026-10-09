package com.orderflow.notificationservice.service;

import com.orderflow.events.*;

/** One template per event type. Swap for Thymeleaf/Freemarker HTML templates later. */
public final class NotificationTemplates {

    private NotificationTemplates() {
    }

    public record Message(String subject, String body) {
    }

    public static Message welcome(UserRegisteredEvent e) {
        return new Message("Welcome to OrderFlow, " + e.fullName() + "!",
                "Hi " + e.fullName() + ", your account is ready. Happy shopping!");
    }

    public static Message orderCreated(OrderCreatedEvent e, String name) {
        return new Message("Order #" + e.orderId() + " received",
                "Hi " + name + ", we received your order #" + e.orderId() + " for Rs. " + e.totalAmount()
                        + " (" + e.items().size() + " item(s)). We'll update you once it's confirmed.");
    }

    public static Message paymentSuccess(PaymentSuccessEvent e, String name) {
        return new Message("Payment received for order #" + e.orderId(),
                "Hi " + name + ", your payment of Rs. " + e.amount() + " for order #" + e.orderId() + " succeeded.");
    }

    public static Message paymentFailed(PaymentFailedEvent e, String name) {
        return new Message("Payment failed for order #" + e.orderId(),
                "Hi " + name + ", your payment for order #" + e.orderId() + " failed: " + e.reason()
                        + ". The order has been cancelled.");
    }

    public static Message orderCancelled(OrderCancelledEvent e, String name) {
        return new Message("Order #" + e.orderId() + " cancelled",
                "Hi " + name + ", your order #" + e.orderId() + " was cancelled. Reason: " + e.reason() + ".");
    }
}
