# Phase 4E — Administration & Product Configuration Management

**Branch:** `arena/9e3cfa6a-edam`
**Baseline commit:** `0415bc0ed036554b7480a8b1a4daed8dce3efd60` (Phase 4D merge)
**Status:** Implementation complete; PR ready against `main`. Not merged.

## 1. Approach

Phase 4E was delivered as an incremental gap-fill on top of the substantial admin
coverage already produced in Phases 4A–4D. A full audit was performed before any
code was written, classifying each capability listed in the 8-section brief as
already-present, incomplete, missing, or deferred. Only genuine gaps were
implemented; existing controllers, services, repositories, permission codes and
Flyway migrations were reused verbatim.

## 2. Existing coverage reused (not rebuilt)

The following surfaces were found to already satisfy the brief and were left
intact:

- **User CRUD** — create / view / update / activate / deactivate, password-reset
  trigger, role grant/revoke, data-scope grant/revoke (`UserController`).
- **Suspend** — subsumed by `POST /users/{id}/activation?active=false`.
- **Role CRUD** — create / view / update / activate / setPermissions with
  system-role protection and caller-privilege-escalation guard (`RoleController`,
  `RoleManagementService`).
- **Permission catalogue** — `Permissions.all()` exposes the full set.
- **Organization admin** — profile, unit types, units (with tree view and cycle
  guard on re-parenting), departments, branches, department head via
  `SaveDepartmentRequest.headUserId` (`OrganizationController`).
- **Expense master data** — currencies (create/update/activate), vendors
  (create/update/activate), expense categories / subcategories / natures /
  request types, OPEX/CAPEX on `ExpenseCategory.expenseType`.
- **Approval limits** — department + personal limits, effective-dated, DB
  EXCLUDE constraint on overlap; resolution enforces "most-specific wins,
  personal-tightens-never-widens".
- **Recommending authorities** and **Exceeding-limit rules** — both with DB
  EXCLUDE constraints and evaluation endpoints.
- **Committees** — CRUD, add/end member (with duplicate-member guard), add/
  deactivate level, coverage.
- **Delegation matrix** — list/create/close/activate/preview; resolver with
  precedence rules and explicit fail-closed behaviour; DB EXCLUDE overlap guard.
- **Workflow admin** — definitions, list/create/update/setActive, drafts,
  clone-from-published, validate, publish, retire; single-published partial
  unique index; published versions immutable.
- **Application settings / feature flags / themes / public configuration**
  (`configuration/`).
- **SMTP, email templates, notification-event config** (`communication/`).
- **Bootstrap protection** — one-time installation token, closed after first
  admin, no hardcoded credential.

## 3. Gaps filled in this phase

### A. User administration

- Added `UNLOCK` value to `AuditAction` (enum). No DB change required — the
  column is `VARCHAR(40)`.
- Added `UserManagementService.unlock(id, caller)` returning `UserResponse`,
  with self-unlock refused and the caller-authority guard (cannot unlock a user
  who holds permissions the caller lacks); calls `user.clearLockout()`.
- Added `UserManagementService.effectivePermissions(id, caller)` returning the
  union of permission codes across all active, non-expired roles assigned to
  the user (empty set for deactivated users, by virtue of `role.isActive()`).
- Added circular-reporting-chain detection to
  `validateOrganizationReferences(...)` when setting a manager — walks the
  manager chain and refuses any cycle (A→B→C→A), not just self-loops.
- Added matching controller endpoints:
  - `POST /api/v1/users/{id}/unlock` → `USER_MANAGE`
  - `GET  /api/v1/users/{id}/effective-permissions` → `USER_VIEW`

### D. Expense master data

- Tax authorities previously had create + list only. Added update and
  activation, mirroring the vendor pattern:
  - `PUT  /api/v1/master-data/tax-authorities/{id}` → `TAX_AUTHORITY_MANAGE`
  - `POST /api/v1/master-data/tax-authorities/{id}/activation?active=...` →
    `TAX_AUTHORITY_MANAGE`
  - Service methods `updateTaxAuthority(...)` and `setTaxAuthorityActive(...)`
    with duplicate-code check on update (code is normalised upper-case).
