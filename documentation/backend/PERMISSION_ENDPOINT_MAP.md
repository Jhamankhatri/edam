# Permission-to-endpoint map

Generated from the controllers by `tools/permission-map.py`, not maintained by hand. Every `@EdamPermission` below was read off a mapped method, and `EndpointAuthorizationArchitectureTest` fails the build if a mapped method carries no guard at all.

**130 mapped methods. 58 permission constants exist; 37 are consumed by an endpoint; 21 are not.** The unused ones are listed at the end with the module each belongs to, because an unused permission is not a defect — it is a module that has not been built yet.

## Phase 3 — workflow and approvals

These five were seeded in V12 and Phase 3 is their first consumer. No new permission was added and no migration was needed.

| Permission | Method | Path |
|---|---|---|
| `WORKFLOW_VIEW` | GET | `/workflows` |
| `WORKFLOW_VIEW` | GET | `/workflows/{definitionId}/versions` |
| `WORKFLOW_VIEW` | GET | `/workflows/versions/{versionId}` |
| `WORKFLOW_VIEW` | GET | `/workflows/versions/{versionId}/validate` |
| `WORKFLOW_MANAGE` | POST | `/workflows` |
| `WORKFLOW_MANAGE` | PUT | `/workflows/{definitionId}` |
| `WORKFLOW_MANAGE` | POST | `/workflows/{definitionId}/active` |
| `WORKFLOW_MANAGE` | PUT | `/workflows/{definitionId}/draft` |
| `WORKFLOW_MANAGE` | POST | `/workflows/{definitionId}/draft/from-published` |
| `WORKFLOW_PUBLISH` | POST | `/workflows/versions/{versionId}/publish` |
| `WORKFLOW_PUBLISH` | POST | `/workflows/versions/{versionId}/retire` |
| `APPROVAL_VIEW` | GET | `/approvals` |
| `APPROVAL_VIEW` | GET | `/approvals/count` |
| `APPROVAL_VIEW` | GET | `/approvals/tasks/{taskId}` |
| `APPROVAL_VIEW` | GET | `/approvals/tasks/{taskId}/request` |
| `APPROVAL_ACT` | POST | `/approvals/tasks/{taskId}/approve` |
| `APPROVAL_ACT` | POST | `/approvals/tasks/{taskId}/reject` |
| `APPROVAL_ACT` | POST | `/approvals/tasks/{taskId}/revert` |
| `APPROVAL_ACT` | POST | `/approvals/tasks/{taskId}/forward` |

### Why `workflow:publish` is separate from `workflow:manage`

Authoring a revision and making it live are different authorities. With one permission, whoever can draft a chain
can also put it into force, and the bank has no way to require a second pair of eyes on the configuration itself.
The split costs one permission and makes four-eyes review of the approval rules possible — which matters
precisely because those rules decide who may approve money.

### Why `approval:act` is not enough on its own

`approval:act` guards the four action endpoints, but holding it confers no authority over any particular task.
Every action additionally requires an open task naming the caller (`ApprovalService.requireAssigned`). The
endpoint permission answers "may this user act on approvals at all"; the assignment answers "may they act on
*this* one". A test that proved only the first would pass on an implementation that let anyone approve anything.

## All permissions in use

