package com.orderflow.notificationservice.sender;

/**
 * Delivery channel abstraction. Add SmsNotificationSender (Twilio) or
 * PushNotificationSender (FCM) as further implementations.
 * Implementations throw on failure so Kafka's error handler can retry.
 */
public interface NotificationSender {

    void send(String recipient, String subject, String body);
}
