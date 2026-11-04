package com.orderflow.inventoryservice.controller;

import com.orderflow.events.PageResponse;
import com.orderflow.inventoryservice.dto.InventoryDtos.*;
import com.orderflow.inventoryservice.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Products, stock levels and per-order reservations")
public class InventoryController {

    private final ProductService productService;

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a product to the catalogue with its opening stock")
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "Get a product with available and reserved stock")
    public ProductResponse get(@PathVariable Long id) {
        return productService.get(id);
    }

    @GetMapping("/products")
    @Operation(summary = "List products, paginated")
    public PageResponse<ProductResponse> list(@ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return productService.list(pageable);
    }

    @PatchMapping("/products/{id}/stock")
    @Operation(summary = "Add (positive delta) or remove (negative delta) available stock")
    public ProductResponse adjustStock(@PathVariable Long id, @Valid @RequestBody StockAdjustmentRequest request) {
        return productService.adjustStock(id, request.delta());
    }

    @GetMapping("/reservations/{orderId}")
    @Operation(summary = "What inventory did for an order: RESERVED, REJECTED or RELEASED")
    public ReservationResponse reservation(@PathVariable Long orderId) {
        return productService.reservation(orderId);
    }
}