| Permission | Methods | Paths |
|---|---|---|
| `APPROVAL_ACT` | 4 | `/approvals/tasks/{taskId}/approve`, `/approvals/tasks/{taskId}/reject`, `/approvals/tasks/{taskId}/revert`, `/approvals/tasks/{taskId}/forward` |
| `APPROVAL_LIMIT_MANAGE` | 3 | `/approval-limits`, `/approval-limits/{id}`, `/approval-limits/{id}/activation` |
| `APPROVAL_LIMIT_VIEW` | 3 | `/approval-limits`, `/approval-limits/{id}`, `/approval-limits/authority-check` |
| `APPROVAL_VIEW` | 4 | `/approvals`, `/approvals/count`, `/approvals/tasks/{taskId}`, `/approvals/tasks/{taskId}/request` |
| `AUDIT_VIEW` | 2 | `/audit-events`, `/audit-events/{id}` |
| `COMMITTEE_MANAGE` | 7 | `/committees`, `/committees/{id}`, `/committees/{id}/deactivate`, `/committees/{id}/members`, `/committees/{id}/members/{memberId}/end`, `/committees/{id}/levels`, `/committees/{id}/levels/{levelId}/deactivate` |
| `COMMITTEE_VIEW` | 3 | `/committees`, `/committees/{id}`, `/committees/coverage` |
| `CONFIGURATION_MANAGE` | 1 | `/organization/profile` |
| `CURRENCY_MANAGE` | 4 | `/master-data/currencies`, `/master-data/currencies/{id}`, `/master-data/currencies/{id}/activation`, `/master-data/exchange-rates` |
| `CURRENCY_VIEW` | 3 | `/master-data/currencies`, `/master-data/exchange-rates`, `/master-data/tax-authorities` |
| `DATA_SCOPE_MANAGE` | 2 | `/users/{id}/scopes`, `/users/{id}/scopes/{scopeId}` |
| `DELEGATION_MANAGE` | 3 | `/delegation-matrix`, `/delegation-matrix/{id}/close`, `/delegation-matrix/{id}/activation` |
| `DELEGATION_VIEW` | 2 | `/delegation-matrix`, `/delegation-matrix/preview` |
| `EXCEEDING_LIMIT_MANAGE` | 3 | `/exceeding-limit-rules`, `/exceeding-limit-rules/{id}`, `/exceeding-limit-rules/{id}/activation` |
| `EXCEEDING_LIMIT_VIEW` | 3 | `/exceeding-limit-rules`, `/exceeding-limit-rules/{id}`, `/exceeding-limit-rules/evaluation` |
| `EXPENSE_CONFIGURATION_MANAGE` | 9 | `/expense-configuration/categories`, `/expense-configuration/categories/{id}`, `/expense-configuration/categories/{id}/activation`, `/expense-configuration/categories/{categoryId}/subcategories`, `/expense-configuration/subcategories/{id}`, `/expense-configuration/subcategories/{id}/activation`, `/expense-configuration/natures`, `/expense-configuration/natures/{id}/activation`, `/expense-configuration/request-types` |
| `EXPENSE_CONFIGURATION_VIEW` | 3 | `/expense-configuration/categories`, `/expense-configuration/natures`, `/expense-configuration/request-types` |
| `ORGANIZATION_MANAGE` | 9 | `/organization/units`, `/organization/units/{id}`, `/organization/units/{id}/activation`, `/organization/departments`, `/organization/departments/{id}`, `/organization/departments/{id}/activation`, `/organization/branches`, `/organization/branches/{id}`, `/organization/branches/{id}/activation` |
| `ORGANIZATION_VIEW` | 6 | `/organization/profile`, `/organization/unit-types`, `/organization/units`, `/organization/units/tree`, `/organization/departments`, `/organization/branches` |
| `RECOMMENDING_AUTHORITY_MANAGE` | 3 | `/recommending-authorities`, `/recommending-authorities/{id}`, `/recommending-authorities/{id}/activation` |
| `RECOMMENDING_AUTHORITY_VIEW` | 3 | `/recommending-authorities`, `/recommending-authorities/{id}`, `/recommending-authorities/resolved` |
| `REQUEST_CANCEL` | 1 | `/expense-requests/{id}/cancel` |
| `REQUEST_CREATE` | 1 | `/expense-requests` |
| `REQUEST_EDIT` | 1 | `/expense-requests/{id}` |
| `REQUEST_SUBMIT` | 1 | `/expense-requests/{id}/submit` |
| `REQUEST_VIEW_OWN` | 3 | `/expense-requests/{id}/activity`, `/expense-requests`, `/expense-requests/{id}` |
| `ROLE_MANAGE` | 4 | `/roles`, `/roles/{id}`, `/roles/{id}/activation`, `/roles/{id}/permissions` |
| `ROLE_VIEW` | 3 | `/roles`, `/roles/permissions`, `/roles/{id}` |
| `TAX_AUTHORITY_MANAGE` | 1 | `/master-data/tax-authorities` |
| `USER_MANAGE` | 4 | `/users`, `/users/{id}`, `/users/{id}/activation`, `/users/{id}/password-reset` |
| `USER_ROLE_ASSIGN` | 2 | `/users/{id}/roles`, `/users/{id}/roles/{assignmentId}` |
| `USER_VIEW` | 4 | `/users`, `/users/search`, `/users/{id}`, `/users/{id}/roles` |
| `VENDOR_MANAGE` | 3 | `/master-data/vendors`, `/master-data/vendors/{id}`, `/master-data/vendors/{id}/activation` |
| `VENDOR_VIEW` | 2 | `/master-data/vendors`, `/master-data/vendors/{id}` |
| `WORKFLOW_MANAGE` | 5 | `/workflows`, `/workflows/{definitionId}`, `/workflows/{definitionId}/active`, `/workflows/{definitionId}/draft`, `/workflows/{definitionId}/draft/from-published` |
| `WORKFLOW_PUBLISH` | 2 | `/workflows/versions/{versionId}/publish`, `/workflows/versions/{versionId}/retire` |
| `WORKFLOW_VIEW` | 4 | `/workflows`, `/workflows/{definitionId}/versions`, `/workflows/versions/{versionId}`, `/workflows/versions/{versionId}/validate` |
| _Any authenticated user_ | 2 | `/auth/me`, `/auth/change-password` |
| _Public endpoints_ | 7 | `/auth/login`, `/auth/logout`, `/auth/csrf`, `/auth/forgot-password`, `/auth/reset-password`, `/auth/installation`, `/auth/bootstrap` |

