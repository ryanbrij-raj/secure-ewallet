# Security Policy

## Project Status

This is a **portfolio / demonstration project** showcasing secure backend
engineering patterns (encryption at rest, JWT auth, idempotent transactions,
audit logging, concurrency-safe ledgers). It is **not** running in production
and has not been independently security-audited or penetration-tested.

If you adapt this code for a real financial system, at minimum you should:

- Replace the demo key-derivation in `EncryptionUtil` with a proper KDF (HKDF)
  and a managed secret store (AWS Secrets Manager, HashiCorp Vault, GCP Secret Manager).
- Add key rotation support (versioned encryption keys, JWT `kid` claim).
- Put this service behind a WAF / API gateway with rate limiting.
- Add request signing or mTLS for service-to-service calls.
- Run a real threat model and pen test before handling real funds or PII.
- Add structured logging redaction so PII never lands in plaintext logs.

## Reporting a Vulnerability

This repository has no live deployment, so there's no production attack
surface to report against. If you spot a flaw in the *design or code* (e.g.
a logic bug that would compromise security in a real deployment), please open
a GitHub issue describing it — no need for a private disclosure process since
no real users or data are exposed by this codebase.

## Secrets

No real secrets are committed to this repository. All credential-like values
in `application.yml` / `docker-compose.yml` are non-functional placeholders
required via environment variables — see `.env.example`. If you ever find a
real secret committed in this repo's history, please flag it immediately so
it can be rotated and purged.
