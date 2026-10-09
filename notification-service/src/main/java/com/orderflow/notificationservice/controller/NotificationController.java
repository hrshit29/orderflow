package com.orderflow.notificationservice.controller;

import com.orderflow.events.PageResponse;
import com.orderflow.notificationservice.dto.NotificationResponse;
import com.orderflow.notificationservice.model.NotificationLog;
import com.orderflow.notificationservice.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification history")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Notification history — paginated, filter by userId or status (SENT/SKIPPED/FAILED)")
    public PageResponse<NotificationResponse> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) NotificationLog.Status status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return notificationService.list(userId, status, pageable);
    }
}
