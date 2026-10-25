-- Owner: identity module (ADR 0003). Users, roles and user-role links.

CREATE TABLE roles (
    name VARCHAR(50) PRIMARY KEY
);

-- The four roles of SYS-2. Role names match com.swiftdrop.identity.domain.Role.
INSERT INTO roles (name) VALUES
    ('CUSTOMER'),
    ('MERCHANT'),
    ('COURIER'),
    ('DISPATCHER_ADMIN');

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    -- Stored trimmed and in lower case so the unique constraint is case-insensitive.
    email         VARCHAR(320) NOT NULL,
    -- Nullable: users who sign in only with Google (CUS-1) have no local password.
    password_hash VARCHAR(255),
    full_name     VARCHAR(200) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_normalized CHECK (email = LOWER(email))
);

CREATE TABLE user_roles (
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_name VARCHAR(50) NOT NULL REFERENCES roles (name),
    PRIMARY KEY (user_id, role_name)
);

-- Finds the users with a role, for example the couriers.
CREATE INDEX ix_user_roles_role_name ON user_roles (role_name);
