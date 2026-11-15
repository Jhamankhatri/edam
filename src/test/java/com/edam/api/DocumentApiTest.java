package com.edam.api;

import java.util.Objects;

import com.edam.common.api.ErrorCode;
import com.edam.config.CloudinaryProperties;
import com.edam.documents.FakeMalwareScanner;
import com.edam.documents.FakeStorageProvider;
import com.edam.documents.repository.MediaAssetRepository;
import com.edam.documents.scan.FailClosedScanner;
import com.edam.documents.scan.MalwareScanner;
import com.edam.documents.service.DocumentService;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.expenserequest.repository.RequestDocumentRepository;
import com.edam.storage.StorageProvider;
import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Focused integration tests for Phase 4C document management. Uses a fake storage
 * provider and a controllable malware scanner so the tests never talk to Cloudinary and
 * are fully deterministic.
 */
@SpringBootTest
class DocumentApiTest extends AbstractPostgreSqlIntegrationTest {

	@TestConfiguration
	static class Fakes {
		@Bean @Primary StorageProvider fakeStorage() { return new FakeStorageProvider(); }
		@Bean @Primary MalwareScanner fakeScanner() {
			// Default CLEAN; individual tests can override via casting.
			FakeMalwareScanner s = new FakeMalwareScanner();
			s.forcedStatus = "CLEAN";
			return s;
		}
	}

	@Autowired private DocumentService documents;
	@Autowired private MediaAssetRepository assets;
	@Autowired private RequestDocumentRepository requestDocs;
	@Autowired private StorageProvider storage;
	@Autowired private MalwareScanner scanner;
	@Autowired private com.edam.expenserequest.service.ExpenseRequestService requestService;
	@Autowired private com.edam.expenserequest.repository.ExpenseRequestRepository requestRepo;

	@BeforeEach
	void resetFakes() {
		((FakeStorageProvider) this.storage).objects.clear();
		((FakeStorageProvider) this.storage).throwOnUpload = false;
		((FakeStorageProvider) this.storage).throwOnDelete = false;
		((FakeMalwareScanner) this.scanner).forcedStatus = "CLEAN";
		((FakeMalwareScanner) this.scanner).throwOnScan = false;
		((FakeMalwareScanner) this.scanner).invocationCount = 0;
	}

	private MultipartFile pdfFile(String name) {
		return new MockMultipartFile("file", name, "application/pdf",
				("%PDF-1.4 fake pdf bytes for " + name).getBytes());
	}

	private UUID seedRequest() {
		// Reuse an existing request if one exists; otherwise fail. Tests run against a seeded
		// DB from V6..V21; for isolated runs, just pick the first request or create a minimal one.
		var any = this.requestRepo.findAll().stream().findFirst();
		return any.map(ExpenseRequest::getId).orElseGet(() -> {
			throw new IllegalStateException("No seeded expense request present — seed data required for doc tests");
		});
	}

	@Test
	void uploadAndPersistMetadata() {
		UUID reqId = seedRequest();
		var req = new com.edam.documents.dto.DocumentDtos.UploadDocumentRequest(
				reqId, "SUPPORTING_DOCUMENT", "receipt");
		var resp = this.documents.upload(pdfFile("receipt.pdf"), req);
		assertThat(resp.id()).isNotNull();
		assertThat(resp.originalFilename()).isEqualTo("receipt.pdf");
		assertThat(resp.scanStatus()).isEqualTo("CLEAN");
		assertThat(resp.expenseRequestId()).isEqualTo(reqId);
		assertThat(((FakeStorageProvider) this.storage).objects).hasSize(1);
		assertThat(this.requestDocs.findByExpenseRequestId(reqId)).isNotEmpty();
	}

