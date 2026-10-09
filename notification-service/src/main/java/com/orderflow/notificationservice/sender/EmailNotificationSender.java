package com.orderflow.notificationservice.sender;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Real SMTP delivery. In docker-compose this points at MailHog (UI on localhost:8025). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.channel", havingValue = "email")
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;

    @Value("${notification.from:no-reply@orderflow.local}")
    private String from;

    @Override
    public void send(String recipient, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(recipient);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg); // throws MailException when SMTP is down -> retried by Kafka error handler
    }
}
