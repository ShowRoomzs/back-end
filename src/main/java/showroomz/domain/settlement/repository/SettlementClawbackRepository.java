package showroomz.domain.settlement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.settlement.entity.SettlementClawback;
import showroomz.domain.settlement.type.ClawbackStatus;
import showroomz.domain.settlement.type.ClawbackUnrecoverableReason;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 차감 행(44 어드민 설계서 1-7 · 6절). 반영 · 미회수 전이는 <b>조건부 UPDATE</b>(PENDING 에서만)다.
 */
public interface SettlementClawbackRepository extends JpaRepository<SettlementClawback, Long> {

    boolean existsByRefundTaskId(Long refundTaskId);

    List<SettlementClawback> findByRefundTaskIdOrderByIdAsc(Long refundTaskId);

    List<SettlementClawback> findByDeliveryGroupIdOrderByIdAsc(Long deliveryGroupId);

    List<SettlementClawback> findByAppliedSettlementIdOrderByIdAsc(Long appliedSettlementId);

    List<SettlementClawback> findByOriginSettlementIdOrderByIdAsc(Long originSettlementId);

    List<SettlementClawback> findByClawbackNumberInOrderByIdAsc(Collection<String> clawbackNumbers);

    @Query("SELECT COALESCE(MAX(c.seq), 0) FROM SettlementClawback c WHERE c.clawbackNumber = :clawbackNumber "
            + "AND c.side = :side")
    int maxSeq(@Param("clawbackNumber") String clawbackNumber,
               @Param("side") showroomz.domain.settlement.type.ClawbackSide side);

    /** 번호 · 측의 금액 합(이월 seq 포함) — DB 값을 읽는다(같은 트랜잭션의 조건부 UPDATE 뒤에도 정확하다). */
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM SettlementClawback c WHERE c.clawbackNumber = :clawbackNumber "
            + "AND c.side = :side")
    long sumAmount(@Param("clawbackNumber") String clawbackNumber,
                   @Param("side") showroomz.domain.settlement.type.ClawbackSide side);

    /** 회수 대상(2-5) — 브랜드 측은 같은 마켓 · 오래된 순. */
    @Query("SELECT c FROM SettlementClawback c WHERE c.status = showroomz.domain.settlement.type.ClawbackStatus.PENDING "
            + "AND c.side = showroomz.domain.settlement.type.ClawbackSide.BRAND AND c.marketId = :marketId ORDER BY c.id ASC")
    List<SettlementClawback> findPendingBrand(@Param("marketId") Long marketId);

    /** 회수 대상(2-5) — 인플루언서 측은 같은 인플루언서 · 오래된 순. */
    @Query("SELECT c FROM SettlementClawback c WHERE c.status = showroomz.domain.settlement.type.ClawbackStatus.PENDING "
            + "AND c.side = showroomz.domain.settlement.type.ClawbackSide.CREATOR AND c.creatorId = :creatorId ORDER BY c.id ASC")
    List<SettlementClawback> findPendingCreator(@Param("creatorId") Long creatorId);

    /** 반영(2-5) — 부분 반영이면 금액을 반영분으로 줄인다(남은 금액은 이월 행). */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE SettlementClawback c SET c.status = showroomz.domain.settlement.type.ClawbackStatus.APPLIED, "
            + "c.amount = :amount, c.appliedSettlementId = :settlementId, c.appliedAt = :appliedAt "
            + "WHERE c.id = :clawbackId AND c.status = showroomz.domain.settlement.type.ClawbackStatus.PENDING")
    int apply(@Param("clawbackId") Long clawbackId, @Param("amount") long amount,
              @Param("settlementId") Long settlementId, @Param("appliedAt") LocalDateTime appliedAt);

    /** 합의로 리워드가 반영된 차감보다 작아졌다(4-3) — 반영분을 줄인다(남은 금액은 이월 행). */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE SettlementClawback c SET c.amount = :amount WHERE c.id = :clawbackId "
            + "AND c.status = showroomz.domain.settlement.type.ClawbackStatus.APPLIED")
    int reduceApplied(@Param("clawbackId") Long clawbackId, @Param("amount") long amount);

    /** 미회수(3-6). */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE SettlementClawback c SET c.status = showroomz.domain.settlement.type.ClawbackStatus.UNRECOVERABLE, "
            + "c.unrecoverableReason = :reason, c.unrecoverableAt = :now "
            + "WHERE c.id = :clawbackId AND c.status = showroomz.domain.settlement.type.ClawbackStatus.PENDING")
    int markUnrecoverable(@Param("clawbackId") Long clawbackId, @Param("reason") ClawbackUnrecoverableReason reason,
                          @Param("now") LocalDateTime now);

    /** 미회수 대상(3-6) — 인플루언서 측 · 계정 탈퇴. */
    @Query("SELECT c FROM SettlementClawback c, Creator cr WHERE cr.id = c.creatorId "
            + "AND c.status = showroomz.domain.settlement.type.ClawbackStatus.PENDING "
            + "AND c.side = showroomz.domain.settlement.type.ClawbackSide.CREATOR "
            + "AND cr.user.status = showroomz.domain.member.user.type.UserStatus.WITHDRAWN ORDER BY c.id ASC")
    List<SettlementClawback> findPendingOfWithdrawnCreators();

    /** 미회수 대상(3-6) — 브랜드 측 · 마켓 탈퇴. */
    @Query("SELECT c FROM SettlementClawback c, Market m WHERE m.id = c.marketId "
            + "AND c.status = showroomz.domain.settlement.type.ClawbackStatus.PENDING "
            + "AND c.side = showroomz.domain.settlement.type.ClawbackSide.BRAND "
            + "AND m.status = showroomz.domain.market.type.MarketStatus.WITHDRAWN ORDER BY c.id ASC")
    List<SettlementClawback> findPendingOfWithdrawnMarkets();

    /** 07a 차감 탭 — 번호 단위(측별 행을 한 줄로 합친다) · 발생일 최신순. */
    @Query("SELECT c FROM SettlementClawback c ORDER BY c.createdAt DESC, c.id DESC")
    List<SettlementClawback> findAllLatestFirst();

    List<SettlementClawback> findByStatus(ClawbackStatus status);

    @Query("SELECT COUNT(c) > 0 FROM SettlementClawback c WHERE c.creatorId = :creatorId "
            + "AND c.side = showroomz.domain.settlement.type.ClawbackSide.CREATOR "
            + "AND c.status = showroomz.domain.settlement.type.ClawbackStatus.PENDING")
    boolean existsPendingForCreator(@Param("creatorId") Long creatorId);

    @Query("SELECT COUNT(c) > 0 FROM SettlementClawback c WHERE c.marketId = :marketId "
            + "AND c.side = showroomz.domain.settlement.type.ClawbackSide.BRAND "
            + "AND c.status = showroomz.domain.settlement.type.ClawbackStatus.PENDING")
    boolean existsPendingForMarket(@Param("marketId") Long marketId);
}
