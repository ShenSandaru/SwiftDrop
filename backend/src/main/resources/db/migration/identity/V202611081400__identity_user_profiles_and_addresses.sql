-- Owner: identity module. Saved user addresses (phone_number on users is added in V202611131900).

CREATE TABLE user_addresses (
    id             UUID         PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    label          VARCHAR(50)  NOT NULL,
    recipient_name VARCHAR(200) NOT NULL,
    phone_number   VARCHAR(30)  NOT NULL,
    street_address VARCHAR(255) NOT NULL,
    city           VARCHAR(100) NOT NULL,
    postal_code    VARCHAR(20),
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_user_addresses_user_id ON user_addresses (user_id);