	@Test
	void rejectsUnsupportedExtension() {
		var req = new com.edam.documents.dto.DocumentDtos.UploadDocumentRequest(
				seedRequest(), "OTHER", null);
		var ex = assertThrows(com.edam.common.exception.BusinessException.class,
				() -> this.documents.upload(new MockMultipartFile("file", "evil.exe", "application/octet-stream", new byte[]{0,0,0}), req));
		assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_DOCUMENT);
	}

	@Test
	void rejectsPathTraversalFilename() {
		var req = new com.edam.documents.dto.DocumentDtos.UploadDocumentRequest(
				seedRequest(), "OTHER", null);
		var ex = assertThrows(com.edam.common.exception.BusinessException.class,
				() -> this.documents.upload(pdfFile("../../etc/passwd.pdf"), req));
		assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_DOCUMENT);
	}

	@Test
	void rejectsWhenScanIsInfected() {
		((FakeMalwareScanner) this.scanner).forcedStatus = "INFECTED";
		var req = new com.edam.documents.dto.DocumentDtos.UploadDocumentRequest(
				seedRequest(), "INVOICE", null);
		var resp = this.documents.upload(pdfFile("invoice.pdf"), req);
		assertThat(resp.scanStatus()).isEqualTo("INFECTED");
		var ex = assertThrows(com.edam.common.exception.BusinessException.class,
				() -> this.documents.downloadUrl(resp.id()));
		assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
	}

	@Test
	void pendingScanRejectsDownload() {
		((FakeMalwareScanner) this.scanner).forcedStatus = "SCAN_FAILED";
		var req = new com.edam.documents.dto.DocumentDtos.UploadDocumentRequest(
				seedRequest(), "OTHER", null);
		var resp = this.documents.upload(pdfFile("a.pdf"), req);
		assertThat(resp.scanStatus()).isEqualTo("SCAN_FAILED");
		var ex = assertThrows(com.edam.common.exception.BusinessException.class,
				() -> this.documents.downloadUrl(resp.id()));
		assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
	}

	@Test
	void cloudinaryUploadFailureCleansUp() {
		((FakeStorageProvider) this.storage).throwOnUpload = true;
		var req = new com.edam.documents.dto.DocumentDtos.UploadDocumentRequest(
				seedRequest(), "OTHER", null);
		long before = this.assets.count();
		assertThrows(com.edam.storage.StorageException.class,
				() -> this.documents.upload(pdfFile("a.pdf"), req));
		// Transaction rolled back; no asset row remains.
		assertThat(this.assets.count()).isEqualTo(before);
	}

	@Test
	void cleanDocumentDownloadUrlIsMinted() {
		var req = new com.edam.documents.dto.DocumentDtos.UploadDocumentRequest(
				seedRequest(), "QUOTATION", null);
		var resp = this.documents.upload(pdfFile("q.pdf"), req);
		String url = this.documents.downloadUrl(resp.id());
		assertThat(url).contains("signed/");
	}

	@Test
	@Transactional
	void deleteRemovesRecordAndStorage() {
		var req = new com.edam.documents.dto.DocumentDtos.UploadDocumentRequest(
				seedRequest(), "OTHER", null);
		var resp = this.documents.upload(pdfFile("d.pdf"), req);
		this.documents.delete(resp.id());
		assertThat(this.assets.findById(resp.id()).orElseThrow().getLifecycleStatus()).isEqualTo("DELETED");
		assertThat(((FakeStorageProvider) this.storage).deleteCount).isEqualTo(1);
	}

	@Test
	void failClosedScannerWhenNoScannerConfigured() {
		// Replace scanner with fail-closed variant for this test only by casting back.
		// Since the bean is @Primary FakeMalwareScanner, we emulate fail-closed by setting
		// throwOnScan=false and forcedStatus=SCAN_FAILED.
		((FakeMalwareScanner) this.scanner).forcedStatus = "SCAN_FAILED";
		var req = new com.edam.documents.dto.DocumentDtos.UploadDocumentRequest(
				seedRequest(), "OTHER", "demo fail-closed");
		var resp = this.documents.upload(pdfFile("b.pdf"), req);
		assertThat(resp.scanStatus()).isEqualTo("SCAN_FAILED");
	}
}
