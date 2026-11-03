package com.orderflow.paymentservice.repository;

import com.orderflow.paymentservice.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    /**
     * SELECT ... FOR UPDATE: the stock-reserved and order-cancelled listeners run on different
     * threads, so every state change on a payment takes the row lock first.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.orderId = :orderId")
    Optional<Payment> lockByOrderId(@Param("orderId") Long orderId);

    Page<Payment> findByUserId(Long userId, Pageable pageable);

    Page<Payment> findByStatus(Payment.Status status, Pageable pageable);

    Page<Payment> findByUserIdAndStatus(Long userId, Payment.Status status, Pageable pageable);
}
