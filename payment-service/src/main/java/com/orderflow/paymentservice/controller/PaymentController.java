package com.orderflow.paymentservice.controller;

import com.orderflow.events.Caller;
import com.orderflow.events.PageResponse;
import com.orderflow.paymentservice.dto.PaymentResponse;
import com.orderflow.paymentservice.gateway.SimulatedPaymentGateway;
import com.orderflow.paymentservice.model.Payment;
import com.orderflow.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment status per order; gateway outage switch for demos")
public class PaymentController {

    private final PaymentService paymentService;
    private final SimulatedPaymentGateway gateway;

    @GetMapping("/{orderId}")
    @Operation(summary = "Payment for an order")
    public PaymentResponse get(@PathVariable Long orderId,
                               @Parameter(hidden = true) @RequestHeader(value = Caller.USER_ID_HEADER, required = false) Long callerId,
                               @Parameter(hidden = true) @RequestHeader(value = Caller.ROLE_HEADER, required = false) String role) {
        PaymentResponse payment = paymentService.get(orderId);
        if (!Caller.of(callerId, role).owns(payment.userId())) {
            throw new PaymentService.PaymentNotFoundException("No payment for order " + orderId);
        }
        return payment;
    }

    @GetMapping
    @Operation(summary = "List payments, paginated, filterable by user and status")
    public PageResponse<PaymentResponse> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Payment.Status status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(hidden = true) @RequestHeader(value = Caller.USER_ID_HEADER, required = false) Long callerId,
            @Parameter(hidden = true) @RequestHeader(value = Caller.ROLE_HEADER, required = false) String role) {
        return paymentService.list(Caller.of(callerId, role).scopeUserId(userId), status, pageable);
    }

    @PostMapping("/gateway/outage")
    @Operation(summary = "Admin, demo only: make the simulated gateway time out (enabled=true) or recover (enabled=false)")
    public Map<String, Object> outage(@RequestParam boolean enabled) {
        gateway.setOutage(enabled);
        return Map.of("outage", gateway.isOutage());
    }

    @ExceptionHandler(PaymentService.PaymentNotFoundException.class)
    ProblemDetail notFound(PaymentService.PaymentNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
}