## Permissions with no endpoint yet

A permission in the catalogue with no consumer is a module not yet built. Listing them keeps the audit's "unimplemented permission" figure honest instead of letting it quietly become "permission that does not exist".

| Permission | Module it belongs to | Phase |
|---|---|---|
| `AUDIT_EXPORT` | Audit export | Not in Phase 4A Step 1 scope (AUD-03) |
| `CONFIGURATION_VIEW` | System configuration screen | Not in Phase 1-3 scope |
| `DASHBOARD_MANAGE` | Dashboard widgets | Not in Phase 1-3 scope |
| `DOCUMENT_DELETE` | Document management | Phase 4 |
| `DOCUMENT_DOWNLOAD` | Document management | Phase 4 |
| `DOCUMENT_UPLOAD` | Document management | Phase 4 |
| `EMAIL_CONFIGURATION_MANAGE` | Notifications | Phase 4 |
| `INTEGRATION_MANAGE` | External integration | Not in Phase 1-3 scope |
| `INTEGRATION_VIEW` | External integration | Not in Phase 1-3 scope |
| `NOTIFICATION_TEMPLATE_MANAGE` | Notifications | Phase 4 |
| `NOTIFICATION_VIEW` | Notifications | Phase 4 |
| `REPORT_EXPORT` | Reporting | Not in Phase 1-3 scope |
| `REPORT_VIEW` | Reporting | Not in Phase 1-3 scope |
| `REQUEST_EXPORT` | Register export | Not yet scheduled |
| `REQUEST_REVERSE` | Reversing an approved request | Not in Phase 1-3 scope |
| `REQUEST_VIEW_ALL` | Register-wide visibility | Gap - see the note below |
| `REQUEST_VIEW_SCOPE` | Scope-based visibility | Gap - see the note below |
| `RULE_MANAGE` | Business rule authoring | Blocked with OD-P3-04 |
| `RULE_PUBLISH` | Business rule authoring | Blocked with OD-P3-04 |
| `RULE_VIEW` | Business rule authoring | Blocked with OD-P3-04 |
| `SYSTEM_ADMINISTER` | Installation administration | Not in Phase 1-3 scope |

### `request:view-all` and `request:view-scope` are a real gap

The register endpoint is guarded by `request:view-own` and filters by data scope inside the service. There is
therefore **no permission that lets a finance user see every submitted request in the bank**, and no separate
permission for scope-based visibility. Today the widest visibility available is an `ALL` data scope attached to
`request:view-own`.

This is not a Phase 3 regression — Phase 2 built it that way — but it is the kind of thing that only shows up when
the permission catalogue is compared against the endpoints, which is why it is recorded here rather than left for
someone to discover from a support ticket.
