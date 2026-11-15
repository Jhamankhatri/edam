package com.edam.documents.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request/response DTOs for the document-management API. All responses deliberately
 * omit the Cloudinary {@code public_id}, {@code asset_id} and {@code secure_url}; signed
 * download URLs are minted only via the download endpoint after authorization.
 */
public final class DocumentDtos {
	private DocumentDtos() {}

	@Schema(name = "DocumentCategory")
	public enum DocumentCategory { QUOTATION, INVOICE, SUPPORTING_DOCUMENT, APPROVAL_DOCUMENT,
		TAX_DOCUMENT, GRN_DOCUMENT, CAPEX_DOCUMENT, OTHER }

	@Schema(name = "DocumentResponse", description = "Metadata for a stored document; binary content is served via the download endpoint.")
	public record DocumentResponse(
			UUID id,
			String originalFilename,
			String category,
			String mimeType,
			long sizeBytes,
			String description,
			String lifecycleStatus,
			String scanStatus,
			UUID uploadedBy,
			String uploadedByName,
			Instant uploadedAt,
			UUID expenseRequestId) {}

	@Schema(name = "UploadDocumentRequest")
	public record UploadDocumentRequest(
			@NotNull UUID expenseRequestId,
			@NotBlank @Pattern(regexp = "^[A-Z_]+$", message = "Document category must be one of the allowed codes.")
			@Size(max = 30) String category,
			@Size(max = 500) String description) {}

	@Schema(name = "DocumentAssociationRequest")
	public record DocumentAssociationRequest(
			@NotNull UUID mediaAssetId,
			@NotBlank @Pattern(regexp = "^[A-Z_]+$") String documentType,
			boolean mandatory,
			@Size(max = 150) String requiredByRule,
			@Size(max = 500) String description) {}

	@Schema(name = "ScanEventResponse")
	public record ScanEventResponse(
			UUID id,
			String status,
			String scanner,
			String scannerVersion,
			String threatName,
			Instant startedAt,
			Instant finishedAt,
			String detail) {}
}
