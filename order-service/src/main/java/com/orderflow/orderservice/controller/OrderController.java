package com.orderflow.orderservice.controller;

import com.orderflow.events.Caller;
import com.orderflow.events.PageResponse;
import com.orderflow.orderservice.dto.OrderDtos.*;
import com.orderflow.orderservice.exception.Exceptions.ResourceNotFoundException;
import com.orderflow.orderservice.model.OrderStatus;
import com.orderflow.orderservice.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Customers act on their own orders only; the user id comes from the JWT (via the gateway),
 * never from the request body or query string. Admins see everything.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order lifecycle")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place an order for the logged-in user (publishes order-created)")
    public OrderResponse create(@Valid @RequestBody OrderRequest request,
                                @Parameter(hidden = true) @RequestHeader(value = Caller.USER_ID_HEADER, required = false) Long callerId,
                                @Parameter(hidden = true) @RequestHeader(value = Caller.ROLE_HEADER, required = false) String role) {
        Caller caller = Caller.of(callerId, role);
        Long userId = caller.isInternal() ? request.userId() : caller.userId();
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        return orderService.create(request.withUserId(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order with its line items")
    public OrderResponse get(@PathVariable Long id,
                             @Parameter(hidden = true) @RequestHeader(value = Caller.USER_ID_HEADER, required = false) Long callerId,
                             @Parameter(hidden = true) @RequestHeader(value = Caller.ROLE_HEADER, required = false) String role) {
        return owned(id, Caller.of(callerId, role));
    }

    @GetMapping
    @Operation(summary = "List orders — paginated, filterable by status and created date range (and user, for admins)",
            description = "Example: /api/v1/orders?status=PAID&from=2026-10-01T00:00:00Z&page=0&size=20&sort=createdAt,desc")
    public PageResponse<OrderSummary> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(hidden = true) @RequestHeader(value = Caller.USER_ID_HEADER, required = false) Long callerId,
            @Parameter(hidden = true) @RequestHeader(value = Caller.ROLE_HEADER, required = false) String role) {
        return orderService.list(Caller.of(callerId, role).scopeUserId(userId), status, from, to, pageable);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order (publishes order-cancelled)")
    public OrderResponse cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest request,
                                @Parameter(hidden = true) @RequestHeader(value = Caller.USER_ID_HEADER, required = false) Long callerId,
                                @Parameter(hidden = true) @RequestHeader(value = Caller.ROLE_HEADER, required = false) String role) {
        owned(id, Caller.of(callerId, role));
        return orderService.cancel(id, request == null ? null : request.reason());
    }

    /** Someone else's order looks exactly like a missing one (404), so ids can't be probed. */
    private OrderResponse owned(Long id, Caller caller) {
        OrderResponse order = orderService.get(id);
        if (!caller.owns(order.userId())) {
            throw new ResourceNotFoundException("Order not found: " + id);
        }
        return order;
    }
}
