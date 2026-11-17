# 03 — Success, Error & Validation Contracts

Every response from EDAM is JSON and follows a single envelope. Frontend code should treat the envelope as the contract; do not parse Spring's default error pages, HTML or stack traces.

## 3.1 Success envelope: `ApiResponse<T>`

| Field | Type | Always present | Description |
|---|---|---|---|
| `success` | boolean | Yes | `true` for successful responses, `false` for errors. |
| `data` | T (generic) | On success | Payload (resource, `PageResponse<T>`, or `null` for acknowledgements). |
| `error` | `ErrorBody` | On failure | `{ code, message }` — machine-readable error code and a safe, end-user-ready message. |
| `fieldErrors` | `FieldErrorDetail[]` | On validation failure | List of `{ field, message }` entries. Rejected values are deliberately **not** echoed back (prevents attacker-controlled data from round-tripping into error pages). |
| `correlationId` | string (uuid) | Yes | Per-request id the frontend must show (or copy to clipboard) on error screens. Support uses it to locate the server log. |
| `path` | string | Yes | The request path that produced the response. Useful for debugging proxies/rewrites. |
| `timestamp` | ISO-8601 instant (UTC) | Yes | Server-generated. |

Declaration: `src/main/java/com/edam/common/api/ApiResponse.java` (record with `@JsonInclude(NON_NULL)` — absent fields are omitted rather than `null`).

## 3.2 HTTP statuses actually used

The frontend must not assume every 4xx is a validation error. The stable mapping below is enforced by `GlobalExceptionHandler` (`src/main/java/com/edam/common/exception/GlobalExceptionHandler.java`):

| HTTP | When | ErrorCode |
|---|---|---|
| **200 OK** | Successful retrieval, update, action (approve/reject/submit/etc.), login/logout/password change, list, export. | (none; `success:true`) |
| **201 Created** | Successful creation of a new resource where the handler explicitly returns `ResponseEntity.created(...)` (some POSTs return 200 with the created entity for convenience — always check the per-endpoint contract in §02). | (none) |
| **204 No Content** | Successful DELETE (no response body). | (none) |
| **400 Bad Request** | Malformed JSON, missing required parameter, invalid pagination, deserialization failure, idempotency-key conflict with a different body. | `EDAM-1002 INVALID_REQUEST`, `EDAM-1003 INVALID_PAGINATION`, `EDAM-1004 IDEMPOTENCY_CONFLICT`, `EDAM-1006 IDEMPOTENCY_UNKNOWN_OUTCOME` |
| **400 Bad Request** (validation) | Bean Validation (or service-level `ValidationFailedException`) rejected fields. `fieldErrors` is populated. | `EDAM-1001 VALIDATION_FAILED` |
| **401 Unauthorized** | No session, expired session, or invalid credentials. The JSON auth entry point returns JSON (not a redirect). | `EDAM-2001 UNAUTHENTICATED`, `EDAM-2006 INVALID_CREDENTIALS`, `EDAM-2007 ACCOUNT_LOCKED`, `EDAM-2011 INSTALLATION_NOT_BOOTSTRAPPED` |
| **403 Forbidden** | Authenticated but lacks permission (`EDAM-2005 PERMISSION_DENIED` / `EDAM-2002 ACCESS_DENIED`); or CSRF token missing/stale; or installation bootstrap already complete; or system-role protected mutation; or institution access denied. | `EDAM-2002..EDAM-2005`, `EDAM-2010 INSTALLATION_ALREADY_COMPLETED`, `EDAM-3005 SYSTEM_ROLE_PROTECTED` |
| **404 Not Found** | The addressed resource does not exist OR is not visible to the caller. The two cases are intentionally indistinguishable (prevents existence probing). | `EDAM-4001 NOT_FOUND` |
| **409 Conflict** | Business rule violation, invalid state transition, concurrent modification (optimistic lock), duplicate unique key, external service failure surfaced as a conflict. | `EDAM-3001 BUSINESS_RULE_VIOLATION`, `EDAM-3002 CONCURRENT_MODIFICATION`, `EDAM-3003 PRECONDITION_FAILED`, `EDAM-3004 INVALID_STATE_TRANSITION`, `EDAM-5002 DUPLICATE_RESOURCE`, `EDAM-5003 EXTERNAL_SERVICE_FAILURE`, `EDAM-5004 INVALID_DOCUMENT`, `EDAM-5005 INVALID_CONFIGURATION` |
| **413 Payload Too Large** | Upload exceeds the servlet container's max multipart size (container default; EDAM additionally enforces its own size limits at the service layer and returns `INVALID_DOCUMENT`). | — (container) |
| **415 Unsupported Media Type** | Upload with disallowed Content-Type. | (Spring / container) |
| **422 Unprocessable Entity** | Currently **not produced** by EDAM. The frontend may encounter this only from intermediate proxies; do not code specific UI for it. | — |
| **429 Too Many Requests** | Currently **not produced** by the application. Repeated login failures return 401 `ACCOUNT_LOCKED` per account rather than a rate-limit 429. | — |
| **500 Internal Server Error** | Unexpected server error. The response contains only `INTERNAL_ERROR` and the `correlationId`; no stack trace, SQL state or class name. | `EDAM-9001 INTERNAL_ERROR` |
| **503 Service Unavailable** | Returned by the reverse proxy if EDAM is down; the application itself does not emit this. | — |

## 3.3 Examples

