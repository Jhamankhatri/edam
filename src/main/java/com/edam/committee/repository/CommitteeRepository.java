package com.edam.committee.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.committee.model.ExpenseCommittee;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Committee aggregate access. Levels, members and seatings are reached through the aggregate rather than
 * through separate repositories, because they have no independent lifecycle: a level cannot exist without its
 * committee, and a seating cannot exist without both.
 */
public interface CommitteeRepository extends JpaRepository<ExpenseCommittee, UUID> {

	Optional<ExpenseCommittee> findByCommitteeCode(String committeeCode);

	boolean existsByCommitteeCode(String committeeCode);

	List<ExpenseCommittee> findByActiveTrueOrderByCommitteeNameAsc();

	List<ExpenseCommittee> findAllByOrderByCommitteeNameAsc();

}