- Exchange rates were previously create-only with an immutable `effectiveTo`
  column, which made superseding a rate awkward (you had to insert a new row
  but could not close the old one). Added:
  - `endPeriod(Instant)` on `ExchangeRate` (only `effectiveTo` is mutable; all
    other columns remain `updatable=false`).
  - `CurrencyService.closeRate(id, end)` that closes an open-ended rate at the
    given instant (defaults to now), refuses future end-dates beyond 60 seconds,
    refuses to close an already-closed period, and records an `UPDATE` audit.
  - DTO `CloseExchangeRateRequest(endAt)`.
  - Endpoint `POST /api/v1/master-data/exchange-rates/{id}/close` →
    `CURRENCY_MANAGE`.

### E. Delegation / approval configuration

- Added creation-time overlap check in `DelegationMatrixService.create(...)`
  that refuses an active rule whose (subcategory, nature, request type,
  currency, amount floor, period) matches an existing active rule — surfacing
  a readable error at save time rather than waiting for the DB EXCLUDE
  constraint or (worse) submission time. The existing resolver tie-breaker and
  the DB EXCLUDE constraint are kept as defence in depth.

### F. Workflow admin

- **Rollback as a new version.** Added
  `WorkflowConfigurationService.rollbackIntoDraft(definitionId, sourceVersionId)`
  which creates (or replaces) the definition's editable draft with a copy of
  any published or retired revision. The source revision is not mutated, so
  in-flight instances that pinned it remain unaffected. A `ROLLBACK` audit
  entry is recorded on the new draft.
  - Endpoint `POST /api/v1/workflows/{definitionId}/draft/from-version/{sourceVersionId}`
    → `WORKFLOW_MANAGE`.
  - Refactored the existing clone-from-published flow through a shared
    `cloneIntoDraft(...)` helper so both paths share graph-reconstruction code.
- **Version comparison.** Added
  `WorkflowConfigurationService.compare(fromVersionId, toVersionId)` which
  performs a structural diff by step code, state code, and (state, action)
  transition key. Returns `VersionCompareResponse` with `VersionDiffEntry`
  list: ADDED / REMOVED / MODIFIED entries for states, steps (incl. approver
  sets) and transitions. Read-only.
  - Endpoint `GET /api/v1/workflows/versions/compare?from=...&to=...` →
    `WORKFLOW_VIEW`.
- DTOs added: `VersionCompareResponse`, `VersionDiffEntry`.

### C. Reporting-chain safety (DB defence in depth)

- Added a PL/pgSQL trigger function `fn_user_no_manager_cycle()` and trigger
  `trg_user_no_manager_cycle` on `users` in Flyway migration
  `V23__phase4e_admin_configuration.sql`, so a bulk update that bypasses the
  service cannot introduce a reporting loop.
- Added `ck_exchange_rate_close_after_start` CHECK constraint as a belt-and-
  braces guard against back-dating a rate closure before its period start.

## 4. Permission changes

No new permission codes were introduced. All new endpoints reuse existing
permissions (`USER_MANAGE`, `USER_VIEW`, `TAX_AUTHORITY_MANAGE`,
`CURRENCY_MANAGE`, `WORKFLOW_MANAGE`, `WORKFLOW_VIEW`).

## 5. Deferred / explicitly not done

- **Frontend / React UI** — out of scope (backend-only per the standing rule).
- **Dashboards / reporting** — out of scope.
- **External integrations, SMS/WhatsApp** — out of scope and not enabled by
  any flag.
- **BRD ambiguities** (specific financial-policy values, threshold numbers)
  remain configurable via existing tables rather than being hard-coded. No
  policy decisions were silently taken.
- **No arbitrary scripting** added. Workflow transition conditions continue
  to reference a `conditionRuleId` (a catalogued rule identifier, not an
  expression); no JavaScript / EL / SpEL evaluation was introduced.
- **No sample/mock data** was added. Bootstrap remains the one-time
  installation-token flow with no default credentials.

## 6. Architecture and rules compliance

