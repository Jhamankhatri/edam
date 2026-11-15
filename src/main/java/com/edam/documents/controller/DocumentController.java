package com.edam.documents.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.Permissions;
import com.edam.documents.dto.DocumentDtos;
import com.edam.documents.dto.DocumentDtos.DocumentResponse;
import com.edam.documents.dto.DocumentDtos.ScanEventResponse;
import com.edam.documents.dto.DocumentDtos.UploadDocumentRequest;
import com.edam.documents.service.DocumentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Document upload, metadata, download-url minting and deletion. Download itself is via a
 * time-limited signed Cloudinary URL (returned by {@code GET /download-url}); bytes never
 * transit EDAM so the backend can't leak file content via a logged response body.
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/documents", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Documents", description = "Upload, metadata, authorised download and management of request documents.")
public class DocumentController {

	private final DocumentService documents;

	public DocumentController(DocumentService documents) { this.documents = documents; }

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@EdamPermission(Permissions.DOCUMENT_UPLOAD)
	@Operation(summary = "Upload a document and attach it to an expense request")
	public ResponseEntity<ApiResponse<DocumentResponse>> upload(
			@RequestParam("file") MultipartFile file,
			@Valid @ModelAttribute UploadDocumentRequest meta,
			HttpServletRequest request) {
		DocumentResponse saved = this.documents.upload(file, meta);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(saved, CorrelationId.current(), request.getRequestURI()));
	}

	@GetMapping("/requests/{requestId}")
	@EdamPermission(Permissions.DOCUMENT_DOWNLOAD)
	@Operation(summary = "List documents attached to an authorised expense request")
	public ApiResponse<List<DocumentResponse>> listForRequest(@PathVariable UUID requestId,
			HttpServletRequest request) {
		return ApiResponse.success(this.documents.listForRequest(requestId),
				CorrelationId.current(), request.getRequestURI());
	}

	@GetMapping("/{id}")
	@EdamPermission(Permissions.DOCUMENT_DOWNLOAD)
	@Operation(summary = "Metadata for one document")
	public ApiResponse<DocumentResponse> get(@PathVariable UUID id, HttpServletRequest request) {
		return ApiResponse.success(this.documents.metadata(id), CorrelationId.current(), request.getRequestURI());
	}

	@GetMapping("/{id}/download-url")
	@EdamPermission(Permissions.DOCUMENT_DOWNLOAD)
	@Operation(summary = "Issue a time-limited, signed download URL for a CLEAN document")
	public ApiResponse<Map<String, Object>> downloadUrl(@PathVariable UUID id, HttpServletRequest request) {
		String url = this.documents.downloadUrl(id);
		return ApiResponse.success(Map.of("url", url, "expiresInSeconds", 300),
				CorrelationId.current(), request.getRequestURI());
	}

	@GetMapping("/{id}/scans")
	@EdamPermission(Permissions.DOCUMENT_DOWNLOAD)
	@Operation(summary = "Malware scan history for a document")
	public ApiResponse<List<ScanEventResponse>> scans(@PathVariable UUID id, HttpServletRequest request) {
		return ApiResponse.success(this.documents.scanHistory(id),
				CorrelationId.current(), request.getRequestURI());
	}

	@DeleteMapping("/{id}")
	@EdamPermission(Permissions.DOCUMENT_DELETE)
	@Operation(summary = "Delete a document (soft-delete DB record + best-effort remote delete)")
	public ApiResponse<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
		this.documents.delete(id);
		return ApiResponse.success(null, CorrelationId.current(), request.getRequestURI());
	}
}
