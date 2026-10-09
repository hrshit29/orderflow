package com.orderflow.notificationservice.service;

import com.orderflow.events.PageResponse;
import com.orderflow.notificationservice.dto.NotificationResponse;
import com.orderflow.notificationservice.model.NotificationLog;
import com.orderflow.notificationservice.model.UserContact;
import com.orderflow.notificationservice.repository.NotificationLogRepository;
import com.orderflow.notificationservice.repository.UserContactRepository;
import com.orderflow.notificationservice.sender.NotificationSender;
import com.orderflow.notificationservice.service.NotificationTemplates.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationSender sender;
    private final NotificationLogRepository logRepository;
    private final UserContactRepository contactRepository;

    @Transactional
    public void saveContact(Long userId, String email, String fullName) {
        contactRepository.save(new UserContact(userId, email, fullName));
    }

    /**
     * Sends one notification for one event, exactly once from the user's point of view:
     * if this eventKey was already SENT (Kafka redelivery), it is skipped.
     * Throws on delivery failure so the Kafka error handler retries and, finally, dead-letters it.
     */
    public void notify(String eventKey, String eventType, Long userId, Function<String, Message> template) {
        if (logRepository.existsByEventKeyAndStatus(eventKey, NotificationLog.Status.SENT)) {
            log.info("Duplicate event {} — already notified, skipping", eventKey);
            return;
        }
        Optional<UserContact> contact = contactRepository.findById(userId);
        if (contact.isEmpty()) {
            Message msg = template.apply("customer");
            record(eventKey, eventType, userId, null, msg, NotificationLog.Status.SKIPPED,
                    "No contact details known for user " + userId);
            return;
        }
        Message msg = template.apply(contact.get().getFullName());
        sender.send(contact.get().getEmail(), msg.subject(), msg.body());
        record(eventKey, eventType, userId, contact.get().getEmail(), msg, NotificationLog.Status.SENT, null);
    }

    /** Called by the dead-letter recoverer once all retries are exhausted. */
    public void recordFailure(String eventKey, String eventType, Long userId, String reason) {
        record(eventKey, eventType, userId, null, new Message("(undelivered)", "(see notification-dlq)"),
                NotificationLog.Status.FAILED, reason);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, NotificationLog.Status status, Pageable pageable) {
        var page = userId != null ? logRepository.findByUserId(userId, pageable)
                : status != null ? logRepository.findByStatus(status, pageable)
                : logRepository.findAll(pageable);
        return PageResponse.from(page, NotificationResponse::from);
    }

    private void record(String eventKey, String eventType, Long userId, String recipient, Message msg,
                        NotificationLog.Status status, String reason) {
        // Upsert by eventKey: a FAILED/SKIPPED row is overwritten if a later redelivery succeeds.
        NotificationLog entry = logRepository.findByEventKey(eventKey).orElseGet(NotificationLog::new);
        entry.setEventKey(eventKey);
        entry.setEventType(eventType);
        entry.setUserId(userId);
        entry.setChannel(NotificationLog.Channel.EMAIL);
        entry.setRecipient(recipient);
        entry.setSubject(truncate(msg.subject(), 200));
        entry.setBody(truncate(msg.body(), 2000));
        entry.setStatus(status);
        entry.setFailureReason(truncate(reason, 500));
        logRepository.save(entry);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
