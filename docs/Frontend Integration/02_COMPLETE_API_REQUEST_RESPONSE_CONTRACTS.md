# 02 — Complete API Request & Response Contracts

Each subsection below covers one endpoint. Path parameters are `{name}`, query parameters are documented in a table, request bodies list every JSON field with types and constraints, and the success response lists the exact `data` payload (inside the `ApiResponse<T>` envelope).

For global envelope/error semantics see §03; for pagination see §14.


## APR-001 — GET /approvals

- **Module:** Approval Inbox & Actions
- **Purpose:** `inbox` on `/approvals`.
- **Controller / handler:** `ApprovalController#inbox` (`src/main/java/src/main/java/com/edam/workflow/controller/ApprovalController.java`)
- **Authentication:** Authenticated; permission `approval:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `approval:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APR-002 — GET /approvals/count

- **Module:** Approval Inbox & Actions
- **Purpose:** `count` on `/approvals/count`.
- **Controller / handler:** `ApprovalController#count` (`src/main/java/src/main/java/com/edam/workflow/controller/ApprovalController.java`)
- **Authentication:** Authenticated; permission `approval:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `approval:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APR-003 — GET /approvals/tasks/{taskId}

- **Module:** Approval Inbox & Actions
- **Purpose:** `caseFile` on `/approvals/tasks/{taskId}`.
- **Controller / handler:** `ApprovalController#caseFile` (`src/main/java/src/main/java/com/edam/workflow/controller/ApprovalController.java`)
- **Authentication:** Authenticated; permission `approval:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `taskId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `approval:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APR-004 — GET /approvals/tasks/{taskId}/request

- **Module:** Approval Inbox & Actions
- **Purpose:** `requestFor` on `/approvals/tasks/{taskId}/request`.
- **Controller / handler:** `ApprovalController#requestFor` (`src/main/java/src/main/java/com/edam/workflow/controller/ApprovalController.java`)
- **Authentication:** Authenticated; permission `approval:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `taskId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `approval:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APR-005 — POST /approvals/tasks/{taskId}/approve

- **Module:** Approval Inbox & Actions
- **Purpose:** Create a new `approve`.
- **Controller / handler:** `ApprovalController#?` (`src/main/java/src/main/java/com/edam/workflow/controller/ApprovalController.java`)
- **Authentication:** Authenticated; permission `approval:act`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `taskId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `approval:act`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APR-006 — POST /approvals/tasks/{taskId}/reject

- **Module:** Approval Inbox & Actions
- **Purpose:** Create a new `reject`.
- **Controller / handler:** `ApprovalController#?` (`src/main/java/src/main/java/com/edam/workflow/controller/ApprovalController.java`)
- **Authentication:** Authenticated; permission `approval:act`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `taskId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `approval:act`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APR-007 — POST /approvals/tasks/{taskId}/revert

- **Module:** Approval Inbox & Actions
- **Purpose:** Create a new `revert`.
- **Controller / handler:** `ApprovalController#?` (`src/main/java/src/main/java/com/edam/workflow/controller/ApprovalController.java`)
- **Authentication:** Authenticated; permission `approval:act`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `taskId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `approval:act`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APR-008 — POST /approvals/tasks/{taskId}/forward

- **Module:** Approval Inbox & Actions
- **Purpose:** Create a new `forward`.
- **Controller / handler:** `ApprovalController#?` (`src/main/java/src/main/java/com/edam/workflow/controller/ApprovalController.java`)
- **Authentication:** Authenticated; permission `approval:act`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `taskId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `approval:act`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APL-001 — GET /approval-limits

- **Module:** Approval Limits
- **Purpose:** `list` on `/approval-limits`.
- **Controller / handler:** `ApprovalLimitController#list` (`src/main/java/src/main/java/com/edam/approvallimits/controller/ApprovalLimitController.java`)
- **Authentication:** Authenticated; permission `approval-limit:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `approval-limit:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APL-002 — GET /approval-limits/{id}

- **Module:** Approval Limits
- **Purpose:** Fetch `{id}` by id.
- **Controller / handler:** `ApprovalLimitController#get` (`src/main/java/src/main/java/com/edam/approvallimits/controller/ApprovalLimitController.java`)
- **Authentication:** Authenticated; permission `approval-limit:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `approval-limit:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APL-003 — POST /approval-limits

