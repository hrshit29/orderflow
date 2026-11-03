package com.orderflow.inventoryservice.repository;

import com.orderflow.inventoryservice.model.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {
}
