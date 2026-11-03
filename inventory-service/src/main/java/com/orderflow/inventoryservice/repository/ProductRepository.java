package com.orderflow.inventoryservice.repository;

import com.orderflow.inventoryservice.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * SELECT ... FOR UPDATE on every product in the order. Two orders for the last unit
     * are serialised by the row lock, so stock can never go negative. Rows are locked in
     * id order so two orders touching the same products can't deadlock each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> lockAllById(@Param("ids") Collection<Long> ids);
}
