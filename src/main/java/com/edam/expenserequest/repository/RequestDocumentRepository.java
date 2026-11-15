package com.edam.expenserequest.repository;

import java.util.List;
import java.util.UUID;

import com.edam.expenserequest.model.RequestDocument;

import org.springframework.data.jpa.repository.JpaRepository;

/** Documents attached to requests (Module G/H bridge). */
public interface RequestDocumentRepository extends JpaRepository<RequestDocument, UUID> {

	List<RequestDocument> findByExpenseRequestId(UUID expenseRequestId);

	java.util.Optional<RequestDocument> findFirstByMediaAssetId(UUID mediaAssetId);

	boolean existsByExpenseRequestIdAndDocumentType(UUID expenseRequestId, String documentType);

	boolean existsByExpenseRequestIdAndMediaAssetId(UUID expenseRequestId, UUID mediaAssetId);

}
