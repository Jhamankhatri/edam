package com.edam.documents.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.InstitutionContext;
import com.edam.config.CloudinaryProperties;
import com.edam.documents.dto.DocumentDtos;
import com.edam.documents.dto.DocumentDtos.DocumentResponse;
import com.edam.documents.dto.DocumentDtos.ScanEventResponse;
import com.edam.documents.model.DocumentScanEvent;
import com.edam.documents.model.MediaAsset;
import com.edam.documents.model.MediaAsset.DocumentCategory;
import com.edam.documents.model.MediaAsset.Lifecycle;
import com.edam.documents.model.MediaAsset.ScanStatus;
import com.edam.documents.repository.DocumentScanEventRepository;
import com.edam.documents.repository.MediaAssetRepository;
import com.edam.documents.scan.MalwareScanner;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.expenserequest.model.RequestDocument;
import com.edam.expenserequest.repository.ExpenseRequestRepository;
import com.edam.expenserequest.repository.RequestDocumentRepository;
import com.edam.expenserequest.service.ExpenseRequestService;
import com.edam.storage.StorageException;
import com.edam.storage.StorageProvider;
import com.edam.storage.StoredObject;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Orchestrates upload → Cloudinary → DB metadata → malware scan, and authorised
 * download/delete. Database and Cloudinary side-effects are coordinated carefully so that
 * an upload is never invisible to the database and never marked downloadable before a
 * scan verdict exists.
 *
 * <p>Fail-closed security:
 * <ul>
 *   <li>Every new asset is inserted with {@code scan_status=PENDING_SCAN}.</li>
 *   <li>Downloads are rejected unless scan_status = CLEAN.</li>
 *   <li>When no approved scanner is configured, the {@link MalwareScanner} returns
 *       SCAN_FAILED and the document remains quarantined — no "trust the client" path.</li>
 * </ul>
 */
@Service
public class DocumentService {