### 3.3.1 200 — retrieval
```http
GET /api/v1/auth/me
```
```json
{
  "success": true,
  "data": {
    "userId": "00000000-0000-7000-8000-000000000001",
    "username": "admin",
    "displayName": "System Administrator",
    "permissions": ["system:administer","user:manage"],
    "dataScopes": [{"type":"INSTALLATION","referenceId":null,"referenceName":null}],
    "mustChangePassword": false
  },
  "correlationId": "8a4f2c1e-0d3b-4a9c-9b54-1e6a8c7e0a12",
  "path": "/api/v1/auth/me",
  "timestamp": "2026-11-17T14:05:47Z"
}
```

### 3.3.2 201 — created
On endpoints where the handler returns `ResponseEntity.created(...)`, the status is 201 and the `Location` header points at the new resource. The body still follows the success envelope.

### 3.3.3 204 — deleted
```http
DELETE /api/v1/users/{id}/roles/{assignmentId}
```
```http
HTTP/1.1 204
```
(No body.)

### 3.3.4 400 — validation failure
```json
{
  "success": false,
  "error": { "code": "EDAM-1001", "message": "One or more fields are invalid." },
  "fieldErrors": [
    { "field": "username", "message": "must not be blank" },
    { "field": "password", "message": "length must be between 8 and 200" }
  ],
  "correlationId": "8a4f2c1e-0d3b-4a9c-9b54-1e6a8c7e0a12",
  "path": "/api/v1/auth/bootstrap",
  "timestamp": "2026-11-17T14:05:47Z"
}
```
**Frontend handling:** walk `fieldErrors` and show messages inline; clear errors on field change. Do not rely on message text being exactly as shown — always match the HTTP code (400 + non-null `fieldErrors`) to trigger inline display.

### 3.3.5 401 — session expired / unauthenticated
```json
{
  "success": false,
  "error": { "code": "EDAM-2001", "message": "Sign in is required." },
  "correlationId": "…",
  "path": "/api/v1/users",
  "timestamp": "…"
}
```
**Frontend handling:** redirect to `/login?reason=expired` and clear any locally cached user.

### 3.3.6 403 — forbidden / CSRF
```json
{
  "success": false,
  "error": { "code": "EDAM-2005", "message": "You do not have permission to perform this operation." },
  "correlationId": "…",
  "path": "/api/v1/users",
  "timestamp": "…"
}
```
**Frontend handling:** render a "You don't have permission" page; if the user appears to have the permission, encourage a refresh (permission set may be stale). For CSRF-induced 403s (which have a distinct reason from Spring Security that may not expose a custom error code), the safe behaviour is to re-fetch CSRF and retry once.

### 3.3.7 404 — not found (or not visible)
```json
{
  "success": false,
  "error": { "code": "EDAM-4001", "message": "The requested resource was not found." },
  "correlationId": "…",
  "path": "/api/v1/expense-requests/{id}",
  "timestamp": "…"
}
```
**Frontend handling:** show a "Not found" screen; do not distinguish "doesn't exist" from "exists but you can't see it" to match the backend.

### 3.3.8 409 — conflict (business / state / concurrency)
```json
{
  "success": false,
  "error": { "code": "EDAM-3002", "message": "The record was changed by another user. Reload and try again." },
  "correlationId": "…",
  "path": "/api/v1/expense-requests/{id}",
  "timestamp": "…"
}
```
**Frontend handling by code:**
- `EDAM-3002 CONCURRENT_MODIFICATION` → offer a "Reload" button that re-fetches the resource (optimistic lock).
- `EDAM-3004 INVALID_STATE_TRANSITION` → the action is not available in this state (e.g. approving a DRAFT). Disable the button after reload.
- `EDAM-5002 DUPLICATE_RESOURCE` → show "A record with those details already exists."
- `EDAM-3001 BUSINESS_RULE_VIOLATION` → show the server message verbatim (it is safe).

### 3.3.9 500 — unexpected error
```json
{
  "success": false,
  "error": { "code": "EDAM-9001", "message": "An unexpected error occurred." },
  "correlationId": "8a4f2c1e-0d3b-4a9c-9b54-1e6a8c7e0a12",
  "path": "/api/v1/…",
  "timestamp": "…"
}
```
**Frontend handling:** show a generic error toast and surface the `correlationId` in a collapsible "Technical details" block. Never render the raw error, stack trace or SQL state.

## 3.4 Validation sources and rules

Validation failures can come from three layers — the frontend should not attempt to replicate them all, but should reflect them when returned:

1. **Bean Validation on DTOs** (`@NotBlank`, `@Size`, `@Email`, `@Min/@Max`, `@Pattern`) → `fieldErrors` keyed by the JSON field path (e.g. `"items[0].quantity"`, `"password"`).
2. **Service-level business validation** (e.g. "Submit requires at least one line item", "Amount must be positive", "Category does not belong to this request type") → typically returns 400/409 with a top-level error message (no `fieldErrors` entry if it is a cross-field rule). The `message` is safe to display verbatim.
3. **Controller-level pagination/sort validation** → `EDAM-1003 INVALID_PAGINATION` if `size > 200` or `page < 0`.

## 3.5 Frontend error-handler rules

1. **All fetches go through a single wrapper** that:
   - Sets `Accept: application/json` and (on mutations) `Content-Type: application/json` and `X-XSRF-TOKEN`.
   - Sends credentials (`include`).
   - Parses JSON into `ApiResponse<T>`; throws a typed `ApiError` when `!response.ok || !data.success`.
2. **Do not trust `message` for switching logic.** Use `error.code` (e.g. `EDAM-3002`) for routing; `message` is for display only.
3. **Always include `correlationId`** in error UI (it is the single bridge between the user's screen and server logs).
4. **Do not log request/response bodies** in production client logging (they may contain PII).
5. **Treat any non-JSON response as an infrastructure error** (reverse-proxy 502/504 page, etc.) and show "EDAM is unavailable" with the HTTP status, not JSON parse error text.
