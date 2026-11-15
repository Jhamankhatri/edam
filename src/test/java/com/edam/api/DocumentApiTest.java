package com.edam.api;

import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;
import com.edam.documents.FakeMalwareScanner;
import com.edam.documents.FakeStorageProvider;
import com.edam.documents.dto.DocumentDtos;
import com.edam.documents.dto.DocumentDtos.UploadDocumentRequest;
import com.edam.documents.repository.MediaAssetRepository;
import com.edam.documents.scan.MalwareScanner;
import com.edam.documents.service.DocumentService;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.expenserequest.repository.ExpenseRequestRepository;
import com.edam.expenserequest.repository.RequestDocumentRepository;
import com.edam.storage.StorageException;
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
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class DocumentApiTest extends AbstractPostgreSqlIntegrationTest {

	@TestConfiguration
	static class Fakes {
		@Bean @Primary StorageProvider fakeStorage() { return new FakeStorageProvider(); }
		@Bean @Primary MalwareScanner fakeScanner() {
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
	@Autowired private ExpenseRequestRepository requestRepo;

	@BeforeEach
	void resetFakes() {
		((FakeStorageProvider) this.storage).objects.clear();
		((FakeStorageProvider) this.storage).throwOnUpload = false;
		((FakeStorageProvider) this.storage).throwOnDelete = false;
		((FakeMalwareScanner) this.scanner).forcedStatus = "CLEAN";
		((FakeMalwareScanner) this.scanner).invocationCount = 0;
	}

	private MultipartFile pdfFile(String name) {
		return new MockMultipartFile("file", name, "application/pdf",
				("%PDF-1.4 fake pdf bytes for " + name).getBytes());
	}

	private java.util.UUID anyRequestId() {
		return this.requestRepo.findAll().stream().findFirst()
				.map(ExpenseRequest::getId)
				.orElseThrow(() -> new IllegalStateException("No seeded expense request present"));
	}

	@Test
	void uploadAndPersistMetadata() {
		java.util.UUID reqId = anyRequestId();
		var resp = this.documents.upload(pdfFile("receipt.pdf"),
				new UploadDocumentRequest(reqId, "SUPPORTING_DOCUMENT", "receipt"));
		assertThat(resp.id()).isNotNull();
		assertThat(resp.originalFilename()).isEqualTo("receipt.pdf");
		assertThat(resp.scanStatus()).isEqualTo("CLEAN");
		assertThat(resp.expenseRequestId()).isEqualTo(reqId);
		assertThat(((FakeStorageProvider) this.storage).objects).hasSize(1);
		assertThat(this.requestDocs.findByExpenseRequestId(reqId)).isNotEmpty();
	}

	@Test
	void rejectsUnsupportedExtension() {
		var ex = assertThrows(BusinessException.class,
				() -> this.documents.upload(
						new MockMultipartFile("file", "evil.exe", "application/octet-stream", new byte[]{0,0,0}),
						new UploadDocumentRequest(anyRequestId(), "OTHER", null)));
		assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_DOCUMENT);
	}

	@Test
	void rejectsPathTraversalFilename() {
		var ex = assertThrows(BusinessException.class,
				() -> this.documents.upload(pdfFile("../../etc/passwd.pdf"),
						new UploadDocumentRequest(anyRequestId(), "OTHER", null)));
		assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_DOCUMENT);
	}

	@Test
	void rejectsDownloadWhenInfected() {
		((FakeMalwareScanner) this.scanner).forcedStatus = "INFECTED";
		var resp = this.documents.upload(pdfFile("invoice.pdf"),
				new UploadDocumentRequest(anyRequestId(), "INVOICE", null));
		assertThat(resp.scanStatus()).isEqualTo("INFECTED");
		var ex = assertThrows(BusinessException.class, () -> this.documents.downloadUrl(resp.id()));
		assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
	}

	@Test
	void scanFailedRejectsDownload() {
		((FakeMalwareScanner) this.scanner).forcedStatus = "SCAN_FAILED";
		var resp = this.documents.upload(pdfFile("a.pdf"),
				new UploadDocumentRequest(anyRequestId(), "OTHER", null));
		assertThat(resp.scanStatus()).isEqualTo("SCAN_FAILED");
		var ex = assertThrows(BusinessException.class, () -> this.documents.downloadUrl(resp.id()));
		assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
	}

	@Test
	void cloudinaryUploadFailureRollsBack() {
		((FakeStorageProvider) this.storage).throwOnUpload = true;
		long before = this.assets.count();
		assertThrows(StorageException.class,
				() -> this.documents.upload(pdfFile("a.pdf"),
						new UploadDocumentRequest(anyRequestId(), "OTHER", null)));
		assertThat(this.assets.count()).isEqualTo(before);
	}

	@Test
	void cleanDocumentProducesSignedUrl() {
		var resp = this.documents.upload(pdfFile("q.pdf"),
				new UploadDocumentRequest(anyRequestId(), "QUOTATION", null));
		String url = this.documents.downloadUrl(resp.id());
		assertThat(url).contains("signed/").or().contains("fake.example");
	}

	@Test
	void deleteRemovesRecordAndCallsStorage() {
		var resp = this.documents.upload(pdfFile("d.pdf"),
				new UploadDocumentRequest(anyRequestId(), "OTHER", null));
		this.documents.delete(resp.id());
		assertThat(this.assets.findById(resp.id()).orElseThrow().getLifecycleStatus()).isEqualTo("DELETED");
		assertThat(((FakeStorageProvider) this.storage).deleteCount).isEqualTo(1);
	}

	@Test
	void failClosedScannerQuarantinesWhenScannerUnavailable() {
		((FakeMalwareScanner) this.scanner).forcedStatus = "SCAN_FAILED";
		var resp = this.documents.upload(pdfFile("b.pdf"),
				new UploadDocumentRequest(anyRequestId(), "OTHER", "fail-closed demo"));
		assertThat(resp.scanStatus()).isEqualTo("SCAN_FAILED");
	}
}