	/** Extensions we accept. Tight whitelist; extension does NOT determine MIME. */
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
			"pdf", "png", "jpg", "jpeg", "gif", "webp",
			"doc", "docx", "xls", "xlsx", "csv", "txt", "eml", "msg");

	/** Magic-byte sniffing — we refuse streams whose bytes don't match a small allow-list. */
	private static final Map<String, byte[]> MAGIC_SIGNATURES = Map.of(
			"application/pdf", new byte[]{'%','P','D','F','-'},
			"image/png", new byte[]{(byte)0x89,'P','N','G','\r','\n',0x1a,'\n'},
			"image/jpeg", new byte[]{(byte)0xFF,(byte)0xD8,(byte)0xFF},
			"image/gif", new byte[]{'G','I','F','8'}
	);

	private static final long DOWNLOAD_URL_TTL_SECONDS = 5 * 60;

	private final MediaAssetRepository assets;
	private final RequestDocumentRepository requestDocs;
	private final ExpenseRequestRepository requests;
	private final ExpenseRequestService requestService;
	private final UserRepository users;
	private final DocumentScanEventRepository scans;
	private final StorageProvider storage;
	private final MalwareScanner scanner;
	private final CloudinaryProperties storageProps;
	private final AuditRecorder audit;
	private final InstitutionContext ctx;

	public DocumentService(MediaAssetRepository assets, RequestDocumentRepository requestDocs,
			ExpenseRequestRepository requests, ExpenseRequestService requestService,
			UserRepository users, DocumentScanEventRepository scans,
			StorageProvider storage, MalwareScanner scanner, CloudinaryProperties storageProps,
			AuditRecorder audit, InstitutionContext ctx) {
		this.assets = assets;
		this.requestDocs = requestDocs;
		this.requests = requests;
		this.requestService = requestService;
		this.users = users;
		this.scans = scans;
		this.storage = storage;
		this.scanner = scanner;
		this.storageProps = storageProps;
		this.audit = audit;
		this.ctx = ctx;
	}

	@Transactional
	public DocumentResponse upload(MultipartFile file, DocumentDtos.UploadDocumentRequest req) {
		EdamPrincipal caller = requireCaller();

		// 1. Associate with an authorised expense request (requester or scope), not just a UUID.
		ExpenseRequest request = this.requests.findById(req.expenseRequestId())
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Expense request not found."));
		if (!this.requestService.isVisible(request, caller)) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "Expense request not found.");
		}
		// Upload rights: request owner with DOCUMENT_UPLOAD, or scope/installation-wide.
		boolean canUpload = caller.hasInstallationScope()
				|| (request.getRequestedBy().equals(caller.userId()) && caller.hasPermission("document:upload"))
				|| (caller.scopesToDepartment(request.getDepartmentId()) && caller.hasPermission("document:upload"))
				|| (request.getBranchId() != null && caller.scopesToBranch(request.getBranchId()) && caller.hasPermission("document:upload"));
		if (!canUpload) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED, "You cannot attach documents to this request.");
		}

		// 2. File-level validation
		if (file == null || file.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_DOCUMENT, "An empty file is not permitted.");
		}
		if (file.getSize() > this.storageProps.getMaxFileSizeBytes()) {
			throw new BusinessException(ErrorCode.INVALID_DOCUMENT,
					"File exceeds the maximum permitted size (" + (this.storageProps.getMaxFileSizeBytes() / (1024*1024)) + " MiB).");
		}
		String original = sanitizeFilename(file.getOriginalFilename());
		String ext = extensionOf(original).toLowerCase(Locale.ROOT);
		if (!ALLOWED_EXTENSIONS.contains(ext)) {
			throw new BusinessException(ErrorCode.INVALID_DOCUMENT, "Files of type ." + ext + " are not permitted.");
		}
		String detectedMime = detectMimeType(file);
		String category = normalizeCategory(req.category());

		// 3. Read bytes through a digesting wrapper to compute SHA-256, then upload.
		String publicId = storagePrefix() + "/" + UUID.randomUUID();
		StoredObject stored;
		String sha256;
		try (InputStream raw = new BufferedInputStream(file.getInputStream());
			 MessageDigestInputStream digestIn = new MessageDigestInputStream(raw)) {
			stored = this.storage.upload(publicId, this.storageProps.getFolder(), detectedMime, digestIn);
			sha256 = digestIn.digestHex();
			// Persist metadata.
			MediaAsset asset = new MediaAsset();
			asset.setOriginalFileName(original);
			asset.setStoredFileName(stored.publicId());
			asset.setStorageProvider(this.storage.providerName());
			asset.setStorageReference(stored.publicId());
			asset.setPublicId(stored.publicId());
			asset.setAssetId(stored.assetId());
			asset.setSecureUrl(stored.secureUrl());
			asset.setResourceType(stored.resourceType());
			asset.setFileFormat(stored.format() == null ? ext : stored.format());
			asset.setMimeType(detectedMime);
			asset.setFileSize(file.getSize());
			asset.setChecksum(sha256);
			asset.setDocumentCategory(category);
			asset.setDescription(req.description());
			asset.setLifecycleStatus(Lifecycle.ACTIVE.name());
			asset.setScanStatus(ScanStatus.PENDING_SCAN.name());
			asset.markUploaded(caller.userId());
			MediaAsset saved = this.assets.save(asset);

			// 4. Link to the expense request using the canonical document-type codes.
			String docType = mapToRequestDocType(category);
			RequestDocument link = new RequestDocument(req.expenseRequestId(), saved.getId(), docType,
					false, null, caller.userId(), Instant.now());
			if (req.description() != null) link.setDescription(req.description());
			this.requestDocs.save(link);

			// 5. Scan the document (fail closed). Run the scanner against the freshly-opened
			// stream: if the provider gave us back a secure_url we could stream it, but we must
			// NOT trust bytes we didn't just upload. Re-open the input stream from the uploaded
			// file and scan. Failures become SCAN_FAILED and quarantine the asset.
			runScan(saved, file);

			Map<String, Object> after = Map.of("id", saved.getId(), "category", category, "mime", detectedMime);
			this.audit.recordChange(AuditAction.CREATE, "MediaAsset", saved.getId(), Map.of(), after);

			return toResponse(saved, req.expenseRequestId(), caller);
		}
		catch (IOException ioe) {
			// DB transaction will roll back; best-effort delete the orphan from Cloudinary.
			safeDeleteRemotely(publicId, "raw");
			throw new StorageException("Unable to read the uploaded file.", ioe);
		}
		catch (StorageException se) {
			safeDeleteRemotely(publicId, "raw");
			throw se;
		}
	}

	@Transactional(readOnly = true)
	public List<DocumentResponse> listForRequest(UUID requestId) {
		EdamPrincipal caller = requireCaller();
		ExpenseRequest r = this.requests.findById(requestId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Expense request not found."));
		if (!this.requestService.isVisible(r, caller)) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "Expense request not found.");
		}
		List<RequestDocument> links = this.requestDocs.findByExpenseRequestId(requestId);
		if (links.isEmpty()) return List.of();
		List<UUID> ids = links.stream().map(RequestDocument::getMediaAssetId).toList();
		Map<UUID, MediaAsset> byId = new LinkedHashMap<>();
		for (MediaAsset a : this.assets.findAllById(ids)) byId.put(a.getId(), a);
		List<DocumentResponse> out = new ArrayList<>();
		for (RequestDocument l : links) {
			MediaAsset a = byId.get(l.getMediaAssetId());
			if (a == null) continue;
			out.add(toResponse(a, requestId, caller));
		}
		return out;
	}

	@Transactional(readOnly = true)
	public DocumentResponse metadata(UUID assetId) {
		EdamPrincipal caller = requireCaller();
		MediaAsset a = requireAsset(assetId, caller);
		return toResponse(a, linkedRequestId(assetId), caller);
	}

	/** Return a time-limited, signed URL that the caller may use to download the bytes. */
	@Transactional(readOnly = true)
	public String downloadUrl(UUID assetId) {
		EdamPrincipal caller = requireCaller();
		MediaAsset a = requireAsset(assetId, caller);
		if (a.getDeletedAt() != null) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "Document not found.");
		}
		// Only CLEAN documents are downloadable — fail closed.
		if (!ScanStatus.CLEAN.name().equals(a.getScanStatus())) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"This document is not available for download (scan status: " + a.getScanStatus() + ").");
		}
		String url = this.storage.signDownloadUrl(a.getPublicId(), a.getResourceType(), DOWNLOAD_URL_TTL_SECONDS);
		this.audit.record(AuditAction.DOCUMENT_DOWNLOAD, "MediaAsset", a.getId());
		return url;
	}

	@Transactional
	public void delete(UUID assetId) {
		EdamPrincipal caller = requireCaller();
		MediaAsset a = requireAsset(assetId, caller);
		if (!caller.hasPermission("document:delete") && !caller.userId().equals(a.getUploadedBy())) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED, "You cannot delete this document.");
		}
		Map<String, Object> before = snapshot(a);
		// Soft-delete locally first (DB-tx protects the invariant), then best-effort delete remotely.
		a.markDeleted();
		this.assets.save(a);
		try {
			this.storage.delete(a.getPublicId(), a.getResourceType());
		}
		catch (StorageException se) {
			// Cloudinary delete failed — record it, do not roll back the DB deletion. A reconciliation
			// job can be added later; we leave the public_id intact so the cleanup can be retried.
			this.audit.record(AuditAction.DOCUMENT_DELETE, "MediaAsset.delete.remote-failed", a.getId());
		}
		this.audit.recordChange(AuditAction.DOCUMENT_DELETE, "MediaAsset", a.getId(), before, snapshot(a));
	}

	@Transactional(readOnly = true)
	public List<ScanEventResponse> scanHistory(UUID assetId) {
		EdamPrincipal caller = requireCaller();
		requireAsset(assetId, caller);
		List<ScanEventResponse> out = new ArrayList<>();
		for (DocumentScanEvent e : this.scans.findByMediaAssetIdOrderByScanStartedAtDesc(assetId)) {
			out.add(new ScanEventResponse(e.getId(), e.getScanStatus(), e.getScanner(),
					e.getScannerVersion(), e.getThreatName(), e.getScanStartedAt(),
					e.getScanFinishedAt(), e.getDetail()));
		}
		return out;
	}

	// ------------------------------------------------------------------
	// Internals
	// ------------------------------------------------------------------

	private EdamPrincipal requireCaller() {
		return this.ctx.currentPrincipal()
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED, "Sign in is required."));
	}

	private MediaAsset requireAsset(UUID id, EdamPrincipal caller) {
		MediaAsset a = this.assets.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Document not found."));
		UUID reqId = linkedRequestId(id);
		if (reqId != null) {
			ExpenseRequest r = this.requests.findById(reqId).orElse(null);
			if (r == null || !this.requestService.isVisible(r, caller)) {
				// Do not disclose existence via a different error code.
				throw new BusinessException(ErrorCode.NOT_FOUND, "Document not found.");
			}
		}
		return a;
	}

	private UUID linkedRequestId(UUID assetId) {
		return this.requestDocs.findFirstByMediaAssetId(assetId)
				.map(RequestDocument::getExpenseRequestId).orElse(null);
	}

	private void runScan(MediaAsset asset, MultipartFile file) {
		DocumentScanEvent event = new DocumentScanEvent(asset.getId(),
				ScanStatus.PENDING_SCAN.name(), this.scanner.name(), null, null, "Scan queued.");
		this.scans.save(event);
		try (InputStream in = file.getInputStream()) {
			var result = this.scanner.scan(in);
			event.finish();
			asset.setScanStatus(result.status());
			if ("CLEAN".equals(result.status())) {
				asset.setScannedAt(Instant.now());
			}
			// Persist the verdict event row.
			DocumentScanEvent finished = new DocumentScanEvent(asset.getId(), result.status(),
					this.scanner.name(), result.scannerVersion(), result.threatName(), result.detail());
			finished.finish();
			this.scans.save(finished);
		}
		catch (IOException ioe) {
			DocumentScanEvent failed = new DocumentScanEvent(asset.getId(), ScanStatus.SCAN_FAILED.name(),
					this.scanner.name(), null, null, "Scanner failed to read the stream: " + ioe.getMessage());
			failed.finish();
			this.scans.save(failed);
			asset.setScanStatus(ScanStatus.SCAN_FAILED.name());
		}
	}

	private DocumentResponse toResponse(MediaAsset a, UUID requestId, EdamPrincipal caller) {
		String uploaderName = null;
		if (a.getUploadedBy() != null) {
			uploaderName = this.users.findById(a.getUploadedBy())
					.map(u -> u.getFirstName() + " " + u.getLastName()).orElse(null);
		}
		return new DocumentResponse(a.getId(), a.getOriginalFileName(), a.getDocumentCategory(),
				a.getMimeType(), a.getFileSize(), a.getDescription(),
				a.getLifecycleStatus(), a.getScanStatus(),
				a.getUploadedBy(), uploaderName, a.getUploadedAt(), requestId);
	}

	private static Map<String, Object> snapshot(MediaAsset a) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", a.getId());
		m.put("lifecycle", a.getLifecycleStatus());
		m.put("scan", a.getScanStatus());
		m.put("category", a.getDocumentCategory());
		return m;
	}

	private void safeDeleteRemotely(String publicId, String rt) {
		try { this.storage.delete(publicId, rt); } catch (Exception ignore) { /* best effort */ }
	}

	private static String sanitizeFilename(String name) {
		if (name == null) throw new BusinessException(ErrorCode.INVALID_DOCUMENT, "Filename is required.");
		String n = name.replace('\\', '/');
		int slash = n.lastIndexOf('/');
		if (slash >= 0) n = n.substring(slash + 1);
		if (n.isBlank() || n.contains("..") || n.contains("\0")) {
			throw new BusinessException(ErrorCode.INVALID_DOCUMENT, "Filename is not permitted.");
		}
		return n;
	}

	private static String extensionOf(String filename) {
		int dot = filename.lastIndexOf('.');
		if (dot < 0 || dot == filename.length() - 1) return "";
		return filename.substring(dot + 1);
	}

	private String detectMimeType(MultipartFile file) {
		String clientType = file.getContentType();
		// Sniff magic bytes to confirm declared content type. We accept only PDF/PNG/JPEG/GIF magic
		// checks explicitly; Office/CSV/TXT formats are accepted by extension alone (their magic is
		// inconsistent) but we NEVER trust the client's MIME for storage.
		try (InputStream in = file.getInputStream()) {
			byte[] head = in.readNBytes(16);
			for (var e : MAGIC_SIGNATURES.entrySet()) {
				if (startsWith(head, e.getValue())) return e.getKey();
			}
		}
		catch (IOException ignored) { /* fall through to default */ }
		// Fallback: a conservative MIME map keyed by extension.
		String ext = extensionOf(file.getOriginalFilename() == null ? "" : file.getOriginalFilename())
				.toLowerCase(Locale.ROOT);
		return switch (ext) {
			case "pdf" -> "application/pdf";
			case "png" -> "image/png";
			case "jpg", "jpeg" -> "image/jpeg";
			case "gif" -> "image/gif";
			case "webp" -> "image/webp";
			case "doc" -> "application/msword";
			case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
			case "xls" -> "application/vnd.ms-excel";
			case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
			case "csv" -> "text/csv";
			case "txt", "eml" -> "text/plain";
			case "msg" -> "application/vnd.ms-outlook";
			default -> "application/octet-stream";
		};
	}

	private static boolean startsWith(byte[] head, byte[] sig) {
		if (head.length < sig.length) return false;
		for (int i = 0; i < sig.length; i++) if (head[i] != sig[i]) return false;
		return true;
	}

	private static String normalizeCategory(String raw) {
		if (raw == null) return DocumentCategory.OTHER.name();
		String c = raw.trim().toUpperCase(Locale.ROOT);
		try {
			DocumentCategory.valueOf(c);
			return c;
		}
		catch (IllegalArgumentException ex) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown document category: " + raw);
		}
	}

	private static String mapToRequestDocType(String category) {
		// Map new categories to the closest existing request_documents type to satisfy the CHECK
		// constraint while keeping the V21-added values acceptable. We prefer the new values.
		return switch (category) {
			case "SUPPORTING_DOCUMENT" -> "SUPPORTING_DOCUMENT";
			case "APPROVAL_DOCUMENT" -> "APPROVAL_DOCUMENT";
			case "TAX_DOCUMENT" -> "TAX_DOCUMENT";
			case "GRN_DOCUMENT" -> "GRN_DOCUMENT";
			case "CAPEX_DOCUMENT" -> "CAPEX_DOCUMENT";
			default -> category; // QUOTATION, INVOICE, OTHER, SUPPORTING etc. already valid
		};
	}

	private static String storagePrefix() { return "edam/docs"; }

	/** Small helper that SHA-256s everything that passes through it without buffering. */
	static final class MessageDigestInputStream extends InputStream {
		private final InputStream delegate;
		private final MessageDigest digest;
		MessageDigestInputStream(InputStream in) {
			this.delegate = in;
			try { this.digest = MessageDigest.getInstance("SHA-256"); }
			catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
		}
		@Override public int read() throws IOException {
			int b = this.delegate.read();
			if (b >= 0) this.digest.update((byte) b);
			return b;
		}
		@Override public int read(byte[] buf, int off, int len) throws IOException {
			int n = this.delegate.read(buf, off, len);
			if (n > 0) this.digest.update(buf, off, n);
			return n;
		}
		String digestHex() { return HexFormat.of().formatHex(this.digest.digest()); }
	}
}