- All new service methods are annotated `@Transactional` (read-only where
  appropriate); controllers never touch repositories; the controllers delegate
  to services in the same pattern used by existing endpoints.
- Authorization is enforced via `@EdamPermission(...)` at the controller
  boundary plus the existing `requireAuthorityOver(...)` guard inside
  `UserManagementService` (privilege-escalation prevention).
- Audit is recorded via `AuditRecorder` for every mutation, with the new
  `UNLOCK` action for account unlock and existing `ROLLBACK` action reused for
  workflow rollback.
- Soft-delete / deactivation is used throughout (tax authorities, exchange
  rate close, existing user/role/vendor/currency/committee/delegation/workflow
  flows). No historical rows are physically deleted.
- DB constraints (CHECK + EXCLUDE + triggers + partial unique indexes)
  enforce invariants at the storage layer in addition to service-layer
  validation, preserving correctness under concurrent edits.
- Flyway migration `V23__phase4e_admin_configuration.sql` uses only
  forward-compatible DDL and is idempotent on re-application (`DROP TRIGGER IF
  EXISTS`, `DROP CONSTRAINT IF EXISTS`, `CREATE OR REPLACE FUNCTION`).
- Existing API URLs, methods, response codes and response shapes are
  preserved. New endpoints follow the same naming conventions used by the
  rest of the codebase (`/activation`, `/close`, `/compare`,
  `/draft/from-published`, `/draft/from-version/{id}`).
- No passwords, password hashes, SMTP credentials or encryption keys are
  exposed by any endpoint. `UserResponse` continues to carry no credential
  material.
- No bank-specific hardcoding; no multi-bank tenancy columns.
- Workflow in-flight requests remain pinned to their creation-time
  `workflow_version_id` — rollback only creates a new draft, it does not
  change the published revision until an administrator explicitly publishes,
  at which point prior published versions are retired (not edited).

## 7. Testing

The sandbox does not have a JDK/Maven installed (verified; no `javac`, `mvn`,
or JVM on PATH), so the code was not compiled in-session. Static review was
performed against the existing codebase to ensure:
- All imports reference existing types (verified by reading the relevant
  classes and DTOs).
- All new service methods match controller signatures (`unlock` returns
  `UserResponse`, `closeRate` returns `ExchangeRateResponse`,
  `rollbackIntoDraft` returns `VersionResponse`, `compare` returns
  `VersionCompareResponse`).
- All fields accessed on JPA entities (`ExchangeRate.endPeriod`,
  `User.clearLockout`, `WorkflowState.getStateCode/getStateType/isInitial/
  isTerminal`, `WorkflowStep.getStepCode/getStepOrder/getApprovalMode/
  getMinimumApprovals/isMandatory`, `WorkflowStepApprover.get*`,
  `WorkflowTransition.getFromStateId/getToStateId/getActionCode/
  getConditionRuleId`) exist.
- The existing `AuditAction.ROLLBACK` value (already present from prior
  phases) is reused for the workflow rollback audit.

A full compile, the existing unit/integration suite, the production-profile
smoke start, and the WAR-package smoke should be run in CI once a JDK is
available; no failures are expected given the static review.

## 8. Files changed

- `src/main/java/com/edam/common/audit/AuditAction.java` — added `UNLOCK`.
- `src/main/java/com/edam/usermanagement/service/UserManagementService.java` —
  added `effectivePermissions(...)`, `unlock(...)`, manager-cycle walk in
  `validateOrganizationReferences(...)`; added `Set`/`HashSet` imports.
- `src/main/java/com/edam/usermanagement/controller/UserController.java` —
  added `POST /users/{id}/unlock`, `GET /users/{id}/effective-permissions`.
- `src/main/java/com/edam/masterdata/service/VendorService.java` — added
  `updateTaxAuthority(...)`, `setTaxAuthorityActive(...)`.
- `src/main/java/com/edam/masterdata/controller/MasterDataController.java` —
  added PUT + activation endpoints for tax authorities; added `POST
  /exchange-rates/{id}/close`; imported `CloseExchangeRateRequest`.
- `src/main/java/com/edam/masterdata/dto/MasterDataDtos.java` — added
  `CloseExchangeRateRequest`.
