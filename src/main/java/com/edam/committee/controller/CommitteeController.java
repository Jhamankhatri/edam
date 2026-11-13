package com.edam.committee.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.edam.committee.dto.CommitteeDtos.AddMemberRequest;
import com.edam.committee.dto.CommitteeDtos.CommitteeCoverageResponse;
import com.edam.committee.dto.CommitteeDtos.CommitteeLevelRequest;
import com.edam.committee.dto.CommitteeDtos.CommitteeLevelResponse;
import com.edam.committee.dto.CommitteeDtos.CommitteeMemberResponse;
import com.edam.committee.dto.CommitteeDtos.CommitteeResponse;
import com.edam.committee.dto.CommitteeDtos.SaveCommitteeRequest;
import com.edam.committee.service.CommitteeService;
import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.Permissions;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expense committees (Module F).
 *
 * <h2>Why there is a coverage endpoint</h2>
 * {@code GET /committees/coverage} answers "who would decide an amount today, and could they?" without
 * creating a request. Configuring a committee is the single most consequential administrative act in EDAM: a
 * band whose quorum is unreachable, or a band that accidentally overlaps another, will silently stall every
 * payment above a threshold. The endpoint lets an administrator prove the configuration before money depends
 * on it, and it uses the same band-matching code the request module will use.
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/committees", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Expense committees", description = "Committees, amount bands, seats and quorum reachability")
public class CommitteeController {

	private final CommitteeService committees;

	public CommitteeController(CommitteeService committees) {
		this.committees = committees;
	}

	@GetMapping
	@EdamPermission(Permissions.COMMITTEE_VIEW)
	@Operation(summary = "List committees with their levels and members")
	public ApiResponse<List<CommitteeResponse>> list(@RequestParam(defaultValue = "false") boolean includeInactive,
			HttpServletRequest request) {
		return ApiResponse.success(this.committees.list(includeInactive), CorrelationId.current(),
				request.getRequestURI());
	}

	@GetMapping("/{id}")
	@EdamPermission(Permissions.COMMITTEE_VIEW)
	@Operation(summary = "Get one committee")
	public ApiResponse<CommitteeResponse> get(@PathVariable UUID id, HttpServletRequest request) {
		return ApiResponse.success(this.committees.get(id), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping
	@EdamPermission(Permissions.COMMITTEE_MANAGE)
	@Operation(summary = "Create a committee")
	public ResponseEntity<ApiResponse<CommitteeResponse>> create(@Valid @RequestBody SaveCommitteeRequest body,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(this.committees.create(body), CorrelationId.current(), request.getRequestURI()));
	}

	@PostMapping("/{id}")
	@EdamPermission(Permissions.COMMITTEE_MANAGE)
	@Operation(summary = "Rename a committee",
			description = "The committee code is immutable: published workflows and delegation rules refer to it.")
	public ApiResponse<CommitteeResponse> rename(@PathVariable UUID id, @Valid @RequestBody SaveCommitteeRequest body,
			HttpServletRequest request) {
		return ApiResponse.success(this.committees.rename(id, body), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/{id}/deactivate")
	@EdamPermission(Permissions.COMMITTEE_MANAGE)
	@Operation(summary = "Deactivate a committee and its levels")
	public ApiResponse<Void> deactivate(@PathVariable UUID id, HttpServletRequest request) {
		this.committees.deactivate(id);
		return ApiResponse.success(CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/{id}/members")
	@EdamPermission(Permissions.COMMITTEE_MANAGE)
	@Operation(summary = "Add a member",
			description = "At most one active CEO, CFO and head of administration is permitted per committee.")
	public ResponseEntity<ApiResponse<CommitteeMemberResponse>> addMember(@PathVariable UUID id,
			@Valid @RequestBody AddMemberRequest body, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(this.committees.addMember(id, body), CorrelationId.current(),
					request.getRequestURI()));
	}

	@PostMapping("/{id}/members/{memberId}/end")
	@EdamPermission(Permissions.COMMITTEE_MANAGE)
	@Operation(summary = "End a membership",
			description = "Refused while the member is seated on an active level, because that would silently "
					+ "disable an approval path.")
	public ApiResponse<Void> endMembership(@PathVariable UUID id, @PathVariable UUID memberId,
			HttpServletRequest request) {
		this.committees.endMembership(id, memberId);
		return ApiResponse.success(CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/{id}/levels")
	@EdamPermission(Permissions.COMMITTEE_MANAGE)
	@Operation(summary = "Add an amount band with its seats",
			description = "A band whose quorum exceeds its seats is refused: it could never approve anything.")
	public ResponseEntity<ApiResponse<CommitteeLevelResponse>> addLevel(@PathVariable UUID id,
			@Valid @RequestBody CommitteeLevelRequest body, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(this.committees.addLevel(id, body), CorrelationId.current(),
					request.getRequestURI()));
	}

	@PostMapping("/{id}/levels/{levelId}/deactivate")
	@EdamPermission(Permissions.COMMITTEE_MANAGE)
	@Operation(summary = "Deactivate an amount band")
	public ApiResponse<Void> deactivateLevel(@PathVariable UUID id, @PathVariable UUID levelId,
			HttpServletRequest request) {
		this.committees.deactivateLevel(id, levelId);
		return ApiResponse.success(CorrelationId.current(), request.getRequestURI());
	}

	@GetMapping("/coverage")
	@EdamPermission(Permissions.COMMITTEE_VIEW)
	@Operation(summary = "Report which band would decide an amount, and whether it can reach its quorum")
	public ApiResponse<List<CommitteeCoverageResponse>> coverage(@RequestParam UUID currencyId,
			@RequestParam BigDecimal amount, HttpServletRequest request) {
		return ApiResponse.success(this.committees.coverage(currencyId, amount), CorrelationId.current(),
				request.getRequestURI());
	}

}
