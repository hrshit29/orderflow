package com.orderflow.notificationservice.sender;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default for running without an SMTP server: prints the notification to the console. */
@Slf4j
@Component
@ConditionalOnProperty(name = "notification.channel", havingValue = "log", matchIfMissing = true)
public class LoggingNotificationSender implements NotificationSender {

    @Override
    public void send(String recipient, String subject, String body) {
        log.info("[NOTIFICATION] to={} subject='{}' body='{}'", recipient, subject, body);
    }
}
