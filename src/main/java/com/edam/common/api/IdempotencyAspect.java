package com.edam.common.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.edam.common.exception.BusinessException;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.InstitutionContext;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import tools.jackson.databind.json.JsonMapper;

/**
 * Around aspect that enforces {@link IdempotentOperation} semantics on controller methods.
 *
 * <p>The aspect runs inside the controller's transaction but <em>before</em> the body: the
 * decision (proceed vs replay) is made before the method body runs, and the record is finalised
 * after the method returns or throws. Because the idempotency INSERT uses
 * {@code Propagation.REQUIRES_NEW} semantics at the service boundary, the claim survives the
 * caller's rollback — which is what we want: a failed attempt is marked FAILED so the next retry
 * can proceed, while a successful commit is marked COMPLETED.
 */
@Aspect
@Component
public class IdempotencyAspect {

	private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

	private static final Logger log = LoggerFactory.getLogger(IdempotencyAspect.class);

	private final IdempotencyService idempotency;
	private final InstitutionContext institutionContext;
	private final JsonMapper jsonMapper;

	public IdempotencyAspect(IdempotencyService idempotency, InstitutionContext institutionContext,
			JsonMapper jsonMapper) {
		this.idempotency = idempotency;
		this.institutionContext = institutionContext;
		this.jsonMapper = jsonMapper;
	}

	@Around("@annotation(idempotentOperation)")
	public Object enforce(ProceedingJoinPoint joinPoint, IdempotentOperation idempotentOperation) throws Throwable {
		HttpServletRequest request = currentRequest();
		String rawKey = request == null ? null : request.getHeader(IDEMPOTENCY_HEADER);
		// Trim once and reuse; calling key.trim() in some places but not others caused the claim
		// and finalizer to hash different strings (leading to orphaned IN_PROGRESS rows that
		// broke replay semantics).
		String key = rawKey == null ? null : rawKey.trim();
		if (key == null || key.isEmpty()) {
			return joinPoint.proceed();
		}
		EdamPrincipal principal = this.institutionContext.principal();
		if (principal == null) {
			log.warn("idempotency.no_principal header_present=true path={}", request != null ? request.getRequestURI() : "?");
			return joinPoint.proceed();
		}
		UUID actorId = principal.userId();
		Map<String, String> uriVariables = extractPathVariables(joinPoint, request);
		String fingerprint = RequestFingerprint.forRequest(this.jsonMapper, request, uriVariables);
		String operation = idempotentOperation.value();
		IdempotencyService.Decision decision = this.idempotency.check(operation, actorId, key, fingerprint);
		if (decision instanceof IdempotencyService.Replay replay) {
			// Replay bypasses the controller, so we must re-enforce the method-level permission
			// here. The actorId scoping of the key lookup already proves the caller is the same
			// user who originally made the request, but that user's permissions may have been
			// revoked in the interim — fail closed.
			enforceReplayAuthorization(joinPoint, principal);
			return writeReplay(replay);
		}
		// Proceed: invoke the controller and finalise the record based on outcome.
		Object result;
		try {
			result = joinPoint.proceed();
		}
		catch (Throwable t) {
			this.idempotency.markFailed(operation, actorId, key);
			throw t;
		}
		int httpStatus = resolveStatus(result, request);
		String snapshot = serialiseResult(result);
		this.idempotency.recordSuccess(operation, actorId, key, httpStatus, snapshot);
		return result;
	}

	private Object writeReplay(IdempotencyService.Replay replay) throws IOException {
		HttpServletResponse response = currentResponse();
		if (response == null) {
			// Should not happen in a Servlet dispatcher; fall back to a 500-safe exception.
			throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Idempotency replay failed.");
		}
		response.setStatus(replay.httpStatus());
		response.setContentType("application/json");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		if (replay.responseJson() != null) {
			byte[] body = replay.responseJson().getBytes(StandardCharsets.UTF_8);
			response.setContentLength(body.length);
			response.getOutputStream().write(body);
			response.getOutputStream().flush();
		}
		// Do NOT proceed to the controller — return null; Spring MVC ignores the return value because
		// the response has already been committed.
		return null;
	}

	private static int resolveStatus(Object result, HttpServletRequest request) {
		if (result instanceof ResponseEntity<?> re) {
			return re.getStatusCode().value();
		}
		// Controllers returning ApiResponse directly default to 200 unless annotated; the most
		// common case (POST /expense-requests create) uses ResponseEntity with CREATED, but for
		// safety fall back to 200.
		return HttpStatus.OK.value();
	}

	private String serialiseResult(Object result) {
		try {
			Object body = result;
			if (result instanceof ResponseEntity<?> re) {
				body = re.getBody();
			}
			return this.jsonMapper.writeValueAsString(body);
		}
		catch (Exception ex) {
			log.warn("idempotency.snapshot_failed", ex);
			return null;
		}
	}

	/**
	 * Extract path variables from the handler mapping or fall back to parsing method arguments by
	 * name and type (UUID/String path parameters). This is intentionally best-effort: the
	 * fingerprint includes the URI itself, but path variables are added explicitly so that
	 * {@code POST /requests/A} and {@code POST /requests/B} under the same operation name hash
	 * differently.
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, String> extractPathVariables(ProceedingJoinPoint joinPoint, HttpServletRequest request) {
		Map<String, String> vars = new LinkedHashMap<>();
		Object attr = request.getAttribute(
				"org.springframework.web.servlet.HandlerMapping.uriTemplateVariables");
		if (attr instanceof Map<?, ?> map) {
			for (Map.Entry<?, ?> e : map.entrySet()) {
				if (e.getKey() != null && e.getValue() != null) {
					vars.put(e.getKey().toString(), e.getValue().toString());
				}
			}
		}
		return vars;
	}

	private static HttpServletRequest currentRequest() {
		var attrs = RequestContextHolder.getRequestAttributes();
		if (attrs instanceof ServletRequestAttributes sra) {
			return sra.getRequest();
		}
		return null;
	}

	private static HttpServletResponse currentResponse() {
		var attrs = RequestContextHolder.getRequestAttributes();
		if (attrs instanceof ServletRequestAttributes sra) {
			return sra.getResponse();
		}
		return null;
	}

	/**
	 * Re-verify the caller still holds the {@code @EdamPermission} declared on the controller
	 * method before serving a replay. We deliberately re-read the annotation here rather than
	 * re-invoking the entire {@code RequirePermissionAspect} chain so the replay path cannot
	 * accidentally reach the controller body, but the permission semantics match exactly: the
	 * same permission code the original caller needed is required again on replay.
	 *
	 * <p>If the method has no {@code @EdamPermission} (unexpected for an idempotent endpoint
	 * given the arch-test guard), we refuse the replay rather than leaking data — deny-by-default.
	 */
	private static void enforceReplayAuthorization(ProceedingJoinPoint joinPoint, EdamPrincipal principal) {
		if (!(joinPoint.getSignature() instanceof MethodSignature sig)) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorCode.PERMISSION_DENIED.safeMessage());
		}
		EdamPermission permission = sig.getMethod().getAnnotation(EdamPermission.class);
		if (permission == null) {
			// Try class-level annotation as well (rare but supported by the annotation target).
			permission = joinPoint.getTarget().getClass().getAnnotation(EdamPermission.class);
		}
		if (permission == null || !principal.hasPermission(permission.value())) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorCode.PERMISSION_DENIED.safeMessage());
		}
	}

}
