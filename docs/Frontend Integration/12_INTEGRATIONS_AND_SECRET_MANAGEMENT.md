# 12 — Integrations & Secret Management

## 12.1 Provider SPI
Integrations are configured through the `IntegrationProvider` SPI. In Phase 4J the HTTP webhook provider is shipped; additional providers (SFTP, SOAP, etc.) are added as adapter jars by backend developers. The frontend must not assume a particular provider exists — always read `GET /api/v1/integrations/providers` first.

## 12.2 Endpoints
Base path `/api/v1/integrations`.

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/integrations/providers` | `integration:view` | Catalogue of installed providers and the fields they require. |
| GET | `/integrations` | `integration:view` | List configurations (paginated). |
| GET | `/integrations/{id}` | `integration:view` | Configuration detail (secrets are redacted/blank). |
| POST | `/integrations` | `integration:manage` | Create a configuration. |
| PUT | `/integrations/{id}` | `integration:manage` | Update configuration (secret semantics: empty value keeps existing; non-empty rotates). |
| POST | `/integrations/{id}/activate` | `integration:manage` | Activate. |
| POST | `/integrations/{id}/deactivate` | `integration:manage` | Deactivate. |
| DELETE | `/integrations/{id}` | `integration:manage` | Archive (soft). |
| POST | `/integrations/{id}/test` | `integration:test` | Dry-run against the provider; returns redacted diagnostic. |
| POST | `/integrations/{id}/executions` | `integration:execute` | Create an execution (manual trigger). Send `Idempotency-Key` to guard against double-run. |
| GET | `/integrations/{id}/executions` | `integration:view` | Execution history (paginated). |
| GET | `/integrations/executions/{execId}` | `integration:view` | Execution detail (request/response payloads if the provider makes them available; redacted secrets). |
| POST | `/integrations/executions/{execId}/retry` | `integration:execute` | Retry a failed execution (uses the same idempotency semantics). |

## 12.3 URL validation (SSRF defence)
Endpoint URLs are validated at save time by `SafeDestination.requireSafeHttps`, which blocks:
- loopback (`127.0.0.0/8`, `::1`, `localhost`),
- RFC1918 private ranges (`10/8`, `172.16/12`, `192.168/16`),
- link-local (`169.254/16`, `fe80::/10`),
- cloud-metadata addresses (IMDS, GCP/Azure equivalents),
- plaintext HTTP (only `https` is permitted unless the provider is in an explicit allow-list for on-prem connectors).

When validation fails the UI surfaces `EDAM-5005 INVALID_CONFIGURATION` with a safe message. Do not attempt to "work around" the check.

## 12.4 Secret management
- Credentials (API keys, HTTP auth headers/tokens, SFTP passwords) are stored encrypted (AES-256-GCM) using a key from `EDAM_INTEGRATION_MASTER_KEY`.
- On GET, secret fields come back as blank/masked placeholders — never as ciphertext or plaintext. The UI must render password inputs blank; submitting an empty field preserves the existing secret, submitting a non-empty field rotates it.
- Failure to provide a master key makes the save endpoint fail-closed (503 / INTERNAL_ERROR at startup).

## 12.5 Execution semantics
- Executions are processed by a background worker using `SELECT ... FOR UPDATE SKIP LOCKED` with per-row `REQUIRES_NEW` transactions; exactly one worker claims a given execution.
- Timeouts, provider errors and malformed responses are recorded on the execution as a redacted diagnostic (secrets are stripped).
- The frontend can poll executions (e.g. every 2 s for 30 s) to show progress; do not long-poll indefinitely.
- States include `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `TIMED_OUT`, `DEAD` (after retries exhausted). Retry creates a new attempt under the same execution record.

## 12.6 Idempotency
Clients sending `POST /integrations/{id}/executions` and `/retry` should generate a UUID `Idempotency-Key`. A duplicate body with the same key returns the existing execution; a different body returns `EDAM-1004 IDEMPOTENCY_CONFLICT` (HTTP 400).

## 12.7 Limitations / operational reality
- Outbound HTTP is implemented; SFTP and SOAP adapters are placeholders pending backend work. The providers endpoint reveals what's available.
- Delivery guarantees are **at-least-once from EDAM's perspective** (the outbox/claim pattern); the external system's own semantics determine whether an effect is truly exactly-once.
- There is no WebSocket/streaming for live execution status; use polling.
