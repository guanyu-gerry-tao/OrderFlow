package com.orderflow.checkout;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists checkout sessions.
 */
public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, UUID> {
}
