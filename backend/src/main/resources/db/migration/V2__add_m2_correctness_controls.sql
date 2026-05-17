ALTER TABLE inventory_items
    ADD COLUMN version INTEGER NOT NULL DEFAULT 0;

CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(160) PRIMARY KEY,
    request_hash VARCHAR(128) NOT NULL,
    order_id UUID NOT NULL REFERENCES orders(id),
    response_snapshot TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_idempotency_records_order_id ON idempotency_records(order_id);
