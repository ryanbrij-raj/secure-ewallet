-- ============================================================
--  V1__initial_schema.sql
--  Flyway migration — creates the full database schema.
--  All tables use UUID primary keys and include created_at /
--  updated_at audit columns managed by triggers.
-- ============================================================

-- ── Extensions ──────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Audit trigger function ───────────────────────────────────
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ── users ────────────────────────────────────────────────────
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email           TEXT NOT NULL UNIQUE,
    -- email is stored encrypted at app layer; this column holds ciphertext
    email_hash      TEXT NOT NULL UNIQUE,   -- HMAC for lookups without decrypting
    password_hash   TEXT NOT NULL,
    full_name       TEXT NOT NULL,          -- stored encrypted at app layer
    phone           TEXT,                   -- stored encrypted at app layer
    role            TEXT NOT NULL DEFAULT 'ROLE_USER',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    is_locked       BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_role CHECK (role IN ('ROLE_USER','ROLE_ADMIN'))
);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── refresh_tokens ───────────────────────────────────────────
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- ── currencies ───────────────────────────────────────────────
CREATE TABLE currencies (
    code        CHAR(3) PRIMARY KEY,
    name        TEXT NOT NULL,
    symbol      TEXT NOT NULL,
    decimal_places SMALLINT NOT NULL DEFAULT 2,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO currencies (code, name, symbol, decimal_places) VALUES
    ('USD','US Dollar','$',2),
    ('EUR','Euro','€',2),
    ('GBP','British Pound','£',2),
    ('JPY','Japanese Yen','¥',0),
    ('CAD','Canadian Dollar','CA$',2),
    ('AUD','Australian Dollar','A$',2),
    ('CHF','Swiss Franc','CHF',2),
    ('SGD','Singapore Dollar','S$',2);

-- ── wallets ──────────────────────────────────────────────────
CREATE TABLE wallets (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    currency_code   CHAR(3) NOT NULL REFERENCES currencies(code),
    balance         NUMERIC(19,4) NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    version         BIGINT NOT NULL DEFAULT 0,  -- optimistic locking
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT uq_wallet_user_currency UNIQUE (user_id, currency_code)
);

CREATE TRIGGER trg_wallets_updated_at
    BEFORE UPDATE ON wallets
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_wallets_user_id ON wallets(user_id);

-- ── transactions (immutable ledger) ─────────────────────────
CREATE TABLE transactions (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    idempotency_key     TEXT NOT NULL UNIQUE,   -- client-supplied, prevents double-spend
    type                TEXT NOT NULL,
    status              TEXT NOT NULL DEFAULT 'PENDING',
    source_wallet_id    UUID REFERENCES wallets(id),
    target_wallet_id    UUID REFERENCES wallets(id),
    amount              NUMERIC(19,4) NOT NULL,
    currency_code       CHAR(3) NOT NULL REFERENCES currencies(code),
    -- for cross-currency: amounts in respective currencies
    source_amount       NUMERIC(19,4),
    source_currency     CHAR(3) REFERENCES currencies(code),
    exchange_rate       NUMERIC(19,8),
    fee_amount          NUMERIC(19,4) NOT NULL DEFAULT 0,
    description         TEXT,
    metadata            JSONB,
    initiated_by        UUID NOT NULL REFERENCES users(id),
    failure_reason      TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,
    CONSTRAINT chk_amount_positive     CHECK (amount > 0),
    CONSTRAINT chk_fee_non_negative    CHECK (fee_amount >= 0),
    CONSTRAINT chk_tx_type CHECK (type IN
        ('DEPOSIT','WITHDRAWAL','TRANSFER','FX_CONVERSION','REVERSAL','FEE')),
    CONSTRAINT chk_tx_status CHECK (status IN
        ('PENDING','COMPLETED','FAILED','REVERSED'))
);

-- Transactions are append-only — no UPDATE trigger needed; updates go
-- through a status-machine check in the service layer.
CREATE INDEX idx_tx_source_wallet  ON transactions(source_wallet_id);
CREATE INDEX idx_tx_target_wallet  ON transactions(target_wallet_id);
CREATE INDEX idx_tx_initiated_by   ON transactions(initiated_by);
CREATE INDEX idx_tx_idempotency    ON transactions(idempotency_key);
CREATE INDEX idx_tx_created_at     ON transactions(created_at DESC);

-- ── audit_log ────────────────────────────────────────────────
-- Append-only record of every sensitive action (login, password change, etc.)
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID REFERENCES users(id),
    action      TEXT NOT NULL,
    entity_type TEXT,
    entity_id   TEXT,
    ip_address  TEXT,
    user_agent  TEXT,
    details     JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_user_id   ON audit_log(user_id);
CREATE INDEX idx_audit_action    ON audit_log(action);
CREATE INDEX idx_audit_created   ON audit_log(created_at DESC);

-- Revoke UPDATE/DELETE on audit_log from the app user to enforce immutability.
-- Run as a superuser in production:
-- REVOKE UPDATE, DELETE ON audit_log FROM ewallet_user;
