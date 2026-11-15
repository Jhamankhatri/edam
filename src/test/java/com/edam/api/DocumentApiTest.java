package com.edam.api;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.DataScope;
import com.edam.common.security.Permissions;
import com.edam.documents.FakeMalwareScanner;
import com.edam.documents.FakeStorageProvider;
import com.edam.documents.dto.DocumentDtos;
import com.edam.documents.dto.DocumentDtos.UploadDocumentRequest;
import com.edam.documents.repository.MediaAssetRepository;
import com.edam.documents.scan.MalwareScanner;
import com.edam.documents.service.DocumentService;
import com.edam.expenseconfiguration.model.ExpenseCategory;
import com.edam.expenseconfiguration.model.ExpenseCategory.ExpenseType;
import com.edam.expenseconfiguration.model.ExpenseNature;
import com.edam.expenseconfiguration.model.ExpenseRequestType;
import com.edam.expenseconfiguration.repository.ExpenseCategoryRepository;
import com.edam.expenseconfiguration.repository.ExpenseNatureRepository;
import com.edam.expenseconfiguration.repository.ExpenseRequestTypeRepository;
import com.edam.expenserequest.dto.ExpenseRequestDtos;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.expenserequest.repository.ExpenseRequestRepository;
import com.edam.expenserequest.repository.RequestDocumentRepository;
import com.edam.expenserequest.service.ExpenseRequestService;
import com.edam.masterdata.model.Currency;
import com.edam.masterdata.repository.CurrencyRepository;
import com.edam.organization.model.Department;
import com.edam.organization.repository.DepartmentRepository;
import com.edam.organization.repository.OrganizationProfileRepository;
import com.edam.organization.repository.OrganizationUnitRepository;
import com.edam.organization.repository.OrganizationUnitTypeRepository;
import com.edam.storage.StorageException;
import com.edam.storage.StorageProvider;
import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;
import com.edam.testsupport.RequestFixtures;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class DocumentApiTest extends AbstractPostgreSqlIntegrationTest {

	@TestConfiguration
	static class Fakes {
		@Primary
		@Bean StorageProvider fakeStorage() { return new FakeStorageProvider(); }
		@Primary
		@Bean MalwareScanner fakeScanner() {
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
	@Autowired private ExpenseRequestService requestService;
	@Autowired private UserRepository users;
	@Autowired private OrganizationProfileRepository profiles;
	@Autowired private CurrencyRepository currencies;
	@Autowired private DepartmentRepository departments;
	@Autowired private OrganizationUnitRepository units;
	@Autowired private OrganizationUnitTypeRepository unitTypes;
	@Autowired private ExpenseRequestTypeRepository requestTypes;
	@Autowired private ExpenseCategoryRepository categories;
	@Autowired private ExpenseNatureRepository natures;

	private UUID requestId;
	private EdamPrincipal requester;

	@BeforeEach
	void setUp() {
		((FakeStorageProvider) this.storage).objects.clear();
		((FakeStorageProvider) this.storage).throwOnUpload = false;
		((FakeStorageProvider) this.storage).throwOnDelete = false;
		((FakeStorageProvider) this.storage).deleteCount = 0;
		((FakeMalwareScanner) this.scanner).throwOnScan = false;
		((FakeMalwareScanner) this.scanner).forcedStatus = "CLEAN";
		((FakeMalwareScanner) this.scanner).invocationCount = 0;

		// Seed a minimal organization/department/user and a draft OPEX request so upload has a
		// target, then authenticate as the requester for the duration of each test.
		var profile = RequestFixtures.organizationProfile(this.profiles, this.currencies);
		Currency currency = this.currencies.findById(profile.getDefaultCurrencyId()).orElseThrow();
		Department dept = RequestFixtures.department(this.departments, this.units, this.unitTypes);
		User user = RequestFixtures.user(this.users, dept.getId());
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		ExpenseRequestType rt = RequestFixtures.requestType(this.requestTypes, "DOC-" + suffix);
		ExpenseCategory cat = RequestFixtures.category(this.categories, "DOC-CAT-" + suffix, ExpenseType.OPEX);
		ExpenseNature nat = RequestFixtures.nature(this.natures, "DOC-NAT-" + suffix);
		this.requester = new EdamPrincipal(user.getId(), user.getEmployeeCode(), user.getUsername(),
				"Tester", Set.of(),
				Set.of(Permissions.DOCUMENT_UPLOAD, Permissions.DOCUMENT_DOWNLOAD, Permissions.DOCUMENT_DELETE,
						Permissions.REQUEST_CREATE, Permissions.REQUEST_VIEW_OWN),
				Set.of(new DataScope(DataScope.Type.DEPARTMENT, dept.getId())));
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(this.requester, null, this.requester.getAuthorities()));
		ExpenseRequestDtos.RequestResponse created = this.requestService.create(
				new ExpenseRequestDtos.CreateRequest(
						rt.getId(), cat.getId(), null, nat.getId(),
						dept.getId(), null, currency.getId(),
						RequestFixtures.money("500.0000"), "document test",
						new ExpenseRequestDtos.OpexDetailPayload(true, "MONTHLY", null, null, null, null,
								null, null, null, null, null, null, null, null),
						null, java.util.List.of(), java.util.List.of(), java.util.List.of(),
						java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
						java.util.List.of()), this.requester);
		this.requestId = created.id();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	private MultipartFile pdfFile(String name) {
		return new MockMultipartFile("file", name, "application/pdf",
				("%PDF-1.4 fake pdf bytes for " + name).getBytes());
	}

	private UUID anyRequestId() { return this.requestId; }

	@Test
	void uploadAndPersistMetadata() {
		var resp = this.documents.upload(pdfFile("receipt.pdf"),
				new UploadDocumentRequest(anyRequestId(), "SUPPORTING_DOCUMENT", "receipt"));
		assertThat(resp.id()).isNotNull();
		assertThat(resp.originalFilename()).isEqualTo("receipt.pdf");
		assertThat(((FakeStorageProvider) this.storage).objects).hasSize(1);
	}

	@Test
	void rejectsUnsupportedExtension() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> this.documents.upload(pdfFile("evil.exe"),
						new UploadDocumentRequest(anyRequestId(), "OTHER", null)));
		assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_DOCUMENT);
	}

	@Test
	void rejectsPathTraversalFilename() {
		// File names that contain path separators are rejected outright (traversal attempt).
		var traversal = new MockMultipartFile("file", "../../etc/passwd", "application/pdf",
				"%PDF-1.4 malicious".getBytes());
		BusinessException ex = assertThrows(BusinessException.class,
				() -> this.documents.upload(traversal,
						new UploadDocumentRequest(anyRequestId(), "OTHER", null)));
		assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_DOCUMENT);
	}

	@Test
	void rejectsDownloadWhenInfected() {
		((FakeMalwareScanner) this.scanner).forcedStatus = "INFECTED";
		var resp = this.documents.upload(pdfFile("invoice.pdf"),
				new UploadDocumentRequest(anyRequestId(), "INVOICE", null));
		assertThrows(BusinessException.class, () -> this.documents.downloadUrl(resp.id()));
	}

	@Test
	void cloudinaryUploadFailureRollsBack() {
		((FakeStorageProvider) this.storage).throwOnUpload = true;
		assertThrows(StorageException.class,
				() -> this.documents.upload(pdfFile("fail.pdf"),
						new UploadDocumentRequest(anyRequestId(), "OTHER", null)));
	}

	@Test
	void cleanDocumentProducesSignedUrl() {
		// Explicitly reset scanner to CLEAN for this case (tests may run out of order).
		((FakeMalwareScanner) this.scanner).throwOnScan = false;
		((FakeMalwareScanner) this.scanner).forcedStatus = "CLEAN";
		var resp = this.documents.upload(pdfFile("clean.pdf"),
				new UploadDocumentRequest(anyRequestId(), "QUOTATION", null));
		String url = this.documents.downloadUrl(resp.id());
		assertThat(url).startsWith("https://fake.example/signed/");
	}

	@Test
	void scanFailedRejectsDownload() {
		((FakeMalwareScanner) this.scanner).forcedStatus = "SCAN_FAILED";
		var resp = this.documents.upload(pdfFile("dirty.pdf"),
				new UploadDocumentRequest(anyRequestId(), "OTHER", "fail-closed demo"));
		assertThrows(BusinessException.class, () -> this.documents.downloadUrl(resp.id()));
	}

	@Test
	void failClosedScannerQuarantinesWhenScannerUnavailable() {
		// With the @Primary FakeMalwareScanner in the test context forced to throw, the
		// upload must still record a SCAN_FAILED event rather than silently trusting the file.
		((FakeMalwareScanner) this.scanner).throwOnScan = true;
		var resp = this.documents.upload(pdfFile("q.pdf"),
				new UploadDocumentRequest(anyRequestId(), "OTHER", null));
		assertThat(this.assets.findById(resp.id())).isPresent();
		assertThrows(BusinessException.class, () -> this.documents.downloadUrl(resp.id()));
	}

	@Test
	void deleteRemovesRecordAndCallsStorage() {
		var resp = this.documents.upload(pdfFile("del.pdf"),
				new UploadDocumentRequest(anyRequestId(), "OTHER", null));
		this.documents.delete(resp.id());
		// Request document link is retained for audit; the asset is soft-deleted.
		assertThat(this.assets.findById(resp.id())).isPresent().get()
				.extracting(a -> a.getLifecycleStatus()).isEqualTo("DELETED");
		assertThat(((FakeStorageProvider) this.storage).deleteCount).isEqualTo(1);
	}
}
