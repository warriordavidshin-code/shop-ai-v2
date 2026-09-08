-- V1: member & auth
CREATE TABLE member (
    member_id       BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    birth_date      DATE NOT NULL,
    gender          VARCHAR(32) NOT NULL,
    phone           VARCHAR(32) NOT NULL,
    postcode        VARCHAR(16) NOT NULL,
    address1        VARCHAR(255) NOT NULL,
    address2        VARCHAR(255),
    role            VARCHAR(32) NOT NULL DEFAULT 'CUSTOMER',
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_member_email UNIQUE (email),
    CONSTRAINT ck_member_gender CHECK (gender IN ('FEMALE', 'MALE', 'OTHER', 'PREFER_NOT_TO_SAY')),
    CONSTRAINT ck_member_role CHECK (role IN ('CUSTOMER', 'ADMIN')),
    CONSTRAINT ck_member_status CHECK (status IN ('ACTIVE', 'DORMANT', 'BLOCKED', 'WITHDRAWN'))
);

CREATE TABLE refresh_token (
    refresh_token_id BIGSERIAL PRIMARY KEY,
    member_id        BIGINT NOT NULL REFERENCES member (member_id),
    token_hash       VARCHAR(255) NOT NULL,
    expires_at       TIMESTAMPTZ NOT NULL,
    revoked_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_token_member ON refresh_token (member_id);

CREATE TABLE audit_log (
    audit_id      BIGSERIAL PRIMARY KEY,
    actor_member_id BIGINT REFERENCES member (member_id),
    action        VARCHAR(100) NOT NULL,
    target_type   VARCHAR(100),
    target_id     VARCHAR(100),
    detail        TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
