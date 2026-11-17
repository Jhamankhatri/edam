# 11 — SMTP, Email Templates & Notifications

## 11.1 SMTP configuration
Base path `/api/v1/communication/smtp`; permission `email-configuration:manage`.

| Method | Path | Purpose |
|---|---|---|
| GET | `/communication/smtp` | Read current configuration. **Password is never returned** (field is blank/masked placeholder; treat it as empty). |
| POST | `/communication/smtp` | Create or update configuration. Password is encrypted at rest with AES-256-GCM (key from `EDAM_MAIL_MASTER_KEY`). |
| DELETE | `/communication/smtp/active` | Deactivate (does not delete ciphertext history). |
| POST | `/communication/smtp/test` | Send a test email to the supplied address; returns 200 with redacted diagnostic on failure (never the password). |

**SET/KEEP/CLEAR semantics** (verify against UI — confirmed by service behaviour):
- If the password field is empty on update, the previously stored password is kept (do not overwrite with empty).
- If the password field is non-empty, the stored ciphertext is rotated.
- There is no separate "clear password" endpoint beyond deactivate; to rotate credentials send the new password in the POST body.

## 11.2 Email templates
Base path `/api/v1/communication/email-templates`; permission `notification-template:manage` (for both read and write).

| Method | Path | Purpose |
|---|---|---|
| GET | `/communication/email-templates` | List templates (summary, active version). |
| GET | `/communication/email-templates/{id}` | Template detail with current active version body. |
| POST | `/communication/email-templates` | Create template + first version. |
| POST | `/communication/email-templates/{id}/versions` | Create a new version (immutable previous versions). |
| GET | `/communication/email-templates/{id}/versions` | Version history. |
| PUT | `/communication/email-templates/{id}/active` | Set active version. |
| POST | `/communication/email-templates/{id}/preview` | Render with sample variables (returns HTML/text body). |

Templates support placeholders such as `{{requestNumber}}`, `{{approverName}}`, `{{requesterName}}`, `{{reason}}`, `{{link}}` (exact list is returned in the template metadata; do not hard-code). The preview endpoint supplies default sample values.

## 11.3 Notification event triggers
Base path `/api/v1/communication/events`; permission `notification-template:manage`.

| Method | Path | Purpose |
|---|---|---|
| GET | `/communication/events` | List events with their in-app/email toggle state and the assigned template. |
| PUT | `/communication/events/{eventCode}` | Update an event's toggle + selected template. |

Events include (non-exhaustive — source of truth is the GET response): `REQUEST_SUBMITTED`, `APPROVAL_REQUESTED`, `APPROVAL_COMPLETED`, `REQUEST_REJECTED`, `REQUEST_RETURNED`, `REQUEST_CANCELLED`, `PASSWORD_RESET`, `BOOTSTRAP_COMPLETED`.

## 11.4 Notification inbox (in-app)
Base path `/api/v1/notifications`; permission `notification:view`.

| Method | Path | Purpose |
|---|---|---|
| GET | `/notifications` | Paginated list of notifications for the caller. Query: `page`, `size`, `unreadOnly`. Each entry: `id, eventCode, title, body, read, createdAt, link?` (deep link to request/entity). |
| GET | `/notifications/unread-count` | Badge count `{ count: number }` for the top bar; poll every 30 s (no SSE/WebSocket in Phase 4J). |
| POST | `/notifications/{id}/read` | Mark one notification as read. |
| POST | `/notifications/read-all` | Mark all as read. |

## 11.5 Outbox
- On every business event that triggers a notification, a row is written to `comm_notification_outbox` in the same transaction (at-least-once delivery).
- When SMTP is unavailable, outbox rows fail with a redacted diagnostic and are retried; no row contains the plaintext SMTP password.
- The frontend does not read outbox rows directly; they are an operational concern visible in admin diagnostics if exposed.

## 11.6 Production dependency
SMTP server must be configured for any outbound email; without it, in-app notifications still work (they are written directly to `comm_notifications`), but email delivery fails and users will not receive reset/approval emails.
