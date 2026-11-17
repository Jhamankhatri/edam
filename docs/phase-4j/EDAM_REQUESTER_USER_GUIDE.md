# EDAM Requester User Guide

Audience: employees who submit OPEX or CAPEX expense requests through EDAM.

> Screens in this guide refer to the frontend routes specified in `FRONTEND_MASTER_INTEGRATION_GUIDE.md`. The Postman collection can also be used to test the same flows.

## 1. Signing in

1. Navigate to EDAM. You will see the institution-branded login page (logo, colours and name are set by your administrator).
2. Enter your username and password.
3. If you forget your password, click "Forgot password" — a reset link is sent to your registered email if SMTP is configured.
4. After sign-in you land on your default landing page:
   - Requesters land on **My requests**.
   - Approvers land on **Approval inbox** (see the Approver guide).

## 2. My requests

The **My requests** register shows every request you have submitted (and only yours), regardless of department/branch. Filters:
- Status: Draft / Submitted / In approval / Approved / Rejected / Reverted / Completed / Cancelled
- Type: OPEX / CAPEX
- Date range
- Search by request number or vendor

Columns (at minimum): request number, type, title, department, total amount (in request currency), status, submitted date, current approver step, last updated.

Actions available depend on status:
- **Draft** — Edit / Submit / Cancel / Delete (delete only for drafts not yet submitted).
- **Submitted / In approval** — View / Cancel (cancelling returns the request to you as cancelled).
- **Rejected / Reverted** — View / Edit (and resubmit) / Cancel.
- **Approved / Completed / Cancelled** — View only.

## 3. Creating a new request

1. Click **New request**. Choose type: **OPEX** or **CAPEX**.
2. Fill the header:
   - Title (short description)
   - Department, branch (populated from your data scopes)
   - Request currency; if non-PKR, an exchange rate is required (FX auto-conversion is pending finance sign-off, so you may be asked to enter both local and PKR values).
   - Vendor (search the master-data vendor list)
   - Required-by date
   - Budget code / cost centre (if configured for your institution)
   - Narration / business justification
3. **OPEX:** Add line items. Each item has: category, subcategory, nature (when applicable), description, quantity, unit price, tax %, total (auto-calculated).
4. **CAPEX:** Complete the CAPEX-specific section (STA details, budget summary, previous amounts where applicable). **Note:** some CAPEX finance fields (e.g. variance formulas, who enters finance values) are still under finance sign-off; required fields are marked.
5. **Attachments:** upload invoices, quotations and supporting documents. Accepted types and size limits are set by the administrator. Each document is scanned for malware; you cannot submit until mandatory documents have been uploaded and passed scanning.
6. **Save draft** at any time. Drafts are visible only to you and are not routed for approval.
7. **Submit:** when validations pass, click Submit. The request is assigned a request number (e.g. `REQ-2026-000123`) and enters the workflow.

## 4. After submission

- The request is routed to the approvers configured in the published workflow for your department and request type.
- You can open the request detail to view the workflow stepper (current step, approvers, who has approved, who is pending).
- When the request moves through each step you will receive an in-app notification; email notifications are sent if your administrator has enabled them.
- **If rejected**, the reason (approver remarks) is shown; you can edit the request to address the remarks and resubmit (resubmission keeps the same request number and is logged to the timeline).
- **If reverted**, the entire case is returned to you; you must resubmit to restart approval.
- **If forwarded**, no action is required on your part; the approver has delegated to a colleague.

## 5. Documents
- Attachments are visible to you and to approvers within the configured visibility scope.
- You can download attachments only after they have been marked clean by the malware scanner. If a document is marked infected, it is quarantined and cannot be downloaded — remove and re-upload a clean file.
- You can add additional supporting documents while the request is in draft, and (if your institution allows) after it has been submitted but before final approval.

## 6. Notifications & profile
- Bell icon in the top bar shows unread count; click to open the notification centre, mark items read.
- From the user menu you can: view your profile, change your password, log out.
- Change password requires your current password; choose a password that meets the policy (length and complexity shown inline).

## 7. Troubleshooting
- **"Session expired"** — log in again (sessions time out after inactivity per administrator policy).
- **"You are not authorized"** — your role may not include the required permission for the action; if this is unexpected, ask your administrator to verify your data scopes.
- **"Invalid CSRF token"** — refresh the page and retry; this usually means your session was replaced (e.g. by logging in on another tab).
- **"Document cannot be downloaded"** — the document is still being scanned or was flagged. Wait for scan completion or upload a new copy.
- Every error shows a **correlation id** in the technical details panel; quote it when contacting support.
