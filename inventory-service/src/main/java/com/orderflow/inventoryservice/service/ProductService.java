package com.orderflow.inventoryservice.service;

import com.orderflow.events.PageResponse;
import com.orderflow.inventoryservice.dto.InventoryDtos.*;
import com.orderflow.inventoryservice.exception.Exceptions.DuplicateResourceException;
import com.orderflow.inventoryservice.exception.Exceptions.ResourceNotFoundException;
import com.orderflow.inventoryservice.model.Product;
import com.orderflow.inventoryservice.repository.ProductRepository;
import com.orderflow.inventoryservice.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final StockReservationRepository reservationRepository;

    @Transactional
    public ProductResponse create(ProductRequest req) {
        if (productRepository.existsById(req.id())) {
            throw new DuplicateResourceException("Product already exists: " + req.id());
        }
        return ProductResponse.from(productRepository.save(
                new Product(req.id(), req.name(), req.price(), req.quantity())));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return ProductResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(Pageable pageable) {
        return PageResponse.from(productRepository.findAll(pageable), ProductResponse::from);
    }

    @Transactional
    public ProductResponse adjustStock(Long id, int delta) {
        Product p = find(id);
        if (p.getAvailableQuantity() + delta < 0) {
            throw new IllegalArgumentException("Cannot remove " + (-delta) + " units; only "
                    + p.getAvailableQuantity() + " available for product " + id);
        }
        p.setAvailableQuantity(p.getAvailableQuantity() + delta);
        return ProductResponse.from(p);
    }

    @Transactional(readOnly = true)
    public ReservationResponse reservation(Long orderId) {
        return reservationRepository.findById(orderId).map(ReservationResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("No reservation for order " + orderId));
    }

    private Product find(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }
}
