package com.edam.common.api;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * The single paginated payload shape for every EDAM list API.
 *
 * <p>BRD API-03 requires consistent pagination, sorting and bounded page sizes so the future
 * React client does not have to special-case each endpoint. {@link ApiResponse} wraps this
 * wrapper: {@code { success, data: { items, page, size, totalElements, totalPages, hasNext } }}.
 *
 * <p>The shape is intentionally explicit rather than Spring's serialised {@code Page}, whose
 * JSON form is not a stable contract (it changes between Spring Data versions) and which leaks
 * internal field names.
 *
 * @param <T> row type
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages,
		boolean hasNext, boolean hasPrevious) {

	public PageResponse {
		items = (items == null) ? List.of() : List.copyOf(items);
	}

	public static <T> PageResponse<T> of(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages(), page.hasNext(), page.hasPrevious());
	}

	/** Map the rows while preserving the paging metadata. */
	public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages(), page.hasNext(), page.hasPrevious());
	}

}
