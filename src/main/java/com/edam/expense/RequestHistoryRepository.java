package com.edam.expense;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Append-only request history. */
public interface RequestHistoryRepository extends JpaRepository<RequestHistoryEntry, Long> {

	List<RequestHistoryEntry> findByInstitutionIdAndRequestIdOrderBySequenceNumberAsc(Long institutionId,
			Long requestId);

	@Query("select coalesce(max(h.sequenceNumber), 0) from RequestHistoryEntry h where h.requestId = :requestId")
	int maxSequence(@Param("requestId") Long requestId);

}
