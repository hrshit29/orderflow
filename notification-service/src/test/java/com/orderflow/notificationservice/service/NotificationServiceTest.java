package com.orderflow.notificationservice.service;

import com.orderflow.notificationservice.model.NotificationLog;
import com.orderflow.notificationservice.model.UserContact;
import com.orderflow.notificationservice.repository.NotificationLogRepository;
import com.orderflow.notificationservice.repository.UserContactRepository;
import com.orderflow.notificationservice.sender.NotificationSender;
import com.orderflow.notificationservice.service.NotificationTemplates.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private NotificationSender sender;
    private NotificationLogRepository logRepo;
    private UserContactRepository contactRepo;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        sender = mock(NotificationSender.class);
        logRepo = mock(NotificationLogRepository.class);
        contactRepo = mock(UserContactRepository.class);
        service = new NotificationService(sender, logRepo, contactRepo);
        when(logRepo.findByEventKey(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void sendsAndLogsAsSent() {
        when(contactRepo.findById(7L)).thenReturn(Optional.of(new UserContact(7L, "asha@x.com", "Asha")));

        service.notify("order-created:1", "order-created", 7L, name -> new Message("Hi", "Hello " + name));

        verify(sender).send("asha@x.com", "Hi", "Hello Asha");
        verify(logRepo).save(argThat(l -> l.getStatus() == NotificationLog.Status.SENT));
    }

    @Test
    void duplicateEventIsNotSentTwice() {
        when(logRepo.existsByEventKeyAndStatus("order-created:1", NotificationLog.Status.SENT)).thenReturn(true);

        service.notify("order-created:1", "order-created", 7L, name -> new Message("Hi", "x"));

        verifyNoInteractions(sender);
    }

    @Test
    void deliveryFailurePropagatesSoKafkaCanRetry() {
        when(contactRepo.findById(7L)).thenReturn(Optional.of(new UserContact(7L, "asha@x.com", "Asha")));
        doThrow(new RuntimeException("SMTP down")).when(sender).send(any(), any(), any());

        assertThatThrownBy(() -> service.notify("order-created:1", "order-created", 7L,
                name -> new Message("Hi", "x"))).hasMessage("SMTP down");
        verify(logRepo, never()).save(any());
    }

    @Test
    void unknownUserIsSkippedNotRetried() {
        when(contactRepo.findById(9L)).thenReturn(Optional.empty());

        service.notify("order-created:2", "order-created", 9L, name -> new Message("Hi", "x"));

        verifyNoInteractions(sender);
        verify(logRepo).save(argThat(l -> l.getStatus() == NotificationLog.Status.SKIPPED));
    }
}
