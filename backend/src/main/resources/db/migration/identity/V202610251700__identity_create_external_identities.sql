-- Owner: identity module (ADR 0003). Links between users and external sign-in identities.

-- One row per external identity, keyed by the provider's stable subject identifier (the OIDC
-- "sub" claim). The email address is never used to find the link, so a changed email at the
-- provider keeps the same account.
CREATE TABLE external_identities (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Provider names match com.swiftdrop.identity.domain.IdentityProvider.
    provider   VARCHAR(50)  NOT NULL,
    -- OIDC limits "sub" to 255 ASCII characters.
    subject    VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_external_identities_provider_subject UNIQUE (provider, subject),
    -- At most one identity per provider and user; also serves lookups by user.
    CONSTRAINT uq_external_identities_user_provider UNIQUE (user_id, provider),
    CONSTRAINT ck_external_identities_provider CHECK (provider IN ('GOOGLE'))
);
