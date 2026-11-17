# 14 — Pagination, Filtering, Sorting & Common Types

## 14.1 Pagination
Every list endpoint returns a **`PageResponse<T>`** (see `src/main/java/com/edam/common/api/PageResponse.java`) wrapped inside `ApiResponse.data`:

```json
{
  "items": [ /* T … */ ],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7,
  "hasNext": true,
  "hasPrevious": false
}
```

Request parameters (all query string, optional):
| Parameter | Type | Default | Max / validation | Notes |
|---|---|---|---|---|
| `page` | integer (0-based) | `0` | Must be ≥ 0 (negative coerced to 0) | First page is 0, not 1. |
| `size` | integer | `20` | Capped at **200** (product limit in `PageQuery.MAX_SIZE`) | Larger values are clamped server-side and the response reflects the clamped size. |
| `sort` | string | unsorted | Must be an allowed property for the endpoint (validated server-side) | Syntax: `property,asc|desc`, e.g. `createdAt,desc`. Multiple sorts are supported by repeating the parameter. |

TypeScript model:
```ts
interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}
```

Invalid `page`/`size` returns 400 `EDAM-1003 INVALID_PAGINATION`.

## 14.2 Filtering / search
Filter parameters are endpoint-specific and are passed as query parameters:
- `q` / `search`: free-text search (meaning varies by endpoint — username/email/request-number/title).
- Explicit filters (`status`, `type`, `departmentId`, `branchId`, `eventCode`, `active`, …) are documented per endpoint in §02.
- Date ranges: `from`, `to` — ISO-8601 instant (`2026-11-17T00:00:00Z`).
- Booleans: `true` / `false` strings.

## 14.3 Common envelopes
See §03 for `ApiResponse<T>`, `ErrorBody`, `FieldErrorDetail` and HTTP status mapping.

## 14.4 Common data types

| JSON / TS type | Java type | Format & rules |
|---|---|---|
| `string` (uuid) | `java.util.UUID` | Canonical UUID v7 format `xxxxxxxx-xxxx-7xxx-8xxx-xxxxxxxxxxxx`; treat as opaque identifier — never parse client-side. |
| `string` (date-time) | `java.time.Instant` | ISO-8601 UTC with `Z` suffix, e.g. `2026-11-17T14:05:47.123Z`. Always display in the user's timezone; never drop the timezone. |
| `string` (decimal) | `java.math.BigDecimal` | Serialized as a JSON number? **NO** — Jackson writes BigDecimal as a number but due to JS precision risks, treat monetary amounts as strings on the client; EDAM returns them with 2+ decimal places and ISO-4217 currency is always available alongside (either as a field or from the request currency). Format with `Intl.NumberFormat(locale, { style: 'currency', currency })`. |
| `string` (ISO-4217) | `Currency` | Three-letter uppercase code (`PKR`, `USD`, `EUR`, …). |
| `boolean` | `boolean` | true/false; never `1`/`0`. |
| `number` (int) | `Integer`, `Long` | Whole number; no fractional part. |
| arrays | `List<T>` | Never `null` — always at least `[]`. |
| enums | Java enums | Serialized as UPPER_SNAKE_CASE strings; do not assume casing — always use the values returned by the API (e.g. statuses). |

## 14.5 Nullable vs omitted
- `@JsonInclude(NON_NULL)` is applied at the `ApiResponse` level; fields that are `null` are omitted from JSON. Treat missing keys as `undefined` in TypeScript, not as `null`.
- A field that is declared nullable in the DTO can be `null` explicitly (Jackson only suppresses null at the ApiResponse envelope; individual DTOs do not have global non-null inclusion). Always check for both `undefined` and `null`.

## 14.6 Correlation ID
Returned on every response as `correlationId`. Surface it in technical details on errors; never omit it from bug reports.

## 14.7 Idempotency
Supported on create-style mutations and approval actions (annotated with `@IdempotentOperation`). Client sends header:
```
Idempotency-Key: <client-generated-uuid-v4>
```
Behaviour:
- Same key + same body within the TTL → the original response is replayed (no duplicate row).
- Same key + different body → 400 `EDAM-1004 IDEMPOTENCY_CONFLICT`.
- Same key with in-flight request → 409 `EDAM-1005 IDEMPOTENCY_IN_PROGRESS`.
- Prior request crashed before the record was finalized → 400 `EDAM-1006 IDEMPOTENCY_UNKNOWN_OUTCOME` (client must query the resource to determine outcome).

Always reuse the same key when the user retries a failed network call (e.g. after a 502 or timeout).

## 14.8 Optimistic locking
Certain entities (request, workflow task, workflow version, integration config, outbox, notification) carry a `version` field implicitly via JPA `@Version`. If two users edit concurrently, the second save returns 409 `EDAM-3002 CONCURRENT_MODIFICATION`. The frontend should reload and offer to merge/discard changes. Do not invent your own version field — send the server-returned object as-is on update to allow Spring's optimistic-lock check to run.

## 14.9 Header conventions
- `Content-Type: application/json` on JSON requests.
- `Accept: application/json` on all requests.
- `X-XSRF-TOKEN: <value>` on all POST/PUT/PATCH/DELETE (see §04).
- `X-EDAM-Bootstrap-Token: <token>` on `/auth/bootstrap`.
- `Idempotency-Key: <uuid>` where documented above.
- `If-Match: <etag>` is not used in Phase 4J; optimistic lock uses the `@Version` column within the JSON body.
