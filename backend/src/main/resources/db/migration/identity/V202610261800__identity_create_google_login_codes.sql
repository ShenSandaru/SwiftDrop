-- Owner: identity module (ADR 0005). Short-lived exchange codes for Google OAuth token handoff.

CREATE TABLE google_login_codes (
    code_hash   VARCHAR(64)  PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ  NULL
);

CREATE INDEX idx_google_login_codes_expires_at ON google_login_codes (expires_at);
