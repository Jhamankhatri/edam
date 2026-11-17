# 10 — Document Upload, Download & Scanning

## 10.1 Upload
`POST /api/v1/documents` — `multipart/form-data`; permission `document:upload`; CSRF required.

| Part | Type | Required | Description |
|---|---|---|---|
| `file` | binary | Yes | File content. |
| `requestId` | string (uuid) | Yes | Request the document is attached to. The caller must have visibility over the request. |
| `documentType` | string enum | Yes | Document classification — values are constrained by the server (e.g. `INVOICE`, `QUOTATION`, `SUPPORTING`, `BUDGET`, `STA_REPORT`, `OTHER`). Use the server-advertised list where exposed; otherwise use the documented subset for the request type. |
| `description` | string | No | Free-text description (≤ 500 chars). |

**Size and type limits** are enforced server-side. Failure returns 409 `EDAM-5004 INVALID_DOCUMENT` with a safe message; the upload screen should display that message verbatim.

On success, 201 returns document metadata including `id`, `mediaAssetId`, `filename`, `size`, `contentType`, `uploadedBy`, `uploadedAt`, and `scanStatus = PENDING_SCAN`.

## 10.2 Listing / metadata
- `GET /api/v1/documents/requests/{requestId}` — `document:download`. Returns the list of documents attached to a request (visible to the caller).
- `GET /api/v1/documents/{id}` — `document:download`. Returns single document metadata.
- `GET /api/v1/documents/{id}/scans` — `document:download`. Returns scan-event history (`{ status, scanner, scannerVersion, scannedAt, notes }` array).

## 10.3 Download
- `GET /api/v1/documents/{id}/download-url` — `document:download`. Returns a short-lived signed URL (e.g. Cloudinary or other `StorageProvider`). Open this URL in a new tab / anchor with `target="_blank" rel="noopener noreferrer"`.
- **Never expose raw Cloudinary or storage credentials.** The URL is time-limited and signed; the frontend must not construct storage URLs itself.
- Download is fail-closed: when `scanStatus !== CLEAN`, the download-url endpoint returns 409 with an error (or a URL that is not yet usable, depending on provider). The UI must disable the download button unless `scanStatus === CLEAN`.

## 10.4 Scan lifecycle
`MediaAsset.ScanStatus`: `PENDING_SCAN`, `CLEAN`, `INFECTED`, `SCAN_FAILED`.

| Status | UI behaviour |
|---|---|
| `PENDING_SCAN` | Badge "Scanning…"; download disabled; poll scans endpoint or refresh on timeline events. |
| `CLEAN` | Badge "Clean" (green); download enabled. |
| `INFECTED` | Badge "Infected" (red); download blocked; offer delete-and-replace. |
| `SCAN_FAILED` | Badge "Scan failed" (orange); download blocked; allow retry if administrator has enabled it (Phase 4J default: no retry UI — show "contact support"). |

## 10.5 Delete
`DELETE /api/v1/documents/{id}` — `document:delete`. Only allowed when the request is in an editable state (e.g. DRAFT/RETURNED) per policy; otherwise 409 `INVALID_STATE_TRANSITION`. Returns 204 on success.

## 10.6 Production dependencies
- **Object storage:** `StorageProvider` SPI. The Cloudinary adapter is wired when `CLOUDINARY_URL` is present; `FakeStorageProvider` is used only in tests. Without a provider, uploads fail 503/500 (`EXTERNAL_SERVICE_FAILURE`).
- **Malware scanner:** `MalwareScanner` SPI. `FakeMalwareScanner` is used only in tests. In production a real scanner (e.g. ClamAV or SaaS) must be plugged in; until then every upload stays `PENDING_SCAN` and downloads are blocked by default (fail-closed). This is an explicit go-live dependency (see §17, OD-14).

## 10.7 Frontend components
- `<FileUploader>` — shows drop zone, per-file progress, scan status badge, errors inline.
- `<DocumentList>` — tabular list of documents with download/delete actions, scan badge.
- Never render raw user-uploaded HTML/SVG inline — always download via the signed URL.
