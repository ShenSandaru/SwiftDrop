-- Creates notification_push_tokens table for storing FCM Web Push tokens per user.

CREATE TABLE notification_push_tokens (
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL,
    token        TEXT        NOT NULL,
    platform     VARCHAR(20) NOT NULL DEFAULT 'WEB',
    is_active    BOOLEAN     NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,

    CONSTRAINT uq_notification_push_tokens_token UNIQUE (token)
);

CREATE INDEX ix_notification_push_tokens_user_active
    ON notification_push_tokens (user_id, is_active);
