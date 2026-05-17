package com.orderflow.order;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists and queries order aggregate records.
 */
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
}
