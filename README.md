# 🔐 Secure E-Wallet & Transaction Ledger

A production-grade financial backend demonstrating **data security**, **compliance-ready audit trails**, and **concurrent transaction safety**.

---

## Architecture Overview

```
┌──────────────┐   JWT/Bearer    ┌─────────────────────────────────────────┐
│   Client     │ ─────────────▶ │         Spring Boot Application         │
└──────────────┘                 │                                         │
                                 │  ┌──────────┐  ┌────────┐  ┌────────┐ │
                                 │  │   Auth   │  │Wallets │  │  Txns  │ │
                                 │  │Controller│  │Ctrl    │  │Ctrl    │ │
                                 │  └────┬─────┘  └───┬────┘  └───┬────┘ │
                                 │       │             │            │      │
                                 │  ┌────▼─────────────▼────────────▼───┐ │
                                 │  │           Service Layer            │ │
                                 │  │  AES-256 ▪ BCrypt ▪ Idempotency  │ │
                                 │  │  Daily limits ▪ Pessimistic locks │ │
                                 │  └────────────────────┬──────────────┘ │
                                 └───────────────────────┼────────────────┘
                                                         │
                                              ┌──────────▼──────────┐
                                              │     PostgreSQL       │
                                              │  (Flyway migrations) │
                                              │  users / wallets     │
                                              │  transactions        │
                                              │  audit_log           │
                                              └─────────────────────┘
```

---

## Key Security Features

| Feature | Implementation |
|---|---|
| **Password hashing** | BCrypt, cost factor 12 (~300 ms/hash) |
| **PII encryption at rest** | AES-256-GCM; each field uses a fresh random 96-bit IV |
| **Index-safe lookups** | HMAC-SHA-256 "blind index" — find by email without decrypting |
| **JWT access tokens** | HS256, 15-minute TTL, no server state |
| **Refresh tokens** | SHA-256 hash stored; raw token only ever sent to client |
| **Account lockout** | 5 failed attempts → auto-lock; admin unlock required |
| **Security headers** | HSTS, X-Frame-Options: DENY, no content-type sniffing |
| **Audit log** | Append-only table; DB-level REVOKE of UPDATE/DELETE |

---

## Transaction Safety

| Concern | Solution |
|---|---|
| **Double-spend** | Client-supplied `idempotencyKey` with UNIQUE constraint |
| **Concurrent transfers** | Pessimistic `SELECT FOR UPDATE`; locks in canonical UUID order to prevent deadlock |
| **Lost updates** | `@Version` optimistic locking on `Wallet` entity |
| **Phantom reads** | `ISOLATION = REPEATABLE_READ` on transfer transactions |
| **Daily limit TOCTOU** | Limit check runs inside the same serialised transaction as the debit |
| **Audit write isolation** | `Propagation.REQUIRES_NEW` — audit commits even if business tx rolls back |

---

## API Reference

### Auth
```
POST /api/v1/auth/register   — Register; returns access + refresh tokens
POST /api/v1/auth/login      — Authenticate
POST /api/v1/auth/refresh    — Rotate refresh token
POST /api/v1/auth/logout     — Revoke all refresh tokens (requires auth)
```

### Wallets
```
GET    /api/v1/wallets             — List my wallets
GET    /api/v1/wallets/{id}        — Get a specific wallet
POST   /api/v1/wallets?currency=USD — Open a new wallet
```

### Transactions
```
POST /api/v1/transactions/deposit   — Deposit funds (idempotent)
POST /api/v1/transactions/transfer  — Transfer to another user (idempotent)
GET  /api/v1/transactions           — Paginated ledger history
```

---

## Idempotency

Every mutating transaction endpoint requires an `idempotencyKey` in the request body:

```json
{
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
  "currencyCode": "USD",
  "amount": "100.00"
}
```

Submitting the same key twice returns the original response — **no double-credit/debit**.  
The enforcement is a `UNIQUE` constraint at the database level, not just application logic.

---

## Running Locally

### Prerequisites
- Java 21, Maven 3.9+
- Docker & Docker Compose

### 1. Configure secrets
```bash
cp .env.example .env
```
Then edit `.env` and generate real values:
```bash
# JWT signing secret (256-bit)
openssl rand -base64 48

# AES-256 encryption key (32 bytes, hex)
openssl rand -hex 32
```
`.env` is git-ignored — never commit it.

### 2. Start the database
```bash
docker compose up -d postgres
```

### 3. Run the application
Export the same variables from `.env` into your shell (or use a tool like `direnv`), then:
```bash
./mvnw spring-boot:run
```

### 4. Run tests
```bash
./mvnw test
```

### Full stack in Docker
```bash
docker compose up -d
```

Application starts at `http://localhost:8080`.

---

## Database Schema

Flyway manages all schema changes. Migrations live in `src/main/resources/db/migration/`.

```
users            — Encrypted PII, BCrypt passwords, lockout tracking
wallets          — Multi-currency balances with optimistic locking
transactions     — Immutable ledger; append-only; idempotency key indexed
audit_log        — Append-only security event trail; DB UPDATE/DELETE revoked
refresh_tokens   — Hashed tokens with expiry and revocation flag
currencies       — ISO 4217 reference data (seeded in V1)
```

---

## Environment Variables

All variables are **required** — there are no insecure built-in defaults.
Copy `.env.example` to `.env` and fill in real values before running.

| Variable | Description |
|---|---|
| `DB_HOST` | PostgreSQL host (default `localhost` when unset) |
| `DB_PORT` | PostgreSQL port (default `5432` when unset) |
| `DB_NAME` | Database name (default `ewallet_db` when unset) |
| `DB_USERNAME` | DB user (default `ewallet_user` when unset) |
| `DB_PASSWORD` | **Required, no default** — strong password |
| `JWT_SECRET` | **Required, no default** — ≥32-byte random string. Generate with `openssl rand -base64 48` |
| `ENCRYPTION_KEY` | **Required, no default** — 32-byte hex string. Generate with `openssl rand -hex 32` |

---

## Resume Talking Points

- **AES-256-GCM** with per-field random IVs; HMAC blind indexes for searchable encrypted fields
- **Idempotent APIs** — UNIQUE constraint + application-layer deduplication; safe for client retries
- **Deadlock-safe pessimistic locking** — wallets acquired in canonical UUID order
- **Append-only audit ledger** — DB-level `REVOKE UPDATE, DELETE` on `audit_log`
- **BCrypt cost 12** — resistant to GPU-accelerated brute-force on stolen password hashes
- **Flyway migrations** — schema versioned alongside code; `validate-on-migrate` catches drift
- **RFC 7807 Problem Details** — consistent, machine-readable error responses
