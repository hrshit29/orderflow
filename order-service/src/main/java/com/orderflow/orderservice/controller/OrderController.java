package com.orderflow.orderservice.controller;

import com.orderflow.events.PageResponse;
import com.orderflow.orderservice.dto.OrderDtos.*;
import com.orderflow.orderservice.model.OrderStatus;
import com.orderflow.orderservice.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order lifecycle")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place an order (publishes order-created)")
    public OrderResponse create(@Valid @RequestBody OrderRequest request) {
        return orderService.create(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order with its line items")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.get(id);
    }

    @GetMapping
    @Operation(summary = "List orders — paginated, filterable by user, status and created date range",
            description = "Example: /api/v1/orders?status=PAID&from=2026-10-01T00:00:00Z&page=0&size=20&sort=createdAt,desc")
    public PageResponse<OrderSummary> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.list(userId, status, from, to, pageable);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order (publishes order-cancelled)")
    public OrderResponse cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest request) {
        return orderService.cancel(id, request == null ? null : request.reason());
    }
}