- **Module:** Approval Limits
- **Purpose:** Create a new `approval-limits`.
- **Controller / handler:** `ApprovalLimitController#create` (`src/main/java/src/main/java/com/edam/approvallimits/controller/ApprovalLimitController.java`)
- **Authentication:** Authenticated; permission `approval-limit:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `departmentId` | string (uuid) | Yes |  |
| `userId` | string (uuid) | No |  |
| `expenseType` | ExpenseType | Yes |  |
| `currencyId` | string (uuid) | Yes |  |
| `effectiveFrom` | string (ISO-8601 instant, UTC) | No |  |
| `effectiveTo` | string (ISO-8601 instant, UTC) | No |  |

**Example:**

```json
{
  "departmentId": "00000000-0000-7000-8000-000000000001",
  "userId": "00000000-0000-7000-8000-000000000001",
  "expenseType": null,
  "currencyId": "00000000-0000-7000-8000-000000000001",
  "effectiveFrom": "2026-11-17T14:05:47Z",
  "effectiveTo": "2026-11-17T14:05:47Z"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `approval-limit:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APL-004 — PUT /approval-limits/{id}

- **Module:** Approval Limits
- **Purpose:** Update an existing `{id}`.
- **Controller / handler:** `ApprovalLimitController#update` (`src/main/java/src/main/java/com/edam/approvallimits/controller/ApprovalLimitController.java`)
- **Authentication:** Authenticated; permission `approval-limit:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `departmentId` | string (uuid) | Yes |  |
| `userId` | string (uuid) | No |  |
| `expenseType` | ExpenseType | Yes |  |
| `currencyId` | string (uuid) | Yes |  |
| `effectiveFrom` | string (ISO-8601 instant, UTC) | No |  |
| `effectiveTo` | string (ISO-8601 instant, UTC) | No |  |

**Example:**

```json
{
  "departmentId": "00000000-0000-7000-8000-000000000001",
  "userId": "00000000-0000-7000-8000-000000000001",
  "expenseType": null,
  "currencyId": "00000000-0000-7000-8000-000000000001",
  "effectiveFrom": "2026-11-17T14:05:47Z",
  "effectiveTo": "2026-11-17T14:05:47Z"
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `approval-limit:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APL-005 — POST /approval-limits/{id}/activation

- **Module:** Approval Limits
- **Purpose:** `setActive` on `/approval-limits/{id}/activation`.
- **Controller / handler:** `ApprovalLimitController#setActive` (`src/main/java/src/main/java/com/edam/approvallimits/controller/ApprovalLimitController.java`)
- **Authentication:** Authenticated; permission `approval-limit:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `active` | boolean | No | — | — |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `approval-limit:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APL-006 — POST /approval-limits/authority-check

- **Module:** Approval Limits
- **Purpose:** Create a new `authority-check`.
- **Controller / handler:** `ApprovalLimitController#checkAuthority` (`src/main/java/src/main/java/com/edam/approvallimits/controller/ApprovalLimitController.java`)
- **Authentication:** Authenticated; permission `approval-limit:view`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `departmentId` | string (uuid) | Yes |  |
| `expenseType` | string | Yes |  |
| `currencyId` | string (uuid) | Yes |  |
| `candidateUserIds` | string (uuid)[] | No |  |

**Example:**

```json
{
  "departmentId": "00000000-0000-7000-8000-000000000001",
  "expenseType": "string",
  "currencyId": "00000000-0000-7000-8000-000000000001",
  "candidateUserIds": []
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `approval-limit:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APL-001 — GET /exceeding-limit-rules

- **Module:** Approval Limits
- **Purpose:** `list` on `/exceeding-limit-rules`.
- **Controller / handler:** `ExceedingLimitRuleController#list` (`src/main/java/src/main/java/com/edam/approvallimits/controller/ExceedingLimitRuleController.java`)
- **Authentication:** Authenticated; permission `exceeding-limit:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `exceeding-limit:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APL-002 — GET /exceeding-limit-rules/{id}

- **Module:** Approval Limits
- **Purpose:** Fetch `{id}` by id.
- **Controller / handler:** `ExceedingLimitRuleController#get` (`src/main/java/src/main/java/com/edam/approvallimits/controller/ExceedingLimitRuleController.java`)
- **Authentication:** Authenticated; permission `exceeding-limit:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `exceeding-limit:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APL-003 — POST /exceeding-limit-rules

- **Module:** Approval Limits
- **Purpose:** Create a new `exceeding-limit-rules`.
- **Controller / handler:** `ExceedingLimitRuleController#create` (`src/main/java/src/main/java/com/edam/approvallimits/controller/ExceedingLimitRuleController.java`)
- **Authentication:** Authenticated; permission `exceeding-limit:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `expenseType` | ExpenseType | Yes |  |
| `natureId` | string (uuid) | Yes |  |
| `currencyId` | string (uuid) | Yes |  |
| `escalationType` | EscalationType | Yes |  |

**Example:**

```json
{
  "expenseType": null,
  "natureId": "00000000-0000-7000-8000-000000000001",
  "currencyId": "00000000-0000-7000-8000-000000000001",
  "escalationType": null
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `exceeding-limit:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APL-004 — PUT /exceeding-limit-rules/{id}

- **Module:** Approval Limits
- **Purpose:** Update an existing `{id}`.
- **Controller / handler:** `ExceedingLimitRuleController#update` (`src/main/java/src/main/java/com/edam/approvallimits/controller/ExceedingLimitRuleController.java`)
- **Authentication:** Authenticated; permission `exceeding-limit:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `expenseType` | ExpenseType | Yes |  |
| `natureId` | string (uuid) | Yes |  |
| `currencyId` | string (uuid) | Yes |  |
| `escalationType` | EscalationType | Yes |  |

**Example:**

```json
{
  "expenseType": null,
  "natureId": "00000000-0000-7000-8000-000000000001",
  "currencyId": "00000000-0000-7000-8000-000000000001",
  "escalationType": null
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `exceeding-limit:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APL-005 — POST /exceeding-limit-rules/{id}/activation

- **Module:** Approval Limits
- **Purpose:** `setActive` on `/exceeding-limit-rules/{id}/activation`.
- **Controller / handler:** `ExceedingLimitRuleController#setActive` (`src/main/java/src/main/java/com/edam/approvallimits/controller/ExceedingLimitRuleController.java`)
- **Authentication:** Authenticated; permission `exceeding-limit:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `active` | boolean | No | — | — |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `exceeding-limit:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APL-006 — GET /exceeding-limit-rules/evaluation

- **Module:** Approval Limits
- **Purpose:** `evaluate` on `/exceeding-limit-rules/evaluation`.
- **Controller / handler:** `ExceedingLimitRuleController#evaluate` (`src/main/java/src/main/java/com/edam/approvallimits/controller/ExceedingLimitRuleController.java`)
- **Authentication:** Authenticated; permission `exceeding-limit:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `expenseType` | string | No | — | — |
| `natureId` | string (uuid) | No | — | — |
| `currencyId` | string (uuid) | No | — | — |
| `amount` | string (decimal) | No | — | — |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `exceeding-limit:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APL-001 — GET /recommending-authorities

- **Module:** Approval Limits
- **Purpose:** `list` on `/recommending-authorities`.
- **Controller / handler:** `RecommendingAuthorityController#list` (`src/main/java/src/main/java/com/edam/approvallimits/controller/RecommendingAuthorityController.java`)
- **Authentication:** Authenticated; permission `recommending-authority:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `recommending-authority:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APL-002 — GET /recommending-authorities/{id}

- **Module:** Approval Limits
- **Purpose:** Fetch `{id}` by id.
- **Controller / handler:** `RecommendingAuthorityController#get` (`src/main/java/src/main/java/com/edam/approvallimits/controller/RecommendingAuthorityController.java`)
- **Authentication:** Authenticated; permission `recommending-authority:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `recommending-authority:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APL-003 — POST /recommending-authorities

- **Module:** Approval Limits
- **Purpose:** Create a new `recommending-authorities`.
- **Controller / handler:** `RecommendingAuthorityController#create` (`src/main/java/src/main/java/com/edam/approvallimits/controller/RecommendingAuthorityController.java`)
- **Authentication:** Authenticated; permission `recommending-authority:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `departmentId` | string (uuid) | Yes |  |
| `userId` | string (uuid) | Yes |  |
| `expenseType` | ExpenseType | Yes |  |
| `effectiveFrom` | string (ISO-8601 instant, UTC) | No |  |
| `effectiveTo` | string (ISO-8601 instant, UTC) | No |  |

**Example:**

```json
{
  "departmentId": "00000000-0000-7000-8000-000000000001",
  "userId": "00000000-0000-7000-8000-000000000001",
  "expenseType": null,
  "effectiveFrom": "2026-11-17T14:05:47Z",
  "effectiveTo": "2026-11-17T14:05:47Z"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `recommending-authority:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APL-004 — PUT /recommending-authorities/{id}

- **Module:** Approval Limits
- **Purpose:** Update an existing `{id}`.
- **Controller / handler:** `RecommendingAuthorityController#update` (`src/main/java/src/main/java/com/edam/approvallimits/controller/RecommendingAuthorityController.java`)
- **Authentication:** Authenticated; permission `recommending-authority:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `departmentId` | string (uuid) | Yes |  |
| `userId` | string (uuid) | Yes |  |
| `expenseType` | ExpenseType | Yes |  |
| `effectiveFrom` | string (ISO-8601 instant, UTC) | No |  |
| `effectiveTo` | string (ISO-8601 instant, UTC) | No |  |

**Example:**

```json
{
  "departmentId": "00000000-0000-7000-8000-000000000001",
  "userId": "00000000-0000-7000-8000-000000000001",
  "expenseType": null,
  "effectiveFrom": "2026-11-17T14:05:47Z",
  "effectiveTo": "2026-11-17T14:05:47Z"
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `recommending-authority:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APL-005 — POST /recommending-authorities/{id}/activation

- **Module:** Approval Limits
- **Purpose:** `setActive` on `/recommending-authorities/{id}/activation`.
- **Controller / handler:** `RecommendingAuthorityController#setActive` (`src/main/java/src/main/java/com/edam/approvallimits/controller/RecommendingAuthorityController.java`)
- **Authentication:** Authenticated; permission `recommending-authority:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `active` | boolean | No | — | — |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `recommending-authority:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## APL-006 — GET /recommending-authorities/resolved

- **Module:** Approval Limits
- **Purpose:** `resolved` on `/recommending-authorities/resolved`.
- **Controller / handler:** `RecommendingAuthorityController#resolved` (`src/main/java/src/main/java/com/edam/approvallimits/controller/RecommendingAuthorityController.java`)
- **Authentication:** Authenticated; permission `recommending-authority:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `departmentId` | string (uuid) | No | — | — |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `recommending-authority:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## AUD-001 — GET /audit-events

- **Module:** Audit
- **Purpose:** `search` on `/audit-events`.
- **Controller / handler:** `AuditController#search` (`src/main/java/src/main/java/com/edam/audit/controller/AuditController.java`)
- **Authentication:** Authenticated; permission `audit:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `audit:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## AUD-002 — GET /audit-events/{id}

- **Module:** Audit
- **Purpose:** Fetch `{id}` by id.
- **Controller / handler:** `AuditController#get` (`src/main/java/src/main/java/com/edam/audit/controller/AuditController.java`)
- **Authentication:** Authenticated; permission `audit:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `audit:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## AUD-001 — GET /expense-requests/{id}/activity

- **Module:** Audit
- **Purpose:** Fetch `activity` by id.
- **Controller / handler:** `RequestTimelineController#timeline` (`src/main/java/src/main/java/com/edam/audit/controller/RequestTimelineController.java`)
- **Authentication:** Authenticated; permission `request:view-own`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `request:view-own`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## AUTH-001 — POST /auth/login

- **Module:** Authentication
- **Purpose:** Establish an authenticated session (sets `JSESSIONID` + rotates `XSRF-TOKEN`).
- **Controller / handler:** `AuthController#login` (`src/main/java/src/main/java/com/edam/identity/controller/AuthController.java`)
- **Authentication:** Public
- **CSRF:** Not required
- **Content-Type:** application/json
- **Success status:** 200

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `username` | string | Yes | ≤100 chars |
| `password` | string | Yes | ≤200 chars |

**Example:**

```json
{
  "username": "j.doe",
  "password": "(redacted)"
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## AUTH-002 — POST /auth/logout

- **Module:** Authentication
- **Purpose:** Invalidate the current session.
- **Controller / handler:** `AuthController#logout` (`src/main/java/src/main/java/com/edam/identity/controller/AuthController.java`)
- **Authentication:** Public
- **CSRF:** Not required
- **Content-Type:** application/json
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## AUTH-003 — GET /auth/me

- **Module:** Authentication
- **Purpose:** Return the current principal, effective roles/permissions, data scopes and whether the password must be changed.
- **Controller / handler:** `AuthController#currentUser` (`src/main/java/src/main/java/com/edam/identity/controller/AuthController.java`)
- **Authentication:** Authenticated (no business permission)
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## AUTH-004 — GET /auth/csrf

- **Module:** Authentication
- **Purpose:** Issue a CSRF token + establish a session (idempotent).
- **Controller / handler:** `AuthController#csrf` (`src/main/java/src/main/java/com/edam/identity/controller/AuthController.java`)
- **Authentication:** Public
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## AUTH-005 — POST /auth/change-password

- **Module:** Authentication
- **Purpose:** Change the caller's password.
- **Controller / handler:** `AuthController#changePassword` (`src/main/java/src/main/java/com/edam/identity/controller/AuthController.java`)
- **Authentication:** Authenticated (no business permission)
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `currentPassword` | string | Yes | ≤200 chars |
| `newPassword` | string | Yes | ≤200 chars |

**Example:**

```json
{
  "currentPassword": "string",
  "newPassword": "string"
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## AUTH-006 — POST /auth/forgot-password

- **Module:** Authentication
- **Purpose:** Request a password reset (response does not reveal account existence).
- **Controller / handler:** `AuthController#forgotPassword` (`src/main/java/src/main/java/com/edam/identity/controller/AuthController.java`)
- **Authentication:** Public
- **CSRF:** Not required
- **Content-Type:** application/json
- **Success status:** 200

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | string | Yes | email; ≤255 chars |

**Example:**

```json
{
  "email": "user@example.com"
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## AUTH-007 — POST /auth/reset-password

- **Module:** Authentication
- **Purpose:** Redeem a password-reset token for a new password.
- **Controller / handler:** `AuthController#resetPassword` (`src/main/java/src/main/java/com/edam/identity/controller/AuthController.java`)
- **Authentication:** Public
- **CSRF:** Not required
- **Content-Type:** application/json
- **Success status:** 200

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `token` | string | Yes | ≤400 chars |
| `newPassword` | string | Yes | ≤200 chars |

**Example:**

```json
{
  "token": "3b3f2d…opaque-reset-token",
  "newPassword": "string"
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## AUTH-008 — GET /auth/installation

- **Module:** Authentication
- **Purpose:** Return whether bootstrap has been completed (one bit).
- **Controller / handler:** `AuthController#installationStatus` (`src/main/java/src/main/java/com/edam/identity/controller/AuthController.java`)
- **Authentication:** Public
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## AUTH-009 — POST /auth/bootstrap

- **Module:** Authentication
- **Purpose:** First-administrator bootstrap (one-time; requires installation token header).
- **Controller / handler:** `AuthController#bootstrap` (`src/main/java/src/main/java/com/edam/identity/controller/AuthController.java`)
- **Authentication:** Public
- **CSRF:** Not required
- **Content-Type:** application/json
- **Success status:** 201

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BRD-001 — GET /public/configuration

- **Module:** Branding / Configuration
- **Purpose:** Public subset of branding/configuration for the login screen and app shell.
- **Controller / handler:** `ConfigurationController#publicConfiguration` (`src/main/java/src/main/java/com/edam/configuration/controller/ConfigurationController.java`)
- **Authentication:** Public
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BRD-002 — GET /themes

- **Module:** Branding / Configuration
- **Purpose:** `listThemes` on `/themes`.
- **Controller / handler:** `ConfigurationController#listThemes` (`src/main/java/src/main/java/com/edam/configuration/controller/ConfigurationController.java`)
- **Authentication:** Authenticated; permission `configuration:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `configuration:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BRD-003 — GET /themes/active

- **Module:** Branding / Configuration
- **Purpose:** `activeTheme` on `/themes/active`.
- **Controller / handler:** `ConfigurationController#activeTheme` (`src/main/java/src/main/java/com/edam/configuration/controller/ConfigurationController.java`)
- **Authentication:** Authenticated; permission `configuration:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `configuration:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BRD-004 — GET /themes/{id}

- **Module:** Branding / Configuration
- **Purpose:** Fetch `{id}` by id.
- **Controller / handler:** `ConfigurationController#getTheme` (`src/main/java/src/main/java/com/edam/configuration/controller/ConfigurationController.java`)
- **Authentication:** Authenticated; permission `configuration:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `configuration:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BRD-005 — POST /themes

- **Module:** Branding / Configuration
- **Purpose:** Create a new `themes`.
- **Controller / handler:** `ConfigurationController#createTheme` (`src/main/java/src/main/java/com/edam/configuration/controller/ConfigurationController.java`)
- **Authentication:** Authenticated; permission `configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | Yes | ≤100 chars |
| `description` | string | No | ≤1000 chars |
| `themeType` | ThemeType | Yes |  |
| `tokens` | ThemeTokenRequest[] | No |  |

**Example:**

```json
{
  "name": "string",
  "description": "string",
  "themeType": null,
  "tokens": []
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BRD-006 — PUT /themes/{id}

- **Module:** Branding / Configuration
- **Purpose:** Update an existing `{id}`.
- **Controller / handler:** `ConfigurationController#updateTheme` (`src/main/java/src/main/java/com/edam/configuration/controller/ConfigurationController.java`)
- **Authentication:** Authenticated; permission `configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | Yes | ≤100 chars |
| `description` | string | No | ≤1000 chars |
| `themeType` | ThemeType | Yes |  |
| `tokens` | ThemeTokenRequest[] | No |  |

**Example:**

```json
{
  "name": "string",
  "description": "string",
  "themeType": null,
  "tokens": []
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BRD-007 — POST /themes/{id}/activation

- **Module:** Branding / Configuration
- **Purpose:** `setThemeActive` on `/themes/{id}/activation`.
- **Controller / handler:** `ConfigurationController#setThemeActive` (`src/main/java/src/main/java/com/edam/configuration/controller/ConfigurationController.java`)
- **Authentication:** Authenticated; permission `configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `active` | boolean | No | — | — |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BRD-008 — POST /themes/{id}/default

- **Module:** Branding / Configuration
- **Purpose:** `setDefaultTheme` on `/themes/{id}/default`.
- **Controller / handler:** `ConfigurationController#setDefaultTheme` (`src/main/java/src/main/java/com/edam/configuration/controller/ConfigurationController.java`)
- **Authentication:** Authenticated; permission `configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BRD-009 — GET /settings

- **Module:** Branding / Configuration
- **Purpose:** `listSettings` on `/settings`.
- **Controller / handler:** `ConfigurationController#listSettings` (`src/main/java/src/main/java/com/edam/configuration/controller/ConfigurationController.java`)
- **Authentication:** Authenticated; permission `configuration:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `configuration:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BRD-010 — GET /settings/categories/{category}

- **Module:** Branding / Configuration
- **Purpose:** `listByCategory` on `/settings/categories/{category}`.
- **Controller / handler:** `ConfigurationController#listByCategory` (`src/main/java/src/main/java/com/edam/configuration/controller/ConfigurationController.java`)
- **Authentication:** Authenticated; permission `configuration:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `category` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `configuration:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BRD-011 — GET /settings/{key}

- **Module:** Branding / Configuration
- **Purpose:** `getSetting` on `/settings/{key}`.
- **Controller / handler:** `ConfigurationController#getSetting` (`src/main/java/src/main/java/com/edam/configuration/controller/ConfigurationController.java`)
- **Authentication:** Authenticated; permission `configuration:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `key` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `configuration:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BRD-012 — POST /settings

- **Module:** Branding / Configuration
- **Purpose:** Create a new `settings`.
- **Controller / handler:** `ConfigurationController#saveSetting` (`src/main/java/src/main/java/com/edam/configuration/controller/ConfigurationController.java`)
- **Authentication:** Authenticated; permission `configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `displayName` | string | No | ≤200 chars |
| `description` | string | No | ≤2000 chars |
| `category` | string | Yes | ≤80 chars |
| `type` | SettingType | Yes |  |
| `value` | object | No |  |
| `defaultValue` | object | No |  |
| `active` | boolean | No |  |
| `editable` | boolean | No |  |

**Example:**

```json
{
  "displayName": "string",
  "description": "string",
  "category": "string",
  "type": null,
  "value": {},
  "defaultValue": {},
  "active": true,
  "editable": true
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BRD-013 — PATCH /settings/bulk

- **Module:** Branding / Configuration
- **Purpose:** Patch `bulk`.
- **Controller / handler:** `ConfigurationController#bulkUpdateSettings` (`src/main/java/src/main/java/com/edam/configuration/controller/ConfigurationController.java`)
- **Authentication:** Authenticated; permission `configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `values` | Map<String, Object> | Yes |  |

**Example:**

```json
{
  "values": {}
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BRD-014 — GET /feature-flags

- **Module:** Branding / Configuration
- **Purpose:** `listFlags` on `/feature-flags`.
- **Controller / handler:** `ConfigurationController#listFlags` (`src/main/java/src/main/java/com/edam/configuration/controller/ConfigurationController.java`)
- **Authentication:** Authenticated; permission `configuration:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `configuration:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BRD-015 — GET /feature-flags/{key}

- **Module:** Branding / Configuration
- **Purpose:** `getFlag` on `/feature-flags/{key}`.
- **Controller / handler:** `ConfigurationController#getFlag` (`src/main/java/src/main/java/com/edam/configuration/controller/ConfigurationController.java`)
- **Authentication:** Authenticated; permission `configuration:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `key` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `configuration:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BRD-016 — POST /feature-flags

- **Module:** Branding / Configuration
- **Purpose:** Create a new `feature-flags`.
- **Controller / handler:** `ConfigurationController#saveFlag` (`src/main/java/src/main/java/com/edam/configuration/controller/ConfigurationController.java`)
- **Authentication:** Authenticated; permission `configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `displayName` | string | Yes | ≤150 chars |
| `description` | string | No | ≤2000 chars |
| `enabled` | boolean | No |  |
| `configuration` | object | No |  |

**Example:**

```json
{
  "displayName": "string",
  "description": "string",
  "enabled": true,
  "configuration": {}
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BRD-017 — POST /feature-flags/{key}/activation

- **Module:** Branding / Configuration
- **Purpose:** Create a new `activation`.
- **Controller / handler:** `ConfigurationController#setFlagEnabled` (`src/main/java/src/main/java/com/edam/configuration/controller/ConfigurationController.java`)
- **Authentication:** Authenticated; permission `configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `key` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `enabled` | boolean | No | — | — |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BR-001 — GET /business-rules

- **Module:** Business Rules
- **Purpose:** `list` on `/business-rules`.
- **Controller / handler:** `BusinessRuleController#list` (`src/main/java/src/main/java/com/edam/businessrules/controller/BusinessRuleController.java`)
- **Authentication:** Authenticated; permission `rule:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `rule:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BR-002 — GET /business-rules/{id}

- **Module:** Business Rules
- **Purpose:** Fetch `{id}` by id.
- **Controller / handler:** `BusinessRuleController#get` (`src/main/java/src/main/java/com/edam/businessrules/controller/BusinessRuleController.java`)
- **Authentication:** Authenticated; permission `rule:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `rule:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BR-003 — POST /business-rules

- **Module:** Business Rules
- **Purpose:** Create a new `business-rules`.
- **Controller / handler:** `BusinessRuleController#create` (`src/main/java/src/main/java/com/edam/businessrules/controller/BusinessRuleController.java`)
- **Authentication:** Authenticated; permission `rule:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | Yes | ≤200 chars |
| `module` | string | Yes | ≤60 chars |
| `description` | string | No | ≤2000 chars |

**Example:**

```json
{
  "name": "string",
  "module": "string",
  "description": "string"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `rule:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BR-004 — PUT /business-rules/{id}

- **Module:** Business Rules
- **Purpose:** Update an existing `{id}`.
- **Controller / handler:** `BusinessRuleController#update` (`src/main/java/src/main/java/com/edam/businessrules/controller/BusinessRuleController.java`)
- **Authentication:** Authenticated; permission `rule:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | No | ≤200 chars |
| `module` | string | No | ≤60 chars |
| `description` | string | No | ≤2000 chars |
| `active` | boolean | No |  |

**Example:**

```json
{
  "name": "string",
  "module": "string",
  "description": "string",
  "active": true
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `rule:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BR-005 — POST /business-rules/{id}/drafts

- **Module:** Business Rules
- **Purpose:** `newDraft` on `/business-rules/{id}/drafts`.
- **Controller / handler:** `BusinessRuleController#newDraft` (`src/main/java/src/main/java/com/edam/businessrules/controller/BusinessRuleController.java`)
- **Authentication:** Authenticated; permission `rule:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `rule:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BR-006 — POST /business-rules/{id}/publish

- **Module:** Business Rules
- **Purpose:** `publish` on `/business-rules/{id}/publish`.
- **Controller / handler:** `BusinessRuleController#publish` (`src/main/java/src/main/java/com/edam/businessrules/controller/BusinessRuleController.java`)
- **Authentication:** Authenticated; permission `rule:publish`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `rule:publish`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BR-007 — POST /business-rules/{id}/retire

- **Module:** Business Rules
- **Purpose:** `retire` on `/business-rules/{id}/retire`.
- **Controller / handler:** `BusinessRuleController#retire` (`src/main/java/src/main/java/com/edam/businessrules/controller/BusinessRuleController.java`)
- **Authentication:** Authenticated; permission `rule:publish`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `rule:publish`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BR-008 — PUT /business-rules/versions/{versionId}/rules

- **Module:** Business Rules
- **Purpose:** Update an existing `rules`.
- **Controller / handler:** `BusinessRuleController#upsertRule` (`src/main/java/src/main/java/com/edam/businessrules/controller/BusinessRuleController.java`)
- **Authentication:** Authenticated; permission `rule:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `versionId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string (uuid) | No |  |
| `name` | string | Yes | ≤200 chars |
| `condition` | Map<String, Object> | No |  |
| `action` | Map<String, Object> | No |  |
| `priority` | number (int) | No |  |
| `active` | boolean | No |  |

**Example:**

```json
{
  "id": "00000000-0000-7000-8000-000000000001",
  "name": "string",
  "condition": {},
  "action": {},
  "priority": 1,
  "active": true
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `rule:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## BR-009 — DELETE /business-rules/rules/{ruleId}

- **Module:** Business Rules
- **Purpose:** Delete `{ruleId}`.
- **Controller / handler:** `BusinessRuleController#deleteRule` (`src/main/java/src/main/java/com/edam/businessrules/controller/BusinessRuleController.java`)
- **Authentication:** Authenticated; permission `rule:manage`
- **CSRF:** Required
- **Content-Type:** —
- **Success status:** 204

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `ruleId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (204)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `rule:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CMT-001 — GET /committees

- **Module:** Committees
- **Purpose:** `list` on `/committees`.
- **Controller / handler:** `CommitteeController#list` (`src/main/java/src/main/java/com/edam/committee/controller/CommitteeController.java`)
- **Authentication:** Authenticated; permission `committee:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `committee:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CMT-002 — GET /committees/{id}

- **Module:** Committees
- **Purpose:** Fetch `{id}` by id.
- **Controller / handler:** `CommitteeController#get` (`src/main/java/src/main/java/com/edam/committee/controller/CommitteeController.java`)
- **Authentication:** Authenticated; permission `committee:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `committee:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CMT-003 — POST /committees

- **Module:** Committees
- **Purpose:** Create a new `committees`.
- **Controller / handler:** `CommitteeController#create` (`src/main/java/src/main/java/com/edam/committee/controller/CommitteeController.java`)
- **Authentication:** Authenticated; permission `committee:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `committeeCode` | string | Yes | ≤64 chars |
| `committeeName` | string | Yes | ≤200 chars |
| `description` | string | No | ≤2000 chars |

**Example:**

```json
{
  "committeeCode": "string",
  "committeeName": "string",
  "description": "string"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `committee:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CMT-004 — POST /committees/{id}

- **Module:** Committees
- **Purpose:** `rename` on `/committees/{id}`.
- **Controller / handler:** `CommitteeController#rename` (`src/main/java/src/main/java/com/edam/committee/controller/CommitteeController.java`)
- **Authentication:** Authenticated; permission `committee:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `committeeCode` | string | Yes | ≤64 chars |
| `committeeName` | string | Yes | ≤200 chars |
| `description` | string | No | ≤2000 chars |

**Example:**

```json
{
  "committeeCode": "string",
  "committeeName": "string",
  "description": "string"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `committee:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CMT-005 — POST /committees/{id}/deactivate

- **Module:** Committees
- **Purpose:** `deactivate` on `/committees/{id}/deactivate`.
- **Controller / handler:** `CommitteeController#deactivate` (`src/main/java/src/main/java/com/edam/committee/controller/CommitteeController.java`)
- **Authentication:** Authenticated; permission `committee:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `committee:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CMT-006 — POST /committees/{id}/members

- **Module:** Committees
- **Purpose:** `addMember` on `/committees/{id}/members`.
- **Controller / handler:** `CommitteeController#addMember` (`src/main/java/src/main/java/com/edam/committee/controller/CommitteeController.java`)
- **Authentication:** Authenticated; permission `committee:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `userId` | string (uuid) | Yes |  |
| `memberType` | CommitteeMember.MemberType | Yes |  |
| `effectiveFrom` | string (ISO-8601 instant, UTC) | No |  |

**Example:**

```json
{
  "userId": "00000000-0000-7000-8000-000000000001",
  "memberType": null,
  "effectiveFrom": "2026-11-17T14:05:47Z"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `committee:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CMT-007 — POST /committees/{id}/members/{memberId}/end

- **Module:** Committees
- **Purpose:** `endMembership` on `/committees/{id}/members/{memberId}/end`.
- **Controller / handler:** `CommitteeController#endMembership` (`src/main/java/src/main/java/com/edam/committee/controller/CommitteeController.java`)
- **Authentication:** Authenticated; permission `committee:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |
| `memberId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `committee:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CMT-008 — POST /committees/{id}/levels

- **Module:** Committees
- **Purpose:** `addLevel` on `/committees/{id}/levels`.
- **Controller / handler:** `CommitteeController#addLevel` (`src/main/java/src/main/java/com/edam/committee/controller/CommitteeController.java`)
- **Authentication:** Authenticated; permission `committee:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `levelNumber` | Short | Yes | min=?; max=? |
| `levelName` | string | Yes | ≤200 chars |
| `currencyId` | string (uuid) | Yes |  |
| `minimumApprovals` | Short | Yes | min=?; max=? |
| `members` | CommitteeLevelMemberRequest[] | Yes |  |

**Example:**

```json
{
  "levelNumber": null,
  "levelName": "string",
  "currencyId": "00000000-0000-7000-8000-000000000001",
  "minimumApprovals": null,
  "members": []
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `committee:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CMT-009 — POST /committees/{id}/levels/{levelId}/deactivate

- **Module:** Committees
- **Purpose:** `deactivateLevel` on `/committees/{id}/levels/{levelId}/deactivate`.
- **Controller / handler:** `CommitteeController#deactivateLevel` (`src/main/java/src/main/java/com/edam/committee/controller/CommitteeController.java`)
- **Authentication:** Authenticated; permission `committee:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |
| `levelId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `committee:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CMT-010 — GET /committees/coverage

- **Module:** Committees
- **Purpose:** `coverage` on `/committees/coverage`.
- **Controller / handler:** `CommitteeController#coverage` (`src/main/java/src/main/java/com/edam/committee/controller/CommitteeController.java`)
- **Authentication:** Authenticated; permission `committee:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `currencyId` | string (uuid) | No | — | — |
| `amount` | string (decimal) | No | — | — |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `committee:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## DEL-001 — GET /delegation-matrix

- **Module:** Delegation
- **Purpose:** `list` on `/delegation-matrix`.
- **Controller / handler:** `DelegationMatrixController#list` (`src/main/java/src/main/java/com/edam/delegation/controller/DelegationMatrixController.java`)
- **Authentication:** Authenticated; permission `delegation:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `delegation:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## DEL-002 — POST /delegation-matrix

- **Module:** Delegation
- **Purpose:** Create a new `delegation-matrix`.
- **Controller / handler:** `DelegationMatrixController#create` (`src/main/java/src/main/java/com/edam/delegation/controller/DelegationMatrixController.java`)
- **Authentication:** Authenticated; permission `delegation:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `categoryId` | string (uuid) | Yes |  |
| `subcategoryId` | string (uuid) | No |  |
| `natureId` | string (uuid) | Yes |  |
| `requestTypeId` | string (uuid) | No |  |
| `currencyId` | string (uuid) | Yes |  |
| `effectiveFrom` | string (ISO-8601 instant, UTC) | No |  |
| `effectiveTo` | string (ISO-8601 instant, UTC) | No |  |
| `steps` | SaveApproverStepRequest[] | Yes |  |

**Example:**

```json
{
  "categoryId": "00000000-0000-7000-8000-000000000001",
  "subcategoryId": "00000000-0000-7000-8000-000000000001",
  "natureId": "00000000-0000-7000-8000-000000000001",
  "requestTypeId": "00000000-0000-7000-8000-000000000001",
  "currencyId": "00000000-0000-7000-8000-000000000001",
  "effectiveFrom": "2026-11-17T14:05:47Z",
  "effectiveTo": "2026-11-17T14:05:47Z",
  "steps": []
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `delegation:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## DEL-003 — POST /delegation-matrix/{id}/close

- **Module:** Delegation
- **Purpose:** `close` on `/delegation-matrix/{id}/close`.
- **Controller / handler:** `DelegationMatrixController#close` (`src/main/java/src/main/java/com/edam/delegation/controller/DelegationMatrixController.java`)
- **Authentication:** Authenticated; permission `delegation:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `delegation:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## DEL-004 — POST /delegation-matrix/{id}/activation

- **Module:** Delegation
- **Purpose:** `setActive` on `/delegation-matrix/{id}/activation`.
- **Controller / handler:** `DelegationMatrixController#setActive` (`src/main/java/src/main/java/com/edam/delegation/controller/DelegationMatrixController.java`)
- **Authentication:** Authenticated; permission `delegation:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `active` | boolean | No | — | — |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `delegation:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## DEL-005 — POST /delegation-matrix/preview

- **Module:** Delegation
- **Purpose:** Create a new `preview`.
- **Controller / handler:** `DelegationMatrixController#preview` (`src/main/java/src/main/java/com/edam/delegation/controller/DelegationMatrixController.java`)
- **Authentication:** Authenticated; permission `delegation:view`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `categoryId` | string (uuid) | No | — | — |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `delegation:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## DOC-001 — POST /documents

- **Module:** Documents
- **Purpose:** Create a new `documents`.
- **Controller / handler:** `DocumentController#upload` (`src/main/java/src/main/java/com/edam/documents/controller/DocumentController.java`)
- **Authentication:** Authenticated; permission `document:upload`
- **CSRF:** Required
- **Content-Type:** multipart/form-data
- **Success status:** 201

### Request body (multipart/form-data)

| Part | Type | Required | Notes |
|---|---|---|---|
| `file` | binary (multipart) | Yes | Uploaded document. Type/size limits configured server-side (`INVALID_DOCUMENT` if violated).
| `requestId` | string (uuid) | Yes | Request the document is attached to.
| `documentType` | string enum | Yes | e.g. `INVOICE`, `QUOTATION`, `SUPPORTING`. See §10.
| `description` | string | No | Optional free-text description.

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `document:upload`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## DOC-002 — GET /documents/requests/{requestId}

- **Module:** Documents
- **Purpose:** `listForRequest` on `/documents/requests/{requestId}`.
- **Controller / handler:** `DocumentController#listForRequest` (`src/main/java/src/main/java/com/edam/documents/controller/DocumentController.java`)
- **Authentication:** Authenticated; permission `document:download`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `requestId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `document:download`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## DOC-003 — GET /documents/{id}

- **Module:** Documents
- **Purpose:** Fetch `{id}` by id.
- **Controller / handler:** `DocumentController#get` (`src/main/java/src/main/java/com/edam/documents/controller/DocumentController.java`)
- **Authentication:** Authenticated; permission `document:download`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `document:download`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## DOC-004 — GET /documents/{id}/download-url

- **Module:** Documents
- **Purpose:** Fetch `download-url` by id.
- **Controller / handler:** `DocumentController#downloadUrl` (`src/main/java/src/main/java/com/edam/documents/controller/DocumentController.java`)
- **Authentication:** Authenticated; permission `document:download`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `document:download`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## DOC-005 — GET /documents/{id}/scans

- **Module:** Documents
- **Purpose:** Fetch `scans` by id.
- **Controller / handler:** `DocumentController#scans` (`src/main/java/src/main/java/com/edam/documents/controller/DocumentController.java`)
- **Authentication:** Authenticated; permission `document:download`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `document:download`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## DOC-006 — DELETE /documents/{id}

- **Module:** Documents
- **Purpose:** Delete `{id}`.
- **Controller / handler:** `DocumentController#delete` (`src/main/java/src/main/java/com/edam/documents/controller/DocumentController.java`)
- **Authentication:** Authenticated; permission `document:delete`
- **CSRF:** Required
- **Content-Type:** —
- **Success status:** 204

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (204)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `document:delete`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## TPL-001 — GET /

- **Module:** Email Templates
- **Purpose:** `list` on `/`.
- **Controller / handler:** `EmailTemplateController#list` (`src/main/java/src/main/java/com/edam/communication/controller/EmailTemplateController.java`)
- **Authentication:** Authenticated; permission `notification-template:manage`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `notification-template:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## TPL-002 — GET /{id}

- **Module:** Email Templates
- **Purpose:** Fetch `{id}` by id.
- **Controller / handler:** `EmailTemplateController#get` (`src/main/java/src/main/java/com/edam/communication/controller/EmailTemplateController.java`)
- **Authentication:** Authenticated; permission `notification-template:manage`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `notification-template:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## TPL-003 — POST /

- **Module:** Email Templates
- **Purpose:** Create a new `EmailTemplate`.
- **Controller / handler:** `EmailTemplateController#create` (`src/main/java/src/main/java/com/edam/communication/controller/EmailTemplateController.java`)
- **Authentication:** Authenticated; permission `notification-template:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `displayName` | string | Yes | ≤200 chars |
| `description` | string | No | ≤1000 chars |
| `subject` | string | Yes | ≤500 chars |
| `htmlBody` | string | Yes |  |
| `textBody` | string | No |  |
| `locale` | string | No | ≤16 chars |
| `active` | boolean | No |  |
| `allowedPlaceholders` | Set<String> | No |  |
| `changeNote` | string | No | ≤500 chars |

**Example:**

```json
{
  "displayName": "string",
  "description": "string",
  "subject": "string",
  "htmlBody": "string",
  "textBody": "string",
  "locale": "string",
  "active": true,
  "allowedPlaceholders": null,
  "changeNote": "string"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `notification-template:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## TPL-004 — POST /{id}/versions

- **Module:** Email Templates
- **Purpose:** `newVersion` on `/{id}/versions`.
- **Controller / handler:** `EmailTemplateController#newVersion` (`src/main/java/src/main/java/com/edam/communication/controller/EmailTemplateController.java`)
- **Authentication:** Authenticated; permission `notification-template:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `displayName` | string | Yes | ≤200 chars |
| `description` | string | No | ≤1000 chars |
| `subject` | string | Yes | ≤500 chars |
| `htmlBody` | string | Yes |  |
| `textBody` | string | No |  |
| `locale` | string | No | ≤16 chars |
| `active` | boolean | No |  |
| `allowedPlaceholders` | Set<String> | No |  |
| `changeNote` | string | No | ≤500 chars |

**Example:**

```json
{
  "displayName": "string",
  "description": "string",
  "subject": "string",
  "htmlBody": "string",
  "textBody": "string",
  "locale": "string",
  "active": true,
  "allowedPlaceholders": null,
  "changeNote": "string"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `notification-template:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## TPL-005 — GET /{id}/versions

- **Module:** Email Templates
- **Purpose:** Fetch `versions` by id.
- **Controller / handler:** `EmailTemplateController#history` (`src/main/java/src/main/java/com/edam/communication/controller/EmailTemplateController.java`)
- **Authentication:** Authenticated; permission `notification-template:manage`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `notification-template:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## TPL-006 — PUT /{id}/active

- **Module:** Email Templates
- **Purpose:** Update an existing `active`.
- **Controller / handler:** `EmailTemplateController#setActive` (`src/main/java/src/main/java/com/edam/communication/controller/EmailTemplateController.java`)
- **Authentication:** Authenticated; permission `notification-template:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `notification-template:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## TPL-007 — POST /{id}/preview

- **Module:** Email Templates
- **Purpose:** `preview` on `/{id}/preview`.
- **Controller / handler:** `EmailTemplateController#preview` (`src/main/java/src/main/java/com/edam/communication/controller/EmailTemplateController.java`)
- **Authentication:** Authenticated; permission `notification-template:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `notification-template:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CFG-001 — GET /expense-configuration/categories

- **Module:** Expense Configuration
- **Purpose:** `categories` on `/expense-configuration/categories`.
- **Controller / handler:** `ExpenseConfigurationController#categories` (`src/main/java/src/main/java/com/edam/expenseconfiguration/controller/ExpenseConfigurationController.java`)
- **Authentication:** Authenticated; permission `expense-configuration:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `expense-configuration:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CFG-002 — POST /expense-configuration/categories

- **Module:** Expense Configuration
- **Purpose:** Create a new `categories`.
- **Controller / handler:** `ExpenseConfigurationController#createCategory` (`src/main/java/src/main/java/com/edam/expenseconfiguration/controller/ExpenseConfigurationController.java`)
- **Authentication:** Authenticated; permission `expense-configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | Yes | ≤64 chars; pattern |
| `name` | string | Yes | ≤200 chars |
| `description` | string | No | ≤2000 chars |
| `expenseType` | ExpenseType | Yes |  |

**Example:**

```json
{
  "code": "string",
  "name": "string",
  "description": "string",
  "expenseType": null
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `expense-configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CFG-003 — PUT /expense-configuration/categories/{id}

- **Module:** Expense Configuration
- **Purpose:** Update an existing `{id}`.
- **Controller / handler:** `ExpenseConfigurationController#updateCategory` (`src/main/java/src/main/java/com/edam/expenseconfiguration/controller/ExpenseConfigurationController.java`)
- **Authentication:** Authenticated; permission `expense-configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | Yes | ≤64 chars; pattern |
| `name` | string | Yes | ≤200 chars |
| `description` | string | No | ≤2000 chars |
| `expenseType` | ExpenseType | Yes |  |

**Example:**

```json
{
  "code": "string",
  "name": "string",
  "description": "string",
  "expenseType": null
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `expense-configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CFG-004 — POST /expense-configuration/categories/{id}/activation

- **Module:** Expense Configuration
- **Purpose:** `setCategoryActive` on `/expense-configuration/categories/{id}/activation`.
- **Controller / handler:** `ExpenseConfigurationController#setCategoryActive` (`src/main/java/src/main/java/com/edam/expenseconfiguration/controller/ExpenseConfigurationController.java`)
- **Authentication:** Authenticated; permission `expense-configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `active` | boolean | No | — | — |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `expense-configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CFG-005 — POST /expense-configuration/categories/{categoryId}/subcategories

- **Module:** Expense Configuration
- **Purpose:** Create a new `subcategories`.
- **Controller / handler:** `ExpenseConfigurationController#createSubcategory` (`src/main/java/src/main/java/com/edam/expenseconfiguration/controller/ExpenseConfigurationController.java`)
- **Authentication:** Authenticated; permission `expense-configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `categoryId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | Yes | ≤64 chars; pattern |
| `name` | string | Yes | ≤200 chars |
| `description` | string | No | ≤2000 chars |

**Example:**

```json
{
  "code": "string",
  "name": "string",
  "description": "string"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `expense-configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CFG-006 — PUT /expense-configuration/subcategories/{id}

- **Module:** Expense Configuration
- **Purpose:** Update an existing `{id}`.
- **Controller / handler:** `ExpenseConfigurationController#updateSubcategory` (`src/main/java/src/main/java/com/edam/expenseconfiguration/controller/ExpenseConfigurationController.java`)
- **Authentication:** Authenticated; permission `expense-configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | Yes | ≤64 chars; pattern |
| `name` | string | Yes | ≤200 chars |
| `description` | string | No | ≤2000 chars |

**Example:**

```json
{
  "code": "string",
  "name": "string",
  "description": "string"
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `expense-configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CFG-007 — POST /expense-configuration/subcategories/{id}/activation

- **Module:** Expense Configuration
- **Purpose:** `setSubcategoryActive` on `/expense-configuration/subcategories/{id}/activation`.
- **Controller / handler:** `ExpenseConfigurationController#setSubcategoryActive` (`src/main/java/src/main/java/com/edam/expenseconfiguration/controller/ExpenseConfigurationController.java`)
- **Authentication:** Authenticated; permission `expense-configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `active` | boolean | No | — | — |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `expense-configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CFG-008 — GET /expense-configuration/natures

- **Module:** Expense Configuration
- **Purpose:** `natures` on `/expense-configuration/natures`.
- **Controller / handler:** `ExpenseConfigurationController#natures` (`src/main/java/src/main/java/com/edam/expenseconfiguration/controller/ExpenseConfigurationController.java`)
- **Authentication:** Authenticated; permission `expense-configuration:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `expense-configuration:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CFG-009 — POST /expense-configuration/natures

- **Module:** Expense Configuration
- **Purpose:** Create a new `natures`.
- **Controller / handler:** `ExpenseConfigurationController#createNature` (`src/main/java/src/main/java/com/edam/expenseconfiguration/controller/ExpenseConfigurationController.java`)
- **Authentication:** Authenticated; permission `expense-configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | Yes | ≤64 chars; pattern |
| `name` | string | Yes | ≤200 chars |
| `description` | string | No | ≤2000 chars |

**Example:**

```json
{
  "code": "string",
  "name": "string",
  "description": "string"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `expense-configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CFG-010 — POST /expense-configuration/natures/{id}/activation

- **Module:** Expense Configuration
- **Purpose:** `setNatureActive` on `/expense-configuration/natures/{id}/activation`.
- **Controller / handler:** `ExpenseConfigurationController#setNatureActive` (`src/main/java/src/main/java/com/edam/expenseconfiguration/controller/ExpenseConfigurationController.java`)
- **Authentication:** Authenticated; permission `expense-configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `active` | boolean | No | — | — |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `expense-configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CFG-011 — GET /expense-configuration/request-types

- **Module:** Expense Configuration
- **Purpose:** `requestTypes` on `/expense-configuration/request-types`.
- **Controller / handler:** `ExpenseConfigurationController#requestTypes` (`src/main/java/src/main/java/com/edam/expenseconfiguration/controller/ExpenseConfigurationController.java`)
- **Authentication:** Authenticated; permission `expense-configuration:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `expense-configuration:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## CFG-012 — POST /expense-configuration/request-types

- **Module:** Expense Configuration
- **Purpose:** Create a new `request-types`.
- **Controller / handler:** `ExpenseConfigurationController#createRequestType` (`src/main/java/src/main/java/com/edam/expenseconfiguration/controller/ExpenseConfigurationController.java`)
- **Authentication:** Authenticated; permission `expense-configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | Yes | ≤64 chars; pattern |
| `name` | string | Yes | ≤200 chars |
| `description` | string | No | ≤2000 chars |

**Example:**

```json
{
  "code": "string",
  "name": "string",
  "description": "string"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `expense-configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## INT-001 — GET /integrations/providers

- **Module:** Integrations
- **Purpose:** `providers` on `/integrations/providers`.
- **Controller / handler:** `IntegrationController#providers` (`src/main/java/src/main/java/com/edam/integrations/controller/IntegrationController.java`)
- **Authentication:** Authenticated; permission `integration:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `integration:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## INT-002 — GET /integrations

- **Module:** Integrations
- **Purpose:** `list` on `/integrations`.
- **Controller / handler:** `IntegrationController#list` (`src/main/java/src/main/java/com/edam/integrations/controller/IntegrationController.java`)
- **Authentication:** Authenticated; permission `integration:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `integration:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## INT-003 — GET /integrations/{id}

- **Module:** Integrations
- **Purpose:** Fetch `{id}` by id.
- **Controller / handler:** `IntegrationController#detail` (`src/main/java/src/main/java/com/edam/integrations/controller/IntegrationController.java`)
- **Authentication:** Authenticated; permission `integration:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `integration:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## INT-004 — POST /integrations

- **Module:** Integrations
- **Purpose:** Create a new `integrations`.
- **Controller / handler:** `IntegrationController#create` (`src/main/java/src/main/java/com/edam/integrations/controller/IntegrationController.java`)
- **Authentication:** Authenticated; permission `integration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | No |  |
| `name` | string | No |  |
| `providerType` | string | No |  |
| `description` | string | No |  |
| `endpointUrl` | string | No |  |
| `authMode` | AuthMode | No |  |
| `username` | string | No |  |
| `secret` | string | No |  |
| `secretAction` | SecretAction | No |  |
| `configuration` | Map<String, Object> | No |  |

**Example:**

```json
{
  "code": "string",
  "name": "string",
  "providerType": "string",
  "description": "string",
  "endpointUrl": "string",
  "authMode": null,
  "username": "j.doe",
  "secret": "string",
  "secretAction": null,
  "configuration": {}
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `integration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## INT-005 — PUT /integrations/{id}

- **Module:** Integrations
- **Purpose:** Update an existing `{id}`.
- **Controller / handler:** `IntegrationController#update` (`src/main/java/src/main/java/com/edam/integrations/controller/IntegrationController.java`)
- **Authentication:** Authenticated; permission `integration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | No |  |
| `description` | string | No |  |
| `endpointUrl` | string | No |  |
| `authMode` | AuthMode | No |  |
| `username` | string | No |  |
| `secret` | string | No |  |
| `secretAction` | SecretAction | No |  |
| `configuration` | Map<String, Object> | No |  |
| `expectedVersion` | number (int) | No |  |

**Example:**

```json
{
  "name": "string",
  "description": "string",
  "endpointUrl": "string",
  "authMode": null,
  "username": "j.doe",
  "secret": "string",
  "secretAction": null,
  "configuration": {},
  "expectedVersion": 1
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `integration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## INT-006 — POST /integrations/{id}/activate

- **Module:** Integrations
- **Purpose:** `activate` on `/integrations/{id}/activate`.
- **Controller / handler:** `IntegrationController#activate` (`src/main/java/src/main/java/com/edam/integrations/controller/IntegrationController.java`)
- **Authentication:** Authenticated; permission `integration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `integration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## INT-007 — POST /integrations/{id}/deactivate

- **Module:** Integrations
- **Purpose:** `deactivate` on `/integrations/{id}/deactivate`.
- **Controller / handler:** `IntegrationController#deactivate` (`src/main/java/src/main/java/com/edam/integrations/controller/IntegrationController.java`)
- **Authentication:** Authenticated; permission `integration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `integration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## INT-008 — DELETE /integrations/{id}

- **Module:** Integrations
- **Purpose:** Delete `{id}`.
- **Controller / handler:** `IntegrationController#archive` (`src/main/java/src/main/java/com/edam/integrations/controller/IntegrationController.java`)
- **Authentication:** Authenticated; permission `integration:manage`
- **CSRF:** Required
- **Content-Type:** —
- **Success status:** 204

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (204)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `integration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## INT-009 — POST /integrations/{id}/test

- **Module:** Integrations
- **Purpose:** `test` on `/integrations/{id}/test`.
- **Controller / handler:** `IntegrationController#test` (`src/main/java/src/main/java/com/edam/integrations/controller/IntegrationController.java`)
- **Authentication:** Authenticated; permission `integration:test`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `integration:test`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## INT-010 — POST /integrations/{id}/executions

- **Module:** Integrations
- **Purpose:** `execute` on `/integrations/{id}/executions`.
- **Controller / handler:** `IntegrationController#execute` (`src/main/java/src/main/java/com/edam/integrations/controller/IntegrationController.java`)
- **Authentication:** Authenticated; permission `integration:execute`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `operation` | string | No |  |
| `idempotencyKey` | string | No |  |
| `requestMetadata` | Map<String, Object> | No |  |

**Example:**

```json
{
  "operation": "string",
  "idempotencyKey": "string",
  "requestMetadata": {}
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `integration:execute`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## INT-011 — GET /integrations/{id}/executions

- **Module:** Integrations
- **Purpose:** Fetch `executions` by id.
- **Controller / handler:** `IntegrationController#history` (`src/main/java/src/main/java/com/edam/integrations/controller/IntegrationController.java`)
- **Authentication:** Authenticated; permission `integration:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `integration:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## INT-012 — GET /integrations/executions/{execId}

- **Module:** Integrations
- **Purpose:** `executionDetail` on `/integrations/executions/{execId}`.
- **Controller / handler:** `IntegrationController#executionDetail` (`src/main/java/src/main/java/com/edam/integrations/controller/IntegrationController.java`)
- **Authentication:** Authenticated; permission `integration:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `execId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `integration:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## INT-013 — POST /integrations/executions/{execId}/retry

- **Module:** Integrations
- **Purpose:** Create a new `retry`.
- **Controller / handler:** `IntegrationController#retry` (`src/main/java/src/main/java/com/edam/integrations/controller/IntegrationController.java`)
- **Authentication:** Authenticated; permission `integration:execute`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `execId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `integration:execute`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## MST-001 — GET /master-data/currencies

- **Module:** Master Data
- **Purpose:** `currencies` on `/master-data/currencies`.
- **Controller / handler:** `MasterDataController#currencies` (`src/main/java/src/main/java/com/edam/masterdata/controller/MasterDataController.java`)
- **Authentication:** Authenticated; permission `currency:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `currency:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## MST-002 — POST /master-data/currencies

- **Module:** Master Data
- **Purpose:** Create a new `currencies`.
- **Controller / handler:** `MasterDataController#createCurrency` (`src/main/java/src/main/java/com/edam/masterdata/controller/MasterDataController.java`)
- **Authentication:** Authenticated; permission `currency:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | Yes | ≤100 chars |
| `symbol` | string | No | ≤10 chars |

**Example:**

```json
{
  "name": "string",
  "symbol": "string"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `currency:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## MST-003 — PUT /master-data/currencies/{id}

- **Module:** Master Data
- **Purpose:** Update an existing `{id}`.
- **Controller / handler:** `MasterDataController#updateCurrency` (`src/main/java/src/main/java/com/edam/masterdata/controller/MasterDataController.java`)
- **Authentication:** Authenticated; permission `currency:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | Yes | ≤100 chars |
| `symbol` | string | No | ≤10 chars |

**Example:**

```json
{
  "name": "string",
  "symbol": "string"
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `currency:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## MST-004 — POST /master-data/currencies/{id}/activation

- **Module:** Master Data
- **Purpose:** `setCurrencyActive` on `/master-data/currencies/{id}/activation`.
- **Controller / handler:** `MasterDataController#setCurrencyActive` (`src/main/java/src/main/java/com/edam/masterdata/controller/MasterDataController.java`)
- **Authentication:** Authenticated; permission `currency:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `active` | boolean | No | — | — |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `currency:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## MST-005 — GET /master-data/exchange-rates

- **Module:** Master Data
- **Purpose:** `exchangeRates` on `/master-data/exchange-rates`.
- **Controller / handler:** `MasterDataController#exchangeRates` (`src/main/java/src/main/java/com/edam/masterdata/controller/MasterDataController.java`)
- **Authentication:** Authenticated; permission `currency:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `currency:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## MST-006 — POST /master-data/exchange-rates

- **Module:** Master Data
- **Purpose:** Create a new `exchange-rates`.
- **Controller / handler:** `MasterDataController#createRate` (`src/main/java/src/main/java/com/edam/masterdata/controller/MasterDataController.java`)
- **Authentication:** Authenticated; permission `currency:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `fromCurrencyId` | string (uuid) | Yes |  |
| `toCurrencyId` | string (uuid) | Yes |  |
| `effectiveFrom` | string (ISO-8601 instant, UTC) | No |  |
| `effectiveTo` | string (ISO-8601 instant, UTC) | No |  |

**Example:**

```json
{
  "fromCurrencyId": "00000000-0000-7000-8000-000000000001",
  "toCurrencyId": "00000000-0000-7000-8000-000000000001",
  "effectiveFrom": "2026-11-17T14:05:47Z",
  "effectiveTo": "2026-11-17T14:05:47Z"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `currency:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## MST-007 — POST /master-data/exchange-rates/{id}/close

- **Module:** Master Data
- **Purpose:** `closeRate` on `/master-data/exchange-rates/{id}/close`.
- **Controller / handler:** `MasterDataController#closeRate` (`src/main/java/src/main/java/com/edam/masterdata/controller/MasterDataController.java`)
- **Authentication:** Authenticated; permission `currency:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `currency:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## MST-008 — GET /master-data/vendors

- **Module:** Master Data
- **Purpose:** `vendors` on `/master-data/vendors`.
- **Controller / handler:** `MasterDataController#vendors` (`src/main/java/src/main/java/com/edam/masterdata/controller/MasterDataController.java`)
- **Authentication:** Authenticated; permission `vendor:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `vendor:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## MST-009 — GET /master-data/vendors/{id}

- **Module:** Master Data
- **Purpose:** Fetch `{id}` by id.
- **Controller / handler:** `MasterDataController#vendor` (`src/main/java/src/main/java/com/edam/masterdata/controller/MasterDataController.java`)
- **Authentication:** Authenticated; permission `vendor:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `vendor:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## MST-010 — POST /master-data/vendors

- **Module:** Master Data
- **Purpose:** Create a new `vendors`.
- **Controller / handler:** `MasterDataController#createVendor` (`src/main/java/src/main/java/com/edam/masterdata/controller/MasterDataController.java`)
- **Authentication:** Authenticated; permission `vendor:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | Yes | ≤64 chars |
| `name` | string | Yes | ≤300 chars |
| `taxNumber` | string | No | ≤64 chars |
| `contactPerson` | string | No | ≤200 chars |
| `email` | string | No | email; ≤255 chars |
| `phone` | string | No | ≤50 chars |
| `address` | string | No | ≤2000 chars |

**Example:**

```json
{
  "code": "string",
  "name": "string",
  "taxNumber": "string",
  "contactPerson": "string",
  "email": "user@example.com",
  "phone": "string",
  "address": "string"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `vendor:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## MST-011 — PUT /master-data/vendors/{id}

- **Module:** Master Data
- **Purpose:** Update an existing `{id}`.
- **Controller / handler:** `MasterDataController#updateVendor` (`src/main/java/src/main/java/com/edam/masterdata/controller/MasterDataController.java`)
- **Authentication:** Authenticated; permission `vendor:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | Yes | ≤64 chars |
| `name` | string | Yes | ≤300 chars |
| `taxNumber` | string | No | ≤64 chars |
| `contactPerson` | string | No | ≤200 chars |
| `email` | string | No | email; ≤255 chars |
| `phone` | string | No | ≤50 chars |
| `address` | string | No | ≤2000 chars |

**Example:**

```json
{
  "code": "string",
  "name": "string",
  "taxNumber": "string",
  "contactPerson": "string",
  "email": "user@example.com",
  "phone": "string",
  "address": "string"
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `vendor:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## MST-012 — POST /master-data/vendors/{id}/activation

- **Module:** Master Data
- **Purpose:** `setVendorActive` on `/master-data/vendors/{id}/activation`.
- **Controller / handler:** `MasterDataController#setVendorActive` (`src/main/java/src/main/java/com/edam/masterdata/controller/MasterDataController.java`)
- **Authentication:** Authenticated; permission `vendor:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `active` | boolean | No | — | — |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `vendor:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## MST-013 — GET /master-data/tax-authorities

- **Module:** Master Data
- **Purpose:** `taxAuthorities` on `/master-data/tax-authorities`.
- **Controller / handler:** `MasterDataController#taxAuthorities` (`src/main/java/src/main/java/com/edam/masterdata/controller/MasterDataController.java`)
- **Authentication:** Authenticated; permission `currency:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `currency:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## MST-014 — POST /master-data/tax-authorities

- **Module:** Master Data
- **Purpose:** Create a new `tax-authorities`.
- **Controller / handler:** `MasterDataController#createTaxAuthority` (`src/main/java/src/main/java/com/edam/masterdata/controller/MasterDataController.java`)
- **Authentication:** Authenticated; permission `tax-authority:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | Yes | ≤64 chars |
| `name` | string | Yes | ≤200 chars |
| `description` | string | No | ≤2000 chars |

**Example:**

```json
{
  "code": "string",
  "name": "string",
  "description": "string"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `tax-authority:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## MST-015 — PUT /master-data/tax-authorities/{id}

- **Module:** Master Data
- **Purpose:** Update an existing `{id}`.
- **Controller / handler:** `MasterDataController#updateTaxAuthority` (`src/main/java/src/main/java/com/edam/masterdata/controller/MasterDataController.java`)
- **Authentication:** Authenticated; permission `tax-authority:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | Yes | ≤64 chars |
| `name` | string | Yes | ≤200 chars |
| `description` | string | No | ≤2000 chars |

**Example:**

```json
{
  "code": "string",
  "name": "string",
  "description": "string"
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `tax-authority:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## MST-016 — POST /master-data/tax-authorities/{id}/activation

- **Module:** Master Data
- **Purpose:** `setTaxAuthorityActive` on `/master-data/tax-authorities/{id}/activation`.
- **Controller / handler:** `MasterDataController#setTaxAuthorityActive` (`src/main/java/src/main/java/com/edam/masterdata/controller/MasterDataController.java`)
- **Authentication:** Authenticated; permission `tax-authority:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `active` | boolean | No | — | — |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `tax-authority:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## EVT-001 — GET /

- **Module:** Notification Triggers
- **Purpose:** `list` on `/`.
- **Controller / handler:** `NotificationEventConfigController#list` (`src/main/java/src/main/java/com/edam/communication/controller/NotificationEventConfigController.java`)
- **Authentication:** Authenticated; permission `notification-template:manage`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `notification-template:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## EVT-002 — PUT /{eventCode}

- **Module:** Notification Triggers
- **Purpose:** Update an existing `{eventCode}`.
- **Controller / handler:** `NotificationEventConfigController#update` (`src/main/java/src/main/java/com/edam/communication/controller/NotificationEventConfigController.java`)
- **Authentication:** Authenticated; permission `notification-template:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `eventCode` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `emailEnabled` | boolean | Yes |  |
| `inAppEnabled` | boolean | Yes |  |
| `emailTemplateId` | string (uuid) | No |  |
| `recipientGroups` | Set<String> | Yes | pattern |
| `active` | boolean | Yes |  |

**Example:**

```json
{
  "emailEnabled": true,
  "inAppEnabled": true,
  "emailTemplateId": "00000000-0000-7000-8000-000000000001",
  "recipientGroups": null,
  "active": true
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `notification-template:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## NTF-001 — GET /

- **Module:** Notifications
- **Purpose:** `list` on `/`.
- **Controller / handler:** `NotificationInboxController#list` (`src/main/java/src/main/java/com/edam/communication/controller/NotificationInboxController.java`)
- **Authentication:** Authenticated; permission `notification:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `notification:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## NTF-002 — GET /unread-count

- **Module:** Notifications
- **Purpose:** `unreadCount` on `/unread-count`.
- **Controller / handler:** `NotificationInboxController#unreadCount` (`src/main/java/src/main/java/com/edam/communication/controller/NotificationInboxController.java`)
- **Authentication:** Authenticated; permission `notification:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `notification:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## NTF-003 — POST /{id}/read

- **Module:** Notifications
- **Purpose:** `markRead` on `/{id}/read`.
- **Controller / handler:** `NotificationInboxController#markRead` (`src/main/java/src/main/java/com/edam/communication/controller/NotificationInboxController.java`)
- **Authentication:** Authenticated; permission `notification:view`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `notification:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## NTF-004 — POST /read-all

- **Module:** Notifications
- **Purpose:** Create a new `read-all`.
- **Controller / handler:** `NotificationInboxController#markAllRead` (`src/main/java/src/main/java/com/edam/communication/controller/NotificationInboxController.java`)
- **Authentication:** Authenticated; permission `notification:view`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `notification:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ORG-001 — GET /organization/profile

- **Module:** Organization
- **Purpose:** `getProfile` on `/organization/profile`.
- **Controller / handler:** `OrganizationController#getProfile` (`src/main/java/src/main/java/com/edam/organization/controller/OrganizationController.java`)
- **Authentication:** Authenticated; permission `organization:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `organization:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ORG-002 — POST /organization/profile

- **Module:** Organization
- **Purpose:** Create a new `profile`.
- **Controller / handler:** `OrganizationController#createProfile` (`src/main/java/src/main/java/com/edam/organization/controller/OrganizationController.java`)
- **Authentication:** Authenticated; permission `configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `displayName` | string | No | ≤200 chars |
| `legalName` | string | No | ≤300 chars |
| `contactEmail` | string | No | email; ≤255 chars |
| `contactPhone` | string | No | ≤40 chars |
| `supportEmail` | string | No | ≤255 chars; pattern |
| `addressLine1` | string | No | ≤300 chars |
| `addressLine2` | string | No | ≤300 chars |
| `addressCity` | string | No | ≤120 chars |
| `addressState` | string | No | ≤120 chars |
| `addressPostalCode` | string | No | ≤30 chars |
| `addressCountry` | string | No | ≤100 chars |
| `logoAssetId` | string (uuid) | No |  |
| `faviconAssetId` | string (uuid) | No |  |
| `timezone` | string | No | ≤60 chars |
| `dateFormat` | string | No | ≤40 chars |
| `displayName` | string | No | ≤200 chars |
| `legalName` | string | No | ≤300 chars |
| `contactEmail` | string | No | email; ≤255 chars |
| `contactPhone` | string | No | ≤40 chars |
| `supportEmail` | string | No | ≤255 chars; pattern |
| `addressLine1` | string | No | ≤300 chars |
| `addressLine2` | string | No | ≤300 chars |
| `addressCity` | string | No | ≤120 chars |
| `addressState` | string | No | ≤120 chars |
| `addressPostalCode` | string | No | ≤30 chars |
| `addressCountry` | string | No | ≤100 chars |
| `logoAssetId` | string (uuid) | No |  |
| `faviconAssetId` | string (uuid) | No |  |
| `timezone` | string | No | ≤60 chars |
| `dateFormat` | string | No | ≤40 chars |
| `parentUnitId` | string (uuid) | No |  |
| `unitTypeId` | string (uuid) | No |  |
| `unitTypeCode` | string | No |  |
| `unitTypeName` | string | No |  |
| `code` | string | No |  |
| `name` | string | No |  |
| `description` | string | No |  |
| `active` | boolean | No |  |
| `description` | string | No | ≤500 chars |
| `unitTypeId` | string (uuid) | No |  |
| `name` | string | No | ≤150 chars |
| `description` | string | No | ≤500 chars |
| `code` | string | No |  |
| `name` | string | No |  |
| `unitId` | string (uuid) | No |  |
| `code` | string | No |  |
| `name` | string | No |  |
| `headUserId` | string (uuid) | No |  |
| `headName` | string | No |  |
| `description` | string | No | ≤500 chars |
| `parentUnitId` | string (uuid) | No |  |
| `unitId` | string (uuid) | No |  |
| `code` | string | No |  |
| `name` | string | No |  |
| `address` | string | No |  |
| `description` | string | No | ≤500 chars |
| `parentUnitId` | string (uuid) | No |  |

**Example:**

```json
{
  "displayName": "string",
  "legalName": "string",
  "contactEmail": "user@example.com",
  "contactPhone": "string",
  "supportEmail": "user@example.com",
  "addressLine1": "string",
  "addressLine2": "string",
  "addressCity": "string",
  "addressState": "string",
  "addressPostalCode": "string",
  "addressCountry": "string",
  "logoAssetId": "00000000-0000-7000-8000-000000000001",
  "faviconAssetId": "00000000-0000-7000-8000-000000000001",
  "timezone": "string",
  "dateFormat": "string",
  "parentUnitId": "00000000-0000-7000-8000-000000000001",
  "unitTypeId": "00000000-0000-7000-8000-000000000001",
  "unitTypeCode": "string",
  "unitTypeName": "string",
  "code": "string",
  "name": "string",
  "description": "string",
  "active": true,
  "unitId": "00000000-0000-7000-8000-000000000001",
  "headUserId": "00000000-0000-7000-8000-000000000001",
  "headName": "string",
  "address": "string"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ORG-003 — PATCH /organization/profile

- **Module:** Organization
- **Purpose:** Patch `profile`.
- **Controller / handler:** `OrganizationController#updateProfile` (`src/main/java/src/main/java/com/edam/organization/controller/OrganizationController.java`)
- **Authentication:** Authenticated; permission `configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `organizationName` | string | No | ≤200 chars |
| `displayName` | string | No | ≤200 chars |
| `legalName` | string | No | ≤300 chars |
| `contactEmail` | string | No | email; ≤255 chars |
| `contactPhone` | string | No | ≤40 chars |
| `supportEmail` | string | No | ≤255 chars; pattern |
| `addressLine1` | string | No | ≤300 chars |
| `addressLine2` | string | No | ≤300 chars |
| `addressCity` | string | No | ≤120 chars |
| `addressState` | string | No | ≤120 chars |
| `addressPostalCode` | string | No | ≤30 chars |
| `addressCountry` | string | No | ≤100 chars |
| `logoAssetId` | string (uuid) | No |  |
| `faviconAssetId` | string (uuid) | No |  |
| `timezone` | string | No | ≤60 chars |
| `dateFormat` | string | No | ≤40 chars |
| `parentUnitId` | string (uuid) | No |  |
| `unitTypeId` | string (uuid) | No |  |
| `unitTypeCode` | string | No |  |
| `unitTypeName` | string | No |  |
| `code` | string | No |  |
| `name` | string | No |  |
| `description` | string | No |  |
| `active` | boolean | No |  |
| `description` | string | No | ≤500 chars |
| `unitTypeId` | string (uuid) | No |  |
| `name` | string | No | ≤150 chars |
| `description` | string | No | ≤500 chars |
| `code` | string | No |  |
| `name` | string | No |  |
| `unitId` | string (uuid) | No |  |
| `code` | string | No |  |
| `name` | string | No |  |
| `headUserId` | string (uuid) | No |  |
| `headName` | string | No |  |
| `description` | string | No | ≤500 chars |
| `parentUnitId` | string (uuid) | No |  |
| `unitId` | string (uuid) | No |  |
| `code` | string | No |  |
| `name` | string | No |  |
| `address` | string | No |  |
| `description` | string | No | ≤500 chars |
| `parentUnitId` | string (uuid) | No |  |

**Example:**

```json
{
  "organizationName": "string",
  "displayName": "string",
  "legalName": "string",
  "contactEmail": "user@example.com",
  "contactPhone": "string",
  "supportEmail": "user@example.com",
  "addressLine1": "string",
  "addressLine2": "string",
  "addressCity": "string",
  "addressState": "string",
  "addressPostalCode": "string",
  "addressCountry": "string",
  "logoAssetId": "00000000-0000-7000-8000-000000000001",
  "faviconAssetId": "00000000-0000-7000-8000-000000000001",
  "timezone": "string",
  "dateFormat": "string",
  "parentUnitId": "00000000-0000-7000-8000-000000000001",
  "unitTypeId": "00000000-0000-7000-8000-000000000001",
  "unitTypeCode": "string",
  "unitTypeName": "string",
  "code": "string",
  "name": "string",
  "description": "string",
  "active": true,
  "unitId": "00000000-0000-7000-8000-000000000001",
  "headUserId": "00000000-0000-7000-8000-000000000001",
  "headName": "string",
  "address": "string"
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ORG-004 — GET /organization/unit-types

- **Module:** Organization
- **Purpose:** `unitTypes` on `/organization/unit-types`.
- **Controller / handler:** `OrganizationController#unitTypes` (`src/main/java/src/main/java/com/edam/organization/controller/OrganizationController.java`)
- **Authentication:** Authenticated; permission `organization:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `organization:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ORG-005 — GET /organization/units

- **Module:** Organization
- **Purpose:** `units` on `/organization/units`.
- **Controller / handler:** `OrganizationController#units` (`src/main/java/src/main/java/com/edam/organization/controller/OrganizationController.java`)
- **Authentication:** Authenticated; permission `organization:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `organization:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ORG-006 — GET /organization/units/tree

- **Module:** Organization
- **Purpose:** `tree` on `/organization/units/tree`.
- **Controller / handler:** `OrganizationController#tree` (`src/main/java/src/main/java/com/edam/organization/controller/OrganizationController.java`)
- **Authentication:** Authenticated; permission `organization:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `organization:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ORG-007 — POST /organization/units

- **Module:** Organization
- **Purpose:** Create a new `units`.
- **Controller / handler:** `OrganizationController#createUnit` (`src/main/java/src/main/java/com/edam/organization/controller/OrganizationController.java`)
- **Authentication:** Authenticated; permission `organization:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | No | ≤500 chars |
| `unitTypeId` | string (uuid) | No |  |
| `parentUnitId` | string (uuid) | No |  |

**Example:**

```json
{
  "description": "string",
  "unitTypeId": "00000000-0000-7000-8000-000000000001",
  "parentUnitId": "00000000-0000-7000-8000-000000000001"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `organization:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ORG-008 — PUT /organization/units/{id}

- **Module:** Organization
- **Purpose:** Update an existing `{id}`.
- **Controller / handler:** `OrganizationController#updateUnit` (`src/main/java/src/main/java/com/edam/organization/controller/OrganizationController.java`)
- **Authentication:** Authenticated; permission `organization:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | No | ≤64 chars; pattern |
| `name` | string | No | ≤150 chars |
| `description` | string | No | ≤500 chars |
| `parentUnitId` | string (uuid) | No |  |

**Example:**

```json
{
  "code": "string",
  "name": "string",
  "description": "string",
  "parentUnitId": "00000000-0000-7000-8000-000000000001"
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `organization:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ORG-009 — POST /organization/units/{id}/activation

- **Module:** Organization
- **Purpose:** `setUnitActive` on `/organization/units/{id}/activation`.
- **Controller / handler:** `OrganizationController#setUnitActive` (`src/main/java/src/main/java/com/edam/organization/controller/OrganizationController.java`)
- **Authentication:** Authenticated; permission `organization:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `active` | boolean | No | — | — |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `organization:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ORG-010 — GET /organization/departments

- **Module:** Organization
- **Purpose:** `departments` on `/organization/departments`.
- **Controller / handler:** `OrganizationController#departments` (`src/main/java/src/main/java/com/edam/organization/controller/OrganizationController.java`)
- **Authentication:** Authenticated; permission `organization:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `organization:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ORG-011 — POST /organization/departments

- **Module:** Organization
- **Purpose:** Create a new `departments`.
- **Controller / handler:** `OrganizationController#createDepartment` (`src/main/java/src/main/java/com/edam/organization/controller/OrganizationController.java`)
- **Authentication:** Authenticated; permission `organization:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | No | ≤500 chars |
| `parentUnitId` | string (uuid) | No |  |
| `headUserId` | string (uuid) | No |  |

**Example:**

```json
{
  "description": "string",
  "parentUnitId": "00000000-0000-7000-8000-000000000001",
  "headUserId": "00000000-0000-7000-8000-000000000001"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `organization:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ORG-012 — PUT /organization/departments/{id}

- **Module:** Organization
- **Purpose:** Update an existing `{id}`.
- **Controller / handler:** `OrganizationController#updateDepartment` (`src/main/java/src/main/java/com/edam/organization/controller/OrganizationController.java`)
- **Authentication:** Authenticated; permission `organization:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | No | ≤500 chars |
| `parentUnitId` | string (uuid) | No |  |
| `headUserId` | string (uuid) | No |  |

**Example:**

```json
{
  "description": "string",
  "parentUnitId": "00000000-0000-7000-8000-000000000001",
  "headUserId": "00000000-0000-7000-8000-000000000001"
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `organization:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ORG-013 — POST /organization/departments/{id}/activation

- **Module:** Organization
- **Purpose:** `setDepartmentActive` on `/organization/departments/{id}/activation`.
- **Controller / handler:** `OrganizationController#setDepartmentActive` (`src/main/java/src/main/java/com/edam/organization/controller/OrganizationController.java`)
- **Authentication:** Authenticated; permission `organization:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `active` | boolean | No | — | — |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `organization:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ORG-014 — GET /organization/branches

- **Module:** Organization
- **Purpose:** `branches` on `/organization/branches`.
- **Controller / handler:** `OrganizationController#branches` (`src/main/java/src/main/java/com/edam/organization/controller/OrganizationController.java`)
- **Authentication:** Authenticated; permission `organization:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `organization:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ORG-015 — POST /organization/branches

- **Module:** Organization
- **Purpose:** Create a new `branches`.
- **Controller / handler:** `OrganizationController#createBranch` (`src/main/java/src/main/java/com/edam/organization/controller/OrganizationController.java`)
- **Authentication:** Authenticated; permission `organization:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | No | ≤500 chars |
| `parentUnitId` | string (uuid) | No |  |
| `address` | string | No | ≤500 chars |

**Example:**

```json
{
  "description": "string",
  "parentUnitId": "00000000-0000-7000-8000-000000000001",
  "address": "string"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `organization:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ORG-016 — PUT /organization/branches/{id}

- **Module:** Organization
- **Purpose:** Update an existing `{id}`.
- **Controller / handler:** `OrganizationController#updateBranch` (`src/main/java/src/main/java/com/edam/organization/controller/OrganizationController.java`)
- **Authentication:** Authenticated; permission `organization:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | No | ≤500 chars |
| `parentUnitId` | string (uuid) | No |  |
| `address` | string | No | ≤500 chars |

**Example:**

```json
{
  "description": "string",
  "parentUnitId": "00000000-0000-7000-8000-000000000001",
  "address": "string"
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `organization:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ORG-017 — POST /organization/branches/{id}/activation

- **Module:** Organization
- **Purpose:** `setBranchActive` on `/organization/branches/{id}/activation`.
- **Controller / handler:** `OrganizationController#setBranchActive` (`src/main/java/src/main/java/com/edam/organization/controller/OrganizationController.java`)
- **Authentication:** Authenticated; permission `organization:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `active` | boolean | No | — | — |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `organization:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## RPT-001 — GET /reporting/dashboard

- **Module:** Reports & Dashboard
- **Purpose:** `dashboard` on `/reporting/dashboard`.
- **Controller / handler:** `ReportingController#dashboard` (`src/main/java/src/main/java/com/edam/reporting/controller/ReportingController.java`)
- **Authentication:** Authenticated; permission `report:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `report:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## RPT-002 — GET /reporting/widgets

- **Module:** Reports & Dashboard
- **Purpose:** `widgetCatalogue` on `/reporting/widgets`.
- **Controller / handler:** `ReportingController#widgetCatalogue` (`src/main/java/src/main/java/com/edam/reporting/controller/ReportingController.java`)
- **Authentication:** Authenticated; permission `dashboard:manage`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `dashboard:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## RPT-003 — GET /reporting/reports

- **Module:** Reports & Dashboard
- **Purpose:** `reportCatalogue` on `/reporting/reports`.
- **Controller / handler:** `ReportingController#reportCatalogue` (`src/main/java/src/main/java/com/edam/reporting/controller/ReportingController.java`)
- **Authentication:** Authenticated; permission `dashboard:manage`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `dashboard:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## RPT-004 — GET /reporting/expense-requests/register

- **Module:** Reports & Dashboard
- **Purpose:** `register` on `/reporting/expense-requests/register`.
- **Controller / handler:** `ReportingController#register` (`src/main/java/src/main/java/com/edam/reporting/controller/ReportingController.java`)
- **Authentication:** Authenticated; permission `report:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `report:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## RPT-005 — POST /reporting/expense-requests/export

- **Module:** Reports & Dashboard
- **Purpose:** Create a new `export`.
- **Controller / handler:** `ReportingController#export` (`src/main/java/src/main/java/com/edam/reporting/controller/ReportingController.java`)
- **Authentication:** Authenticated; permission `report:export`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `reportKey` | string | No |  |
| `filter` | ReportFilter | No |  |
| `format` | ExportFormat | No |  |

**Example:**

```json
{
  "reportKey": "string",
  "filter": {
    "departmentId": "00000000-0000-7000-8000-000000000001",
    "branchId": "00000000-0000-7000-8000-000000000001",
    "categoryId": "00000000-0000-7000-8000-000000000001",
    "subcategoryId": "00000000-0000-7000-8000-000000000001",
    "requesterId": "00000000-0000-7000-8000-000000000001",
    "expenseType": "string",
    "status": null,
    "from": "2026-11-17T14:05:47Z",
    "to": "2026-11-17T14:05:47Z",
    "currency": "string",
    "search": "string"
  },
  "format": null
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `report:export`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## REQ-001 — GET /expense-requests

- **Module:** Requests (OPEX/CAPEX)
- **Purpose:** `list` on `/expense-requests`.
- **Controller / handler:** `ExpenseRequestController#list` (`src/main/java/src/main/java/com/edam/expenserequest/controller/ExpenseRequestController.java`)
- **Authentication:** Authenticated; permission `request:view-own`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `request:view-own`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## REQ-002 — GET /expense-requests/{id}

- **Module:** Requests (OPEX/CAPEX)
- **Purpose:** Fetch `{id}` by id.
- **Controller / handler:** `ExpenseRequestController#get` (`src/main/java/src/main/java/com/edam/expenserequest/controller/ExpenseRequestController.java`)
- **Authentication:** Authenticated; permission `request:view-own`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `request:view-own`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## REQ-003 — POST /expense-requests

- **Module:** Requests (OPEX/CAPEX)
- **Purpose:** Create a new `expense-requests`.
- **Controller / handler:** `ExpenseRequestController#create` (`src/main/java/src/main/java/com/edam/expenserequest/controller/ExpenseRequestController.java`)
- **Authentication:** Authenticated; permission `request:create`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `requestTypeId` | string (uuid) | Yes |  |
| `expenseCategoryId` | string (uuid) | Yes |  |
| `expenseSubcategoryId` | string (uuid) | No |  |
| `expenseNatureId` | string (uuid) | Yes |  |
| `departmentId` | string (uuid) | Yes |  |
| `branchId` | string (uuid) | No |  |
| `currencyId` | string (uuid) | Yes |  |
| `justification` | string | No | ≤4000 chars |
| `opex` | OpexDetailPayload | No |  |
| `capex` | CapexDetailPayload | No |  |
| `allocations` | AllocationPayload[] | No |  |
| `items` | ItemPayload[] | No |  |
| `quotations` | QuotationPayload[] | No |  |
| `invoices` | InvoicePayload[] | No |  |
| `previousSummaries` | PreviousSummaryPayload[] | No |  |
| `staDetails` | StaPayload[] | No |  |
| `budgetSummaries` | BudgetSummaryPayload[] | No |  |
| `documents` | DocumentReferencePayload[] | No |  |

**Example:**

```json
{
  "requestTypeId": "00000000-0000-7000-8000-000000000001",
  "expenseCategoryId": "00000000-0000-7000-8000-000000000001",
  "expenseSubcategoryId": "00000000-0000-7000-8000-000000000001",
  "expenseNatureId": "00000000-0000-7000-8000-000000000001",
  "departmentId": "00000000-0000-7000-8000-000000000001",
  "branchId": "00000000-0000-7000-8000-000000000001",
  "currencyId": "00000000-0000-7000-8000-000000000001",
  "justification": "string",
  "opex": {
    "recurring": true,
    "recurrenceFrequency": "string",
    "nonRecurringJustification": "string",
    "paymentTerms": "string",
    "budgetReference": "string",
    "threeQuotationsSelected": true,
    "alreadyApproved": true,
    "taxExempt": true,
    "grnRequired": true
  },
  "capex": {
    "requestType": "string",
    "budgeted": true,
    "unbudgetedReason": "string",
    "overBudgetReason": "string",
    "classification": "string",
    "warrantyStartDate": "2026-11-17",
    "warrantyEndDate": "2026-11-17",
    "purchaseNature": "string",
    "financeComment": "string",
    "budgetComment": "string",
    "termsAndConditions": "string"
  },
  "allocations": [],
  "items": [],
  "quotations": [],
  "invoices": [],
  "previousSummaries": [],
  "staDetails": [],
  "budgetSummaries": [],
  "documents": []
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `request:create`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## REQ-004 — PUT /expense-requests/{id}

- **Module:** Requests (OPEX/CAPEX)
- **Purpose:** Update an existing `{id}`.
- **Controller / handler:** `ExpenseRequestController#?` (`src/main/java/src/main/java/com/edam/expenserequest/controller/ExpenseRequestController.java`)
- **Authentication:** Authenticated; permission `request:edit`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `request:edit`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## REQ-005 — POST /expense-requests/{id}/submit

- **Module:** Requests (OPEX/CAPEX)
- **Purpose:** `?` on `/expense-requests/{id}/submit`.
- **Controller / handler:** `ExpenseRequestController#?` (`src/main/java/src/main/java/com/edam/expenserequest/controller/ExpenseRequestController.java`)
- **Authentication:** Authenticated; permission `request:submit`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `request:submit`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## REQ-006 — POST /expense-requests/{id}/cancel

- **Module:** Requests (OPEX/CAPEX)
- **Purpose:** `?` on `/expense-requests/{id}/cancel`.
- **Controller / handler:** `ExpenseRequestController#?` (`src/main/java/src/main/java/com/edam/expenserequest/controller/ExpenseRequestController.java`)
- **Authentication:** Authenticated; permission `request:cancel`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `request:cancel`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ROL-001 — GET /roles

- **Module:** Roles & Permissions
- **Purpose:** `list` on `/roles`.
- **Controller / handler:** `RoleController#list` (`src/main/java/src/main/java/com/edam/rolemanagement/controller/RoleController.java`)
- **Authentication:** Authenticated; permission `role:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `role:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ROL-002 — GET /roles/permissions

- **Module:** Roles & Permissions
- **Purpose:** `catalogue` on `/roles/permissions`.
- **Controller / handler:** `RoleController#catalogue` (`src/main/java/src/main/java/com/edam/rolemanagement/controller/RoleController.java`)
- **Authentication:** Authenticated; permission `role:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `role:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ROL-003 — GET /roles/{id}

- **Module:** Roles & Permissions
- **Purpose:** Fetch `{id}` by id.
- **Controller / handler:** `RoleController#get` (`src/main/java/src/main/java/com/edam/rolemanagement/controller/RoleController.java`)
- **Authentication:** Authenticated; permission `role:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `role:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ROL-004 — POST /roles

- **Module:** Roles & Permissions
- **Purpose:** Create a new `roles`.
- **Controller / handler:** `RoleController#create` (`src/main/java/src/main/java/com/edam/rolemanagement/controller/RoleController.java`)
- **Authentication:** Authenticated; permission `role:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | Yes | ≤100 chars |
| `description` | string | No | ≤2000 chars |

**Example:**

```json
{
  "name": "string",
  "description": "string"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `role:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ROL-005 — PUT /roles/{id}

- **Module:** Roles & Permissions
- **Purpose:** Update an existing `{id}`.
- **Controller / handler:** `RoleController#update` (`src/main/java/src/main/java/com/edam/rolemanagement/controller/RoleController.java`)
- **Authentication:** Authenticated; permission `role:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | Yes | ≤100 chars |
| `description` | string | No | ≤2000 chars |

**Example:**

```json
{
  "name": "string",
  "description": "string"
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `role:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ROL-006 — POST /roles/{id}/activation

- **Module:** Roles & Permissions
- **Purpose:** `setActive` on `/roles/{id}/activation`.
- **Controller / handler:** `RoleController#setActive` (`src/main/java/src/main/java/com/edam/rolemanagement/controller/RoleController.java`)
- **Authentication:** Authenticated; permission `role:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `active` | boolean | No | — | — |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `role:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## ROL-007 — PUT /roles/{id}/permissions

- **Module:** Roles & Permissions
- **Purpose:** Update an existing `permissions`.
- **Controller / handler:** `RoleController#setPermissions` (`src/main/java/src/main/java/com/edam/rolemanagement/controller/RoleController.java`)
- **Authentication:** Authenticated; permission `role:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `permissionCodes` | string[] | Yes |  |

**Example:**

```json
{
  "permissionCodes": []
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `role:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## SMT-001 — GET /

- **Module:** SMTP
- **Purpose:** `get` on `/`.
- **Controller / handler:** `SmtpConfigurationController#get` (`src/main/java/src/main/java/com/edam/communication/controller/SmtpConfigurationController.java`)
- **Authentication:** Authenticated; permission `email-configuration:manage`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `email-configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## SMT-002 — POST /

- **Module:** SMTP
- **Purpose:** Create a new `SmtpConfiguration`.
- **Controller / handler:** `SmtpConfigurationController#save` (`src/main/java/src/main/java/com/edam/communication/controller/SmtpConfigurationController.java`)
- **Authentication:** Authenticated; permission `email-configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `host` | string | Yes | ≤255 chars |
| `port` | number (int) | No | min=?; max=? |
| `username` | string | No | ≤255 chars |
| `password` | string | No |  |
| `securityMode` | string | No | pattern |
| `fromEmail` | string | Yes | email; ≤255 chars |
| `fromDisplayName` | string | No | ≤255 chars |
| `replyTo` | string | No | email; ≤255 chars |
| `connectionTimeoutMs` | number (int) | No | min=?; max=? |
| `readTimeoutMs` | number (int) | No | min=?; max=? |
| `active` | boolean | No |  |

**Example:**

```json
{
  "host": "string",
  "port": 1,
  "username": "j.doe",
  "password": "(redacted)",
  "securityMode": "string",
  "fromEmail": "user@example.com",
  "fromDisplayName": "string",
  "replyTo": "string",
  "connectionTimeoutMs": 1,
  "readTimeoutMs": 1,
  "active": true
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `email-configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## SMT-003 — DELETE /active

- **Module:** SMTP
- **Purpose:** Delete `active`.
- **Controller / handler:** `SmtpConfigurationController#deactivate` (`src/main/java/src/main/java/com/edam/communication/controller/SmtpConfigurationController.java`)
- **Authentication:** Authenticated; permission `email-configuration:manage`
- **CSRF:** Required
- **Content-Type:** —
- **Success status:** 204

### Request body
None.


### Success response (204)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `email-configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## SMT-004 — POST /test

- **Module:** SMTP
- **Purpose:** Create a new `test`.
- **Controller / handler:** `SmtpConfigurationController#test` (`src/main/java/src/main/java/com/edam/communication/controller/SmtpConfigurationController.java`)
- **Authentication:** Authenticated; permission `email-configuration:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `email-configuration:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## SYS-001 — GET /info

- **Module:** System
- **Purpose:** Public deployment metadata (application name, version, UP status).
- **Controller / handler:** `SystemController#info` (`src/main/java/src/main/java/com/edam/web/SystemController.java`)
- **Authentication:** Public
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## USR-001 — GET /users

- **Module:** Users
- **Purpose:** `list` on `/users`.
- **Controller / handler:** `UserController#list` (`src/main/java/src/main/java/com/edam/usermanagement/controller/UserController.java`)
- **Authentication:** Authenticated; permission `user:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `user:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## USR-002 — GET /users/search

- **Module:** Users
- **Purpose:** `search` on `/users/search`.
- **Controller / handler:** `UserController#search` (`src/main/java/src/main/java/com/edam/usermanagement/controller/UserController.java`)
- **Authentication:** Authenticated; permission `user:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `user:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## USR-003 — GET /users/{id}

- **Module:** Users
- **Purpose:** Fetch `{id}` by id.
- **Controller / handler:** `UserController#get` (`src/main/java/src/main/java/com/edam/usermanagement/controller/UserController.java`)
- **Authentication:** Authenticated; permission `user:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `user:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## USR-004 — POST /users

- **Module:** Users
- **Purpose:** Create a new `users`.
- **Controller / handler:** `UserController#create` (`src/main/java/src/main/java/com/edam/usermanagement/controller/UserController.java`)
- **Authentication:** Authenticated; permission `user:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `username` | string | Yes | ≤100 chars |
| `firstName` | string | Yes | ≤100 chars |
| `lastName` | string | Yes | ≤100 chars |
| `email` | string | Yes | email; ≤255 chars |
| `employeeCode` | string | No | ≤50 chars |
| `designation` | string | No | ≤150 chars |
| `departmentId` | string (uuid) | No |  |
| `branchId` | string (uuid) | No |  |
| `managerId` | string (uuid) | No |  |
| `roleIds` | string (uuid)[] | No |  |

**Example:**

```json
{
  "username": "j.doe",
  "firstName": "string",
  "lastName": "string",
  "email": "user@example.com",
  "employeeCode": "string",
  "designation": "string",
  "departmentId": "00000000-0000-7000-8000-000000000001",
  "branchId": "00000000-0000-7000-8000-000000000001",
  "managerId": "00000000-0000-7000-8000-000000000001",
  "roleIds": []
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `user:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## USR-005 — PUT /users/{id}

- **Module:** Users
- **Purpose:** Update an existing `{id}`.
- **Controller / handler:** `UserController#update` (`src/main/java/src/main/java/com/edam/usermanagement/controller/UserController.java`)
- **Authentication:** Authenticated; permission `user:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `firstName` | string | Yes | ≤100 chars |
| `lastName` | string | Yes | ≤100 chars |
| `email` | string | Yes | email; ≤255 chars |
| `employeeCode` | string | No | ≤50 chars |
| `designation` | string | No | ≤150 chars |
| `departmentId` | string (uuid) | No |  |
| `branchId` | string (uuid) | No |  |
| `managerId` | string (uuid) | No |  |

**Example:**

```json
{
  "firstName": "string",
  "lastName": "string",
  "email": "user@example.com",
  "employeeCode": "string",
  "designation": "string",
  "departmentId": "00000000-0000-7000-8000-000000000001",
  "branchId": "00000000-0000-7000-8000-000000000001",
  "managerId": "00000000-0000-7000-8000-000000000001"
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `user:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## USR-006 — POST /users/{id}/activation

- **Module:** Users
- **Purpose:** `setActive` on `/users/{id}/activation`.
- **Controller / handler:** `UserController#setActive` (`src/main/java/src/main/java/com/edam/usermanagement/controller/UserController.java`)
- **Authentication:** Authenticated; permission `user:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `active` | boolean | No | — | — |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `user:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## USR-007 — GET /users/{id}/roles

- **Module:** Users
- **Purpose:** Fetch `roles` by id.
- **Controller / handler:** `UserController#roles` (`src/main/java/src/main/java/com/edam/usermanagement/controller/UserController.java`)
- **Authentication:** Authenticated; permission `user:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `user:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## USR-008 — POST /users/{id}/roles

- **Module:** Users
- **Purpose:** `assignRole` on `/users/{id}/roles`.
- **Controller / handler:** `UserController#assignRole` (`src/main/java/src/main/java/com/edam/usermanagement/controller/UserController.java`)
- **Authentication:** Authenticated; permission `user-role:assign`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `roleId` | string (uuid) | Yes |  |
| `expiresAt` | string (ISO-8601 instant, UTC) | No |  |

**Example:**

```json
{
  "roleId": "00000000-0000-7000-8000-000000000001",
  "expiresAt": "2026-11-17T14:05:47Z"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `user-role:assign`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## USR-009 — DELETE /users/{id}/roles/{assignmentId}

- **Module:** Users
- **Purpose:** Delete `{assignmentId}`.
- **Controller / handler:** `UserController#revokeRole` (`src/main/java/src/main/java/com/edam/usermanagement/controller/UserController.java`)
- **Authentication:** Authenticated; permission `user-role:assign`
- **CSRF:** Required
- **Content-Type:** —
- **Success status:** 204

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |
| `assignmentId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (204)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `user-role:assign`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## USR-010 — POST /users/{id}/scopes

- **Module:** Users
- **Purpose:** `assignScope` on `/users/{id}/scopes`.
- **Controller / handler:** `UserController#assignScope` (`src/main/java/src/main/java/com/edam/usermanagement/controller/UserController.java`)
- **Authentication:** Authenticated; permission `data-scope:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `scopeType` | string | Yes |  |
| `referenceId` | string (uuid) | No |  |

**Example:**

```json
{
  "scopeType": "string",
  "referenceId": "00000000-0000-7000-8000-000000000001"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `data-scope:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## USR-011 — DELETE /users/{id}/scopes/{scopeId}

- **Module:** Users
- **Purpose:** Delete `{scopeId}`.
- **Controller / handler:** `UserController#removeScope` (`src/main/java/src/main/java/com/edam/usermanagement/controller/UserController.java`)
- **Authentication:** Authenticated; permission `data-scope:manage`
- **CSRF:** Required
- **Content-Type:** —
- **Success status:** 204

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |
| `scopeId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (204)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `data-scope:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## USR-012 — POST /users/{id}/password-reset

- **Module:** Users
- **Purpose:** `triggerPasswordReset` on `/users/{id}/password-reset`.
- **Controller / handler:** `UserController#triggerPasswordReset` (`src/main/java/src/main/java/com/edam/usermanagement/controller/UserController.java`)
- **Authentication:** Authenticated; permission `user:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `user:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## USR-013 — POST /users/{id}/unlock

- **Module:** Users
- **Purpose:** `unlock` on `/users/{id}/unlock`.
- **Controller / handler:** `UserController#unlock` (`src/main/java/src/main/java/com/edam/usermanagement/controller/UserController.java`)
- **Authentication:** Authenticated; permission `user:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `user:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## USR-014 — GET /users/{id}/effective-permissions

- **Module:** Users
- **Purpose:** Fetch `effective-permissions` by id.
- **Controller / handler:** `UserController#effectivePermissions` (`src/main/java/src/main/java/com/edam/usermanagement/controller/UserController.java`)
- **Authentication:** Authenticated; permission `user:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `id` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `user:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## WF-001 — GET /workflows

- **Module:** Workflow Configuration
- **Purpose:** `list` on `/workflows`.
- **Controller / handler:** `WorkflowConfigurationController#list` (`src/main/java/src/main/java/com/edam/workflow/controller/WorkflowConfigurationController.java`)
- **Authentication:** Authenticated; permission `workflow:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `workflow:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## WF-002 — POST /workflows

- **Module:** Workflow Configuration
- **Purpose:** Create a new `workflows`.
- **Controller / handler:** `WorkflowConfigurationController#create` (`src/main/java/src/main/java/com/edam/workflow/controller/WorkflowConfigurationController.java`)
- **Authentication:** Authenticated; permission `workflow:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | Yes | ≤64 chars |
| `name` | string | Yes | ≤200 chars |
| `description` | string | No | ≤4000 chars |
| `requestTypeId` | string (uuid) | No |  |

**Example:**

```json
{
  "code": "string",
  "name": "string",
  "description": "string",
  "requestTypeId": "00000000-0000-7000-8000-000000000001"
}
```

### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `workflow:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## WF-003 — PUT /workflows/{definitionId}

- **Module:** Workflow Configuration
- **Purpose:** Update an existing `{definitionId}`.
- **Controller / handler:** `WorkflowConfigurationController#update` (`src/main/java/src/main/java/com/edam/workflow/controller/WorkflowConfigurationController.java`)
- **Authentication:** Authenticated; permission `workflow:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `definitionId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | Yes | ≤64 chars |
| `name` | string | Yes | ≤200 chars |
| `description` | string | No | ≤4000 chars |
| `requestTypeId` | string (uuid) | No |  |

**Example:**

```json
{
  "code": "string",
  "name": "string",
  "description": "string",
  "requestTypeId": "00000000-0000-7000-8000-000000000001"
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `workflow:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## WF-004 — POST /workflows/{definitionId}/active

- **Module:** Workflow Configuration
- **Purpose:** Create a new `active`.
- **Controller / handler:** `WorkflowConfigurationController#setActive` (`src/main/java/src/main/java/com/edam/workflow/controller/WorkflowConfigurationController.java`)
- **Authentication:** Authenticated; permission `workflow:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `definitionId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `active` | boolean | No | — | — |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `workflow:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## WF-005 — GET /workflows/{definitionId}/versions

- **Module:** Workflow Configuration
- **Purpose:** `versions` on `/workflows/{definitionId}/versions`.
- **Controller / handler:** `WorkflowConfigurationController#versions` (`src/main/java/src/main/java/com/edam/workflow/controller/WorkflowConfigurationController.java`)
- **Authentication:** Authenticated; permission `workflow:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `definitionId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `workflow:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## WF-006 — GET /workflows/versions/{versionId}

- **Module:** Workflow Configuration
- **Purpose:** `version` on `/workflows/versions/{versionId}`.
- **Controller / handler:** `WorkflowConfigurationController#version` (`src/main/java/src/main/java/com/edam/workflow/controller/WorkflowConfigurationController.java`)
- **Authentication:** Authenticated; permission `workflow:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `versionId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `workflow:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## WF-007 — PUT /workflows/{definitionId}/draft

- **Module:** Workflow Configuration
- **Purpose:** Update an existing `draft`.
- **Controller / handler:** `WorkflowConfigurationController#saveDraft` (`src/main/java/src/main/java/com/edam/workflow/controller/WorkflowConfigurationController.java`)
- **Authentication:** Authenticated; permission `workflow:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `definitionId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `steps` | StepRequest[] | Yes | min=? |
| `transitions` | TransitionRequest[] | No |  |
| `states` | StateRequest[] | No |  |

**Example:**

```json
{
  "steps": [],
  "transitions": [],
  "states": []
}
```

### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `workflow:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## WF-008 — POST /workflows/{definitionId}/draft/from-published

- **Module:** Workflow Configuration
- **Purpose:** Create a new `from-published`.
- **Controller / handler:** `WorkflowConfigurationController#clonePublished` (`src/main/java/src/main/java/com/edam/workflow/controller/WorkflowConfigurationController.java`)
- **Authentication:** Authenticated; permission `workflow:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `definitionId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `workflow:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## WF-009 — POST /workflows/{definitionId}/draft/from-version/{sourceVersionId}

- **Module:** Workflow Configuration
- **Purpose:** Create a new `{sourceVersionId}`.
- **Controller / handler:** `WorkflowConfigurationController#rollbackIntoDraft` (`src/main/java/src/main/java/com/edam/workflow/controller/WorkflowConfigurationController.java`)
- **Authentication:** Authenticated; permission `workflow:manage`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 201

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `definitionId` | string (uuid) unless noted | Yes | Substituted into the URL. |
| `sourceVersionId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (201)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `workflow:manage`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## WF-010 — GET /workflows/versions/compare

- **Module:** Workflow Configuration
- **Purpose:** `compare` on `/workflows/versions/compare`.
- **Controller / handler:** `WorkflowConfigurationController#compare` (`src/main/java/src/main/java/com/edam/workflow/controller/WorkflowConfigurationController.java`)
- **Authentication:** Authenticated; permission `workflow:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `from` | string (uuid) | No | — | — |
| `to` | string (uuid) | No | — | — |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `workflow:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## WF-011 — GET /workflows/versions/{versionId}/validate

- **Module:** Workflow Configuration
- **Purpose:** `validate` on `/workflows/versions/{versionId}/validate`.
- **Controller / handler:** `WorkflowConfigurationController#validate` (`src/main/java/src/main/java/com/edam/workflow/controller/WorkflowConfigurationController.java`)
- **Authentication:** Authenticated; permission `workflow:view`
- **CSRF:** Not required
- **Content-Type:** —
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `versionId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `workflow:view`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## WF-012 — POST /workflows/versions/{versionId}/publish

- **Module:** Workflow Configuration
- **Purpose:** Create a new `publish`.
- **Controller / handler:** `WorkflowConfigurationController#publish` (`src/main/java/src/main/java/com/edam/workflow/controller/WorkflowConfigurationController.java`)
- **Authentication:** Authenticated; permission `workflow:publish`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `versionId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `workflow:publish`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.


## WF-013 — POST /workflows/versions/{versionId}/retire

- **Module:** Workflow Configuration
- **Purpose:** Create a new `retire`.
- **Controller / handler:** `WorkflowConfigurationController#retire` (`src/main/java/src/main/java/com/edam/workflow/controller/WorkflowConfigurationController.java`)
- **Authentication:** Authenticated; permission `workflow:publish`
- **CSRF:** Required
- **Content-Type:** application/json
- **Success status:** 200

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `versionId` | string (uuid) unless noted | Yes | Substituted into the URL. |

### Request body
None.


### Success response (200)

- Content-Type: `application/json`; envelope: `ApiResponse<?>` (see §03).
- `data` shape depends on handler return type (`?`); see source traceability (§18).

### Common error statuses

| Status | Code | When |
|---|---|---|
| 401 | `EDAM-2001 UNAUTHENTICATED` | No session or session expired. |
| 403 | `EDAM-2005 PERMISSION_DENIED` | Caller lacks `workflow:publish`. |
| 400 | `EDAM-1001 VALIDATION_FAILED` / `EDAM-1002 INVALID_REQUEST` | Malformed payload or Bean Validation failures (`fieldErrors` populated). |
| 404 | `EDAM-4001 NOT_FOUND` | Path id does not exist or is not visible to the caller. |
| 409 | `EDAM-3002 CONCURRENT_MODIFICATION` / `EDAM-3004 INVALID_STATE_TRANSITION` / `EDAM-5002 DUPLICATE_RESOURCE` | Optimistic-lock / state-machine / unique-key conflict. |
| 403 | CSRF token missing or stale | `EDAM-2002 ACCESS_DENIED` (CSRF failure surfaces as a 403 with a specific reason; reload page). |
| 500 | `EDAM-9001 INTERNAL_ERROR` | Unexpected failure; quote `correlationId` to support.
