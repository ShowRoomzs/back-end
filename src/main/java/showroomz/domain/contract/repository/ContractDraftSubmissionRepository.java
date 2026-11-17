package showroomz.domain.contract.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.contract.entity.ContractDraftSubmission;
import showroomz.domain.contract.type.ContractDraftSubmissionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ContractDraftSubmissionRepository extends JpaRepository<ContractDraftSubmission, Long> {

    List<ContractDraftSubmission> findByContractIdOrderBySubmittedAtAscIdAsc(Long contractId);

    Optional<ContractDraftSubmission> findByIdAndContractId(Long id, Long contractId);

    /** 승인 간주 대상 — 회신 대기 ∧ 회신 기한(수령 + 3영업일) 경과. */
    @Query("SELECT s.id FROM ContractDraftSubmission s WHERE s.status = showroomz.domain.contract.type.ContractDraftSubmissionStatus.PENDING "
            + "AND s.reviewDueAt < :now ORDER BY s.reviewDueAt ASC, s.id ASC")
    List<Long> findIdsDueForDeemedApproval(@Param("now") LocalDateTime now, Pageable pageable);

    /** 회신 — 회신 대기일 때만. 0행이면 그사이 다른 회신 · 승인 간주가 먼저 반영됐다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ContractDraftSubmission s SET s.status = :to, s.decidedAt = :now, s.decisionNote = :note, "
            + "s.approvalId = :approvalId WHERE s.id = :id "
            + "AND s.status = showroomz.domain.contract.type.ContractDraftSubmissionStatus.PENDING")
    int decide(@Param("id") Long id, @Param("to") ContractDraftSubmissionStatus to, @Param("now") LocalDateTime now,
               @Param("note") String note, @Param("approvalId") Long approvalId);
}
