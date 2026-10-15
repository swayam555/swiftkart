CREATE TABLE orders (
    id                VARCHAR(40) PRIMARY KEY,
    version           BIGINT NOT NULL DEFAULT 0,
    customer_id       VARCHAR(60) NOT NULL,
    customer_name     VARCHAR(80),
    store_id          VARCHAR(16) NOT NULL,
    status            VARCHAR(24) NOT NULL,
    lines             TEXT NOT NULL,
    item_count        INT NOT NULL,
    item_total        NUMERIC(12, 2) NOT NULL,
    delivery_fee      NUMERIC(8, 2) NOT NULL,
    total             NUMERIC(12, 2) NOT NULL,
    customer_lat      DOUBLE PRECISION NOT NULL,
    customer_lon      DOUBLE PRECISION NOT NULL,
    address           VARCHAR(200),
    distance_km       DOUBLE PRECISION NOT NULL,
    promised_minutes  INT NOT NULL,
    promised_by       TIMESTAMPTZ NOT NULL,
    expected_by       TIMESTAMPTZ,
    reservation_expires_at TIMESTAMPTZ,
    payment_id        VARCHAR(40),
    rider_id          VARCHAR(40),
    rider_name        VARCHAR(80),
    batched_with      VARCHAR(40),
    cancel_reason     VARCHAR(200),
    source            VARCHAR(20) NOT NULL DEFAULT 'APP',
    placed_at         TIMESTAMPTZ NOT NULL,
    paid_at           TIMESTAMPTZ,
    packing_at        TIMESTAMPTZ,
    packed_at         TIMESTAMPTZ,
    rider_assigned_at TIMESTAMPTZ,
    picked_up_at      TIMESTAMPTZ,
    delivered_at      TIMESTAMPTZ,
    closed_at         TIMESTAMPTZ
);
CREATE INDEX idx_orders_customer ON orders (customer_id, placed_at DESC);
CREATE INDEX idx_orders_status ON orders (status, store_id);
CREATE INDEX idx_orders_placed ON orders (placed_at DESC);

CREATE TABLE order_timeline (
    id       BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(40) NOT NULL REFERENCES orders (id),
    at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    label    VARCHAR(60) NOT NULL,
    detail   VARCHAR(300)
);
CREATE INDEX idx_timeline_order ON order_timeline (order_id, at);

-- Consumer-side de-duplication: Kafka delivers at least once.
CREATE TABLE processed_events (
    event_id     VARCHAR(40) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_events (
    id           BIGSERIAL PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    event_key    VARCHAR(100) NOT NULL,
    payload      TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (id) WHERE published_at IS NULL;

CREATE TABLE payments (
    id         VARCHAR(40) PRIMARY KEY,
    order_id   VARCHAR(40) NOT NULL UNIQUE,
    amount     NUMERIC(12, 2) NOT NULL,
    method     VARCHAR(20) NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    refunded_at TIMESTAMPTZ
);
