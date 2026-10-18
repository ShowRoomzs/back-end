package showroomz.domain.member.brand.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.member.brand.entity.BrandWithdrawalRequest;
import showroomz.domain.member.common.type.MemberWithdrawalStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandWithdrawalRequestRepository extends JpaRepository<BrandWithdrawalRequest, Long> {

    Optional<BrandWithdrawalRequest> findFirstByMarketIdAndStatusOrderByRequestedAtDesc(Long marketId,
                                                                                         MemberWithdrawalStatus status);

    /** 승인된 신청 — 탈퇴 계정 상세의 신청일 · 사유 · 승인자. */
    Optional<BrandWithdrawalRequest> findFirstByMarketIdAndStatusOrderByProcessedAtDesc(Long marketId,
                                                                                         MemberWithdrawalStatus status);

    boolean existsByMarketIdAndStatus(Long marketId, MemberWithdrawalStatus status);

    /** 가장 최근 신청(상태 무관) — 파트너센터 계정 탭의 「최근 반려」 안내. */
    Optional<BrandWithdrawalRequest> findFirstByMarketIdOrderByRequestedAtDescIdDesc(Long marketId);

    /** 승인 · 반려 · 취소의 경합 차단 — 행을 잠그고 상태를 다시 본다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM BrandWithdrawalRequest r WHERE r.id = :id")
    Optional<BrandWithdrawalRequest> findForUpdate(@Param("id") Long id);

    @Query("SELECT r.marketId FROM BrandWithdrawalRequest r WHERE r.marketId IN :marketIds "
            + "AND r.status = showroomz.domain.member.common.type.MemberWithdrawalStatus.PENDING")
    List<Long> findPendingMarketIds(@Param("marketIds") Collection<Long> marketIds);

    @Query("SELECT COUNT(r) FROM BrandWithdrawalRequest r "
            + "WHERE r.status = showroomz.domain.member.common.type.MemberWithdrawalStatus.PENDING")
    long countPending();
}
