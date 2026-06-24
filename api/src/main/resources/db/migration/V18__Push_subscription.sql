-- Web Push subscriptions for the waiter PWA. One row per browser/device subscription, keyed by the
-- staff username so we can push an OS notification (e.g. "order ready") even when the app is closed.
CREATE TABLE push_subscription (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username   VARCHAR(100) NOT NULL,
    endpoint   TEXT NOT NULL UNIQUE,
    p256dh     TEXT NOT NULL,
    auth       TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_push_subscription_username ON push_subscription (username);
