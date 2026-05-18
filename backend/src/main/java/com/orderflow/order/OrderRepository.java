package com.orderflow.order;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists and queries order aggregate records.
 */
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    /**
     * Lists recent orders for the operations console.
     *
     * @return orders sorted from newest to oldest
     */
    List<OrderEntity> findAllByOrderByCreatedAtDesc();
}