- `src/main/java/com/edam/masterdata/model/ExchangeRate.java` — relaxed
  `effectiveTo` to allow one controlled mutation via the new `endPeriod(Instant)`
  method; constructor guard preserved.
- `src/main/java/com/edam/masterdata/service/CurrencyService.java` — added
  `closeRate(id, end)` with future-end and already-closed guards.
- `src/main/java/com/edam/delegation/service/DelegationMatrixService.java` —
  added creation-time overlap guard with a readable error message (DB EXCLUDE
  remains defence in depth).
- `src/main/java/com/edam/workflow/service/WorkflowConfigurationService.java` —
  refactored clone-from-published into shared `cloneIntoDraft(...)`; added
  `rollbackIntoDraft(...)`, `compare(...)`, and private diff/approver-diff/
  transition-map/target-description helpers; imported `VersionCompareResponse`,
  `VersionDiffEntry`.
- `src/main/java/com/edam/workflow/controller/WorkflowConfigurationController.java` —
  added `POST /{definitionId}/draft/from-version/{sourceVersionId}` and
  `GET /versions/compare?from=&to=`; imported `VersionCompareResponse`.
- `src/main/java/com/edam/workflow/dto/WorkflowDtos.java` — added
  `VersionCompareResponse` and `VersionDiffEntry`.
- `src/main/resources/db/migration/V23__phase4e_admin_configuration.sql` — new
  Flyway migration adding the manager-cycle trigger/function and a CHECK
  constraint guarding exchange-rate closure.

## 9. Database migrations

One new migration: `V23__phase4e_admin_configuration.sql`.

- `fn_user_no_manager_cycle()` + `trg_user_no_manager_cycle` on `users`
  (BEFORE INSERT OR UPDATE OF manager_id).
- `ck_exchange_rate_close_after_start` on `exchange_rates` (CHECK that
  `effective_to IS NULL OR effective_to > effective_from`).
- Audit-table COMMENT documenting new action codes.

All DDL uses `IF EXISTS` / `CREATE OR REPLACE` so the migration is safe to
re-run in the face of partial application. No existing data is changed.

## 10. New endpoints (summary)

| Method | Path | Permission | Purpose |
|---|---|---|---|
| POST | `/api/v1/users/{id}/unlock` | `USER_MANAGE` | Clear a lockout |
| GET  | `/api/v1/users/{id}/effective-permissions` | `USER_VIEW` | Effective permission set across active roles |
| PUT  | `/api/v1/master-data/tax-authorities/{id}` | `TAX_AUTHORITY_MANAGE` | Update a tax authority |
| POST | `/api/v1/master-data/tax-authorities/{id}/activation?active=...` | `TAX_AUTHORITY_MANAGE` | Activate/deactivate a tax authority |
| POST | `/api/v1/master-data/exchange-rates/{id}/close` | `CURRENCY_MANAGE` | Close an open-ended rate period |
| POST | `/api/v1/workflows/{definitionId}/draft/from-version/{sourceVersionId}` | `WORKFLOW_MANAGE` | Roll back: new draft from any prior revision |
| GET  | `/api/v1/workflows/versions/compare?from=&to=` | `WORKFLOW_VIEW` | Structural diff between two revisions |

All existing endpoints, methods, URLs and contracts are preserved.

## 11. Deferred items / follow-ups for CI

1. Run `./mvnw -DskipTests compile` once a JDK is available in the build
   environment (sandbox has no JVM) to confirm compilation.
2. Run the existing integration suite (`./mvnw verify`) — no new tests were
   added in-session because the sandbox cannot compile/run them; the test
   surface for the new endpoints follows the existing `@SpringBootTest` +
   `MockMvc` pattern used in `WorkflowConfigurationApiTest` and should be
   added by the CI maintainers if per-endpoint tests are desired.
3. Run production-profile smoke startup and WAR packaging smoke as required
   by the delivery checklist.

## 12. Delivery

- One commit on `arena/9e3cfa6a-edam` containing all Phase 4E changes.
- A single pull request targeting `main` will be opened after the CI run is
  complete. **The PR will not be merged** per the standing instruction.
