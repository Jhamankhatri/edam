package com.edam.reporting.controller;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.reporting.dto.ReportingDtos;
import com.edam.reporting.dto.ReportingDtos.DashboardSummary;
import com.edam.reporting.dto.ReportingDtos.ExportFormat;
import com.edam.reporting.dto.ReportingDtos.RegisterResponse;
import com.edam.reporting.dto.ReportingDtos.ReportFilter;
import com.edam.reporting.service.ReportingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboards and the expense-request register (Phase 4F).
 *
 * <h2>No arbitrary queries</h2>
 * Every widget, column and aggregation is enumerated in {@link ReportingService}.
 * Endpoints accept discrete filter values only; there is no free-text expression, SQL,
 * or column selector.
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/reporting", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Reporting", description = "Dashboards, register reports and exports")
public class ReportingController {

	private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	private final ReportingService reporting;

	public ReportingController(ReportingService reporting) {
		this.reporting = reporting;
	}

	@GetMapping("/dashboard")
	@EdamPermission(Permissions.REPORT_VIEW)
	@Operation(summary = "Dashboard summary metrics",
			description = "Scalar cards plus status, department, category, expense-type and daily-trend "
					+ "distributions, scoped to the caller. Monetary figures use base_currency_amount "
					+ "captured at submission; no FX re-conversion is performed.")
	public ApiResponse<DashboardSummary> dashboard(
			@RequestParam(required = false) UUID departmentId,
			@RequestParam(required = false) UUID branchId,
			@RequestParam(required = false) UUID categoryId,
			@RequestParam(required = false) UUID subcategoryId,
			@RequestParam(required = false) UUID requesterId,
			@RequestParam(required = false) String expenseType,
			@RequestParam(required = false) String currency,
			@RequestParam(required = false) ExpenseRequest.Status status,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) Integer lookbackDays,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		ReportFilter filter = new ReportFilter(departmentId, branchId, categoryId, subcategoryId,
				requesterId, expenseType, status, from, to, currency, search);
		return ok(this.reporting.dashboard(caller, filter, lookbackDays), request);
	}

	@GetMapping("/widgets")
	@EdamPermission(Permissions.DASHBOARD_MANAGE)
	@Operation(summary = "Catalogue of supported dashboard widget keys")
	public ApiResponse<List<String>> widgetCatalogue(HttpServletRequest request) {
		return ok(ReportingService.WIDGET_CATALOGUE, request);
	}

	@GetMapping("/reports")
	@EdamPermission(Permissions.DASHBOARD_MANAGE)
	@Operation(summary = "Catalogue of supported report keys")
	public ApiResponse<List<String>> reportCatalogue(HttpServletRequest request) {
		return ok(ReportingService.REPORT_CATALOGUE, request);
	}

	@GetMapping("/expense-requests/register")
	@EdamPermission(Permissions.REPORT_VIEW)
	@Operation(summary = "Expense-request register (paged)",
			description = "Scoped, filtered, paged register. Default sort submittedAt desc; page size capped at 500. "
					+ "Free-text search matches request number and justification.")
	public ApiResponse<RegisterResponse> register(
			@RequestParam(required = false) UUID departmentId,
			@RequestParam(required = false) UUID branchId,
			@RequestParam(required = false) UUID categoryId,
			@RequestParam(required = false) UUID subcategoryId,
			@RequestParam(required = false) UUID requesterId,
			@RequestParam(required = false) String expenseType,
			@RequestParam(required = false) ExpenseRequest.Status status,
			@RequestParam(required = false) String currency,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@RequestParam(required = false) String search,
			@PageableDefault(size = 50, sort = "submittedAt",
					direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		ReportFilter filter = new ReportFilter(departmentId, branchId, categoryId, subcategoryId,
				requesterId, expenseType, status, from, to, currency, search);
		return ok(this.reporting.register(caller, filter, pageable), request);
	}

	@PostMapping("/expense-requests/export")
	@EdamPermission(Permissions.REPORT_EXPORT)
	@Operation(summary = "Export the expense-request register",
			description = "Exports the register using the same filters, scope and inclusive date semantics as the "
					+ "register endpoint. Formats: CSV (RFC 4180 with UTF-8 BOM and formula-injection "
					+ "prefixes), EXCEL (.xlsx via streaming SXSSF). Returns 400 if the matching set exceeds "
					+ ReportingService.EXPORT_ROW_LIMIT + " rows.")
	public void export(@RequestBody ReportingDtos.ExportRequest body,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletResponse response) throws IOException {
		ReportFilter filter = body != null ? body.filter() : null;
		ExportFormat fmt = (body == null || body.format() == null) ? ExportFormat.CSV : body.format();

		// Enforce row cap before streaming starts, using the same scope/filter as the actual export.
		long matched = this.reporting.countForExport(caller, filter);
		if (matched > ReportingService.EXPORT_ROW_LIMIT) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Export matches " + matched + " rows; maximum is " + ReportingService.EXPORT_ROW_LIMIT
							+ ". Narrow the filters and retry.");
		}

		switch (fmt) {
			case CSV -> {
				response.setContentType("text/csv; charset=UTF-8");
				response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"expense-request-register.csv\"");
				// Stream directly to the response; buffer the writer but never accumulate the whole CSV.
				try (BufferedWriter w = new BufferedWriter(
						new OutputStreamWriter(response.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8),
						64 * 1024)) {
					this.reporting.streamCsv(caller, filter, w, "CSV");
					w.flush();
				}
			}
			case EXCEL -> {
				response.setContentType(XLSX);
				response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"expense-request-register.xlsx\"");
				// SXSSF streams to temp files; we stream straight to the HTTP output stream.
				this.reporting.streamExcel(caller, filter, response.getOutputStream());
				response.getOutputStream().flush();
			}
			default -> throw new IllegalArgumentException("Unsupported export format: " + fmt);
		}
	}

	private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
		return ApiResponse.success(data, CorrelationId.current(), request.getRequestURI());
	}
}
