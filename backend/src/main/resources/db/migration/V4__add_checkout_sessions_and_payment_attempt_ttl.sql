ALTER TABLE orders
    ADD COLUMN expires_at TIMESTAMPTZ;

CREATE TABLE checkout_sessions (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    active_payment_attempt_id UUID NOT NULL REFERENCES payment_attempts(id),
    status VARCHAR(40) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE payment_attempts
    ADD COLUMN idempotency_key VARCHAR(160),
    ADD COLUMN request_hash VARCHAR(128),
    ADD COLUMN response_snapshot TEXT,
    ADD COLUMN expires_at TIMESTAMPTZ,
    ADD COLUMN updated_at TIMESTAMPTZ;

CREATE TABLE payment_request_attempts (
    id UUID PRIMARY KEY,
    payment_attempt_id UUID NOT NULL REFERENCES payment_attempts(id),
    order_id UUID NOT NULL REFERENCES orders(id),
    idempotency_key VARCHAR(160) NOT NULL,
    status VARCHAR(40) NOT NULL,
    message VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_checkout_sessions_order_id ON checkout_sessions(order_id);
CREATE INDEX idx_checkout_sessions_status_expires_at ON checkout_sessions(status, expires_at);
CREATE UNIQUE INDEX idx_payment_attempts_idempotency_key
    ON payment_attempts(idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_payment_request_attempts_payment_attempt_id ON payment_request_attempts(payment_attempt_id);
CREATE INDEX idx_payment_request_attempts_idempotency_key ON payment_request_attempts(idempotency_key);
