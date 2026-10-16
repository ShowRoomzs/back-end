package showroomz.domain.member.creator.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.member.creator.entity.CreatorWithdrawalRequest;
import showroomz.domain.member.creator.type.CreatorWithdrawalStatus;

import java.util.Optional;

public interface CreatorWithdrawalRequestRepository extends JpaRepository<CreatorWithdrawalRequest, Long> {

    Optional<CreatorWithdrawalRequest> findFirstByCreatorIdAndStatusOrderByRequestedAtDesc(Long creatorId,
                                                                                           CreatorWithdrawalStatus status);

    /** 승인된 신청 — 탈퇴 계정 상세의 신청일 · 승인자. */
    Optional<CreatorWithdrawalRequest> findFirstByCreatorIdAndStatusOrderByProcessedAtDesc(Long creatorId,
                                                                                           CreatorWithdrawalStatus status);

    boolean existsByCreatorIdAndStatus(Long creatorId, CreatorWithdrawalStatus status);

    /** 승인 · 반려 · 취소의 경합 차단 — 행을 잠그고 상태를 다시 본다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM CreatorWithdrawalRequest r WHERE r.id = :id")
    Optional<CreatorWithdrawalRequest> findForUpdate(@Param("id") Long id);
}
