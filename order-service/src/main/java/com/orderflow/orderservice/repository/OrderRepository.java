package com.orderflow.orderservice.repository;

import com.orderflow.orderservice.model.Order;
import com.orderflow.orderservice.model.OrderStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);

    /** Orders whose saga has stalled: still PENDING/CONFIRMED with no state change since {@code cutoff}. */
    List<Order> findTop100ByStatusInAndUpdatedAtBefore(Collection<OrderStatus> statuses, Instant cutoff);

    /** Composable filters for the paginated list endpoint. Null arguments are ignored. */
    final class Specs {
        private Specs() {
        }

        public static Specification<Order> userId(Long userId) {
            return (root, q, cb) -> userId == null ? null : cb.equal(root.get("userId"), userId);
        }

        public static Specification<Order> status(OrderStatus status) {
            return (root, q, cb) -> status == null ? null : cb.equal(root.get("status"), status);
        }

        public static Specification<Order> createdFrom(Instant from) {
            return (root, q, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
        }

        public static Specification<Order> createdTo(Instant to) {
            return (root, q, cb) -> to == null ? null : cb.lessThan(root.get("createdAt"), to);
        }
    }
}
