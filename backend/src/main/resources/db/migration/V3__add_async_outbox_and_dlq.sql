CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(40) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    processed_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_status_next_attempt ON outbox_events(status, next_attempt_at);
CREATE INDEX idx_outbox_events_aggregate_id ON outbox_events(aggregate_id);
CREATE INDEX idx_outbox_events_event_type ON outbox_events(event_type);

CREATE TABLE dead_letter_events (
    id UUID PRIMARY KEY,
    outbox_event_id UUID NOT NULL REFERENCES outbox_events(id),
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload TEXT NOT NULL,
    retry_count INTEGER NOT NULL,
    last_error VARCHAR(500) NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    replayed_at TIMESTAMPTZ
);

CREATE INDEX idx_dead_letter_events_status ON dead_letter_events(status);
CREATE INDEX idx_dead_letter_events_aggregate_id ON dead_letter_events(aggregate_id);
