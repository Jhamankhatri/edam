# 17 — Open Decisions & Frontend Blockers

Open decisions are preserved verbatim in `docs/phase-4j/open-decisions.md` and `docs/phase-4j/BRD_COMPLIANCE_MATRIX.md`. This document summarises how each one affects the frontend and what the UI should do until it is resolved.

**Settled architecture decision (must not be revisited):** every bank runs an independent EDAM deployment and PostgreSQL database. There is no bank/tenant switcher, no cross-bank access and no multi-tenant selector in the UI.

## 17.1 Settled architectural decisions (NOT open)

These are recorded here because earlier phase documents marked them "open"; they are **settled** for v1 and must not be revisited by the frontend.

| ID | Decision | Settled behaviour | Frontend must do |
|---|---|---|---|

## 17.2 Open product decisions that the frontend must not silently "design around"

| ID | Issue | Actual backend behaviour today | Frontend impact | Frontend may proceed? | Owner |
|---|---|---|---|---|---|
| OD-02 / OD-03 | CEO/CFO/HOD/EC routing order and standalone routes | The workflow engine supports configurable stages/approvers/quorum but ships no pre-seeded CEO/CFO route. Each bank must publish their own workflow. | Workflow Administration screen must be complete before approvals flow end-to-end for routes beyond simple HOD/recommending-authority chains. Provide a clear empty/help state explaining that routing configuration is required. | **Yes, build UI**; ship with sample/reference workflow template in documentation only — do not hardcode routes. | Business owner + Finance |
| OD-04 | CFO action set (approve/reject/revert/forward/remarks rules) | The engine permits all four actions to any eligible approver of a step; no CFO-specific restriction is coded. | Role configuration and step-level remark-required flags must be set by the administrator. Do not hard-code a different button set for CFO. | Yes; rely on permissions/workflow config. | Business + Finance |
| OD-01 | Narrative EC levels 1–3 vs configurable approval levels | Committees/levels are modelled; EC narrative levels are not mapped. | Do not render "EC Level 1/2/3" labels unless a committee is configured to represent them. Use level names from the workflow/committee data. | Yes. | Business owner |
| OD-05 | Exceeding-limit rule enforcement | `exceeding-limit-enforced-in-routing=false` default (warn only). Exceeding-limit can be evaluated via API. | Show a warning banner when evaluation flags the request; do not block submission unless the configuration flag is true (surface the flag in admin UI). | Yes (warn-only by default). | Business owner |
| OD-06 | Department Head threshold granularity | Single nullable threshold per department with documented fallback. | UI reflects single threshold per department; add help text about fallback. | Yes. | Finance |
| OD-07 | PKR conversion formula / rounding / rate direction | Rates stored; no arithmetic performed anywhere. | Do not auto-convert amounts client-side and assume parity with backend. Display amounts in their submitted currency; if the institution wants PKR totals, flag as "pending finance sign-off". | Partial; do not invent math. | Finance |
| OD-08 | CAPEX item-wise amount and variance formulas | Values stored; no arithmetic. | CAPEX fields are accepted but not auto-computed. Render them as entered; flag variance as "not auto-calculated" until sign-off. | Yes (data entry only). | Finance |
| OD-09 | Complete request status state machine (reopen, etc.) | States DRAFT/SUBMITTED/IN_PROGRESS/RETURNED/APPROVED/REJECTED/CANCELLED/REVERSED are implemented (§07). Reopen not implemented. | Do not offer a Reopen button. | Yes. | Business owner |
| OD-10 | Selective-revert eligibility | Revert returns to DRAFT from any current task; caller must be an eligible approver (no role-specific restrictions beyond that in Phase 4J). | Show revert on the action panel for eligible approvers (per workflow config); require remarks. | Yes. | Business owner |
| OD-11 | SSO / AD / OIDC / SAML | Local authentication only (ADR-0004 seam). | Login form only; no "Login with SSO" buttons. | Yes (local auth only). | InfoSec + IT |
| OD-12 | Role → permission → data-scope matrix | Permission constants defined; no pre-seeded roles beyond what bootstrap creates. | Ship no default roles except SYSTEM_ADMINISTRATOR (granted at bootstrap). Customer-specific roles are created in the Roles admin screen. | Yes. | Business owner + InfoSec |
| OD-13 | CAPEX finance-field owner (requester vs finance approver) | Fields are writable by anyone with `request:edit`; workflow does not have a finance-only step by default. | Render CAPEX finance fields on the request form. If the customer configures a finance-approval step, the same fields are visible (read-only) at that step; do not hide them from the requester without config. | Yes. | Finance |
| OD-14 | Malware-scanner provider / retention | `MalwareScanner` port exists; download is fail-closed (only CLEAN downloadable). Only `FakeMalwareScanner` exists in tests. | Show scan-status badges; block download until CLEAN; if scanner is unavailable, display "Scanner not configured" instead of stale PENDING_SCAN. Upload UX should warn "Production scanner not yet provisioned" until the environment sets one. | Yes, but go-live blocker until scanner is provisioned. | InfoSec + IT |
| OD-15 | Email event matrix / SMS / push | SMTP config, templates and per-event toggles exist; not every business event has a default template. | Render event list from API; only enabled events produce notifications. Do not promise SMS/push. | Yes. | Business + IT |
| OD-16 | HR/AD sync for users/departments/branches | Users/departments/branches are maintained locally via the admin UI. | Do not build sync UI. | Yes. | IT + Business |
| OD-17 | EC member-pool vs committee table reconciliation | Committees/levels/members modelled; legacy "EC pool" tables are not present. | Use committees/levels as the source of truth; do not render a separate "EC pool" screen. | Yes. | Business + DBA |
| OD-18 | Legacy data migration | Not started. | N/A — out of scope for the React MVP. | N/A. | IT |
| OD-19 | NFR/SLA numeric targets | Foundation is NFR-neutral. | No performance SLA UI or SLO dashboards in v1. | Yes. | Business + IT + InfoSec |
| OD-21 | CAPEX allowed-department list and Department Head fallback are configuration | Configuration flags with conservative defaults. | Respect configuration flags; do not hard-code allowed departments. | Yes. | Business owner |
| OD-22 | Print/PDF, XLSX, export formats and configuration-log retention | CSV export implemented; XLSX/PDF/print not implemented. | Only expose CSV export; hide/disable PDF/print buttons. | Yes (CSV only). | Business owner |

