package com.orderflow.notificationservice.dto;

import com.orderflow.notificationservice.model.NotificationLog;

import java.time.Instant;

public record NotificationResponse(Long id, Long userId, String eventType, String channel, String recipient,
                                   String subject, String body, String status, String failureReason,
                                   Instant createdAt) {

    public static NotificationResponse from(NotificationLog n) {
        return new NotificationResponse(n.getId(), n.getUserId(), n.getEventType(), n.getChannel().name(),
                n.getRecipient(), n.getSubject(), n.getBody(), n.getStatus().name(), n.getFailureReason(),
                n.getCreatedAt());
    }
}
