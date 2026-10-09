package com.orderflow.notificationservice.repository;

import com.orderflow.notificationservice.model.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    boolean existsByEventKeyAndStatus(String eventKey, NotificationLog.Status status);

    Optional<NotificationLog> findByEventKey(String eventKey);

    Page<NotificationLog> findByUserId(Long userId, Pageable pageable);

    Page<NotificationLog> findByStatus(NotificationLog.Status status, Pageable pageable);
}