## 17.3 Production blockers (configure before go-live)
1. **PostgreSQL 15+** with backups and PITR.
2. **HTTPS reverse proxy** with proper `X-Forwarded-Proto` and CORS exact-origin for the React host.
3. **SMTP server** (forgot-password, approvals, notifications).
4. **Object storage** (Cloudinary-compatible) for document uploads.
5. **Malware scanner** (before enabling user uploads).
6. **Secure provisioning** of `EDAM_BOOTSTRAP_TOKEN`, `EDAM_MAIL_MASTER_KEY`, `EDAM_INTEGRATION_MASTER_KEY`; rotate bootstrap token after first admin setup.
7. **Workflow published for every request type** (OPEX, CAPEX) before submit is enabled for end-users.
8. **Role matrix** created per the institution's SoD policy (OD-12).

## 17.4 Features that should NOT appear in v1 UI
- JWT/bearer-token login screen.
- Bank/tenant switcher.
- Print/PDF, XLSX export (wait for backend support).
- Hard-coded CEO/CFO/EC level routes.
- Arbitrary CSS/JS input in theming (tokens are whitelisted).
- Direct Cloudinary URLs (always go via signed-URL endpoint).
- Any screen that lets an admin view or type SMTP/integration passwords back in (they are write-only).
- Real-time websocket notifications (poll every 30 s).
