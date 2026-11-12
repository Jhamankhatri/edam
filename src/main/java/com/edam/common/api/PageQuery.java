package com.edam.common.api;

import com.edam.common.exception.BusinessException;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Validated paging input for list endpoints.
 *
 * <p>Bounded by design (BRD API-03): a caller cannot request an unbounded page, which would let a
 * single request pull an entire tenant's ledger. The maximum is a product limit, not a
 * performance tuning knob, so it lives here rather than in configuration that could be raised by
 * accident.
 *
 * @param page zero-based page index
 * @param size page size, capped at {@value #MAX_SIZE}
 * @param sort sort expression, {@code property,asc|desc} (validated against an allow-list by the caller)
 */
public record PageQuery(int page, int size, String sort) {

	public static final int DEFAULT_SIZE = 20;

	public static final int MAX_SIZE = 200;

	/** Restrict sorting to properties the endpoint actually exposes, so a client cannot sort by
	 *  an unindexed or internal column. */
	public Pageable toPageable(Sort allowedSort) {
		int safePage = Math.max(page, 0);
		int safeSize = (size <= 0) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
		if (page < 0 || size > MAX_SIZE) {
			throw new BusinessException(com.edam.common.api.ErrorCode.INVALID_PAGINATION,
					"Page size must be between 1 and " + MAX_SIZE + ".");
		}
		return PageRequest.of(safePage, safeSize, (allowedSort == null) ? Sort.unsorted() : allowedSort);
	}

	public static PageQuery of(Integer page, Integer size, String sort) {
		return new PageQuery(page == null ? 0 : page, size == null ? DEFAULT_SIZE : size, sort);
	}

}
