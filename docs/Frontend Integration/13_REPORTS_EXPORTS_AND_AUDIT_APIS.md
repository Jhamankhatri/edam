# 13 — Reports, Exports & Audit APIs

## 13.1 Dashboard
`GET /api/v1/reporting/dashboard` — permission `report:view`. Returns a structured dashboard payload: KPI cards (requests by status, requests by department, requests this month/last month, pending approvals by age, average cycle time), and a small recent-requests feed. The exact fields are defined by the `DashboardResponse` DTO in `reporting/dto/ReportingDtos.java`.

## 13.2 Widget / report catalogues (admin)
- `GET /api/v1/reporting/widgets` — `dashboard:manage`. List configurable widgets.
- `GET /api/v1/reporting/reports` — `dashboard:manage`. List configured report definitions.

## 13.3 Register
`GET /api/v1/reporting/expense-requests/register` — `report:view`. Paginated list of requests visible to the caller (broader than "my requests"), with optional filters: `status`, `type`, `departmentId`, `branchId`, `from`, `to`, `q`, `page`, `size`, `sort`. Returns `PageResponse<RequestRegisterRow>` where rows are denormalised for list display.

## 13.4 Export
`POST /api/v1/reporting/expense-requests/export` — `report:export`. Accepts the same filter payload as the register (as JSON body). Returns `200 OK` with `Content-Type: text/csv` (CSV export in Phase 4J) and `Content-Disposition: attachment; filename=…`. The frontend should trigger the download via a blob (or a same-origin form POST if cookie handling requires it).

> XLSX and PDF/print are **not** implemented (OD-22). Do not add UI for them.

## 13.5 Audit search
Base path `/api/v1/audit-events`; permission `audit:view`.

| Method | Path | Notes |
|---|---|---|
| GET | `/audit-events` | Paginated. Query filters: `actor`, `entityType`, `entityId`, `correlationId`, `category`, `from`, `to`, `page`, `size`, `sort`. Returns `PageResponse<AuditEventSummary>`. |
| GET | `/audit-events/{id}` | Full detail including a redacted before/after diff (`AuditEventDetail`). Secrets (SMTP, integration credentials) are redacted at write time by `AuditValueRedactor` — the UI does not need to additionally filter them. |

## 13.6 Request timeline
`GET /api/v1/expense-requests/{id}/activity` — permission `request:view-own` (service enforces visibility). Returns `TimelinePage` (chronological merged list of workflow actions, document events and audit events) for the activity tab on request detail.

## 13.7 Audit guarantees
- `audit_events` is append-only; a database trigger blocks UPDATE/DELETE outside the application path (V10/V25 migrations).
- Every state change (submit/approve/reject/revert/forward/cancel), every configuration save and every document mutation writes an audit event.
- Rejected values are redacted; correlation id links API responses to audit rows.

## 13.8 Not-yet-implemented
- Audit export (`audit:export` permission is reserved; no endpoint in Phase 4J).
- XLSX/PDF exports (OD-22).
- Scheduled/subscribed reports.
