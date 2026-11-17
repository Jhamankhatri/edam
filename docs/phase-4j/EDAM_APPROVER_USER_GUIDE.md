# EDAM Approver User Guide

Audience: department heads, recommending authorities, committee members, CFO/CEO and other approvers who act on expense requests.

## 1. Approval inbox

When you log in, the **Approval inbox** lists every workflow task that is waiting for your decision (or waiting for a group you belong to to achieve quorum). The badge in the top bar shows your total open count.

Filters:
- Step (HOD review, Recommending authority, Committee, Finance, CFO, CEO, etc.)
- Age (0–2 days, 3–7 days, >7 days)
- Amount threshold (over approval limit, within limit)
- Department / branch
- Request type (OPEX / CAPEX)
- Search by request number, requester, vendor.

Each row shows: request number, requester, department, type, total amount (with currency), current step, time-since-submitted, priority flag (e.g. exceeding limit).

## 2. Reviewing a request

Click a row to open the **case file**. The case file is the single screen for an approval decision:

- **Header:** request number, status, submitted time, requester name/employee code, department, branch.
- **Summary card:** type, currency, total amount, vendor, required-by date.
- **Line items (OPEX)** or **CAPEX detail block** with STA / budget / previous summaries where applicable.
- **Workflow stepper:** previous steps with approver names, timestamps and remarks; current step highlighted; future steps shown dimmed.
- **Documents tab:** all attachments; open signed-URL previews (only CLEAN documents downloadable).
- **Activity / timeline tab:** all prior actions, document events and audit entries for this request.
- **Delegation note** (if applicable): shows whether you are acting as a delegate for another approver.

## 3. Actions you can take

Actions are only enabled when you are an eligible approver for the current task AND the task is still open. If the request has already moved (by quorum or by another approver's action), the UI shows "This task is no longer waiting for you."

### Approve
- Enter optional remarks (required by some institutions at certain steps).
- Click **Approve**. If the step requires quorum (e.g. a committee of N members), your vote is recorded; the step only advances when enough approvers have approved.
- On success, the stepper advances to the next step or the request is completed.

### Reject
- Remarks are **required** (explain the reason the requester must address).
- Click **Reject**. The request returns to the requester in Rejected status; you cannot undo rejection.

### Revert
- Reverts the **entire case** back to the requester (in contrast to rejection which can also be at step level). **Use sparingly** — reverts discard the current approval chain so resubmission starts from scratch.
- Remarks required. This action may be restricted to certain roles (e.g. CFO, workflow admin) depending on configuration.

### Forward
- Forward this task to a different eligible approver (e.g. when you are recused, or delegating to a deputy).
- Select a recipient from the user search; remarks are required and visible in the timeline.
- After forward, **your** task is completed and a NEW task is created for the recipient; the request remains at the same step.

## 4. Quorum steps

If a step is configured with quorum (e.g. EC or committee approval):
- You see other committee members' votes as they arrive (name, vote, remarks, timestamp).
- The step advances when the configured threshold is met (e.g. 3 of 5); subsequent votes are recorded but do not change the outcome.
- You can change your vote only before quorum is met (policy-dependent; your institution may disable this).

## 5. Limits and warnings
- **Exceeding limit:** if the request amount exceeds your approval limit or a configured exceeding-limit rule, the UI shows a warning banner. Depending on policy the request may still be submitted upward (OD-05).
- **Missing recommending authority:** if a required recommending authority is not configured for the department/category, you cannot approve until the administrator sets it up (the case returns with a configuration error in the timeline).
- **CAPEX finance fields:** if finance-only fields are pending (OD-13), they are shown as read-only to non-finance approvers.

## 6. Notifications
- You receive an in-app notification (and optionally an email) when:
  - A new task lands in your inbox.
  - A request you previously approved is rejected or reverted.
  - A forward is addressed to you.
- Mark notifications read individually or "Mark all read".

## 7. Troubleshooting
- **"Task already acted on"** (409 CONCURRENT_MODIFICATION): another approver acted first, or quorum was reached. Refresh the case file.
- **"You are not an eligible approver"** (403): the task is not assigned to you (or your group), or the step has moved. Check your inbox for an up-to-date list.
- **"Remarks are required":** the current step requires a reason for reject/revert/forward.
- Quote the correlation id from the error toast if you escalate to support.
