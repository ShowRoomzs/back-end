package showroomz.domain.settlement.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.SettlementStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long>, SettlementRepositoryCustom {

    @Query("SELECT s FROM Settlement s WHERE s.groupBuy.id = :groupBuyId")
    Optional<Settlement> findByGroupBuyId(@Param("groupBuyId") Long groupBuyId);

    /** 상세 — 공구 · 계약 · 브랜드 · 인플루언서를 함께 올린다. */
    @Query("SELECT s FROM Settlement s "
            + "JOIN FETCH s.groupBuy g "
            + "JOIN FETCH s.contract c "
            + "JOIN FETCH s.market m "
            + "JOIN FETCH s.creator cr "
            + "WHERE s.id = :settlementId")
    Optional<Settlement> findDetailById(@Param("settlementId") Long settlementId);

    /** 정산 · 수취자 행을 함께 바꾸는 쓰기(확정 · 지급 결과 · 재분배)의 행 잠금 — 조건부 UPDATE 앞의 선검사를 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Settlement s WHERE s.id = :settlementId")
    Optional<Settlement> findForUpdate(@Param("settlementId") Long settlementId);

    /**
     * 모든 상태 전이는 조건부 UPDATE(44 어드민 설계서 3-1) — 0행이면 다른 요청이 먼저 상태를 바꿨다(409 SETTLEMENT_STATE_CHANGED).
     * 자동 확정 배치와 조정 요청이 겹쳐도 하나만 통과한다.
     *
     * <p>status 만 건드리고 version 은 올리지 않는다 — 딸린 필드는 같은 트랜잭션에서 엔티티 변경 감지로 쓰인다(공구와 같은 방식).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Settlement s SET s.status = :to WHERE s.id = :settlementId AND s.status IN :from")
    int transition(@Param("settlementId") Long settlementId,
                   @Param("from") Collection<SettlementStatus> from,
                   @Param("to") SettlementStatus to);

    /**
     * 조정 요청의 보류 전이(4-2 {@code holdForAdjustment}) — 상태에 더해 <b>확인 마감이 지나지 않았는지</b>를 UPDATE 조건으로 다시 본다.
     * 마감 직후 요청과 자동 확정이 겹쳐도 하나만 통과한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Settlement s SET s.status = showroomz.domain.settlement.type.SettlementStatus.ADJUSTING "
            + "WHERE s.id = :settlementId "
            + "AND s.status = showroomz.domain.settlement.type.SettlementStatus.REVIEWING "
            + "AND s.reviewDueAt >= :now")
    int holdIfInReviewWindow(@Param("settlementId") Long settlementId, @Param("now") LocalDateTime now);

    // ------------------------------------------------------------------ 파트너 · 스튜디오 요약(KPI · 상태 칩 · GNB)

    @Query("SELECT s.status, COUNT(s) FROM Settlement s WHERE s.market.id = :marketId GROUP BY s.status")
    List<Object[]> countByStatusForMarket(@Param("marketId") Long marketId);

    @Query("SELECT s.status, COUNT(s) FROM Settlement s WHERE s.creator.id = :creatorId GROUP BY s.status")
    List<Object[]> countByStatusForCreator(@Param("creatorId") Long creatorId);

    /** 요청 가능 창이 열린 정산 확인 중 — [건수, 가장 임박한 마감]. GNB 「정산 관리」 배지(공통 결정 #19). */
    @Query("SELECT COUNT(s), MIN(s.reviewDueAt) FROM Settlement s WHERE s.market.id = :marketId "
            + "AND s.status = showroomz.domain.settlement.type.SettlementStatus.REVIEWING AND s.reviewDueAt >= :now")
    List<Object[]> reviewWindowForMarket(@Param("marketId") Long marketId, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(s), MIN(s.reviewDueAt) FROM Settlement s WHERE s.creator.id = :creatorId "
            + "AND s.status = showroomz.domain.settlement.type.SettlementStatus.REVIEWING AND s.reviewDueAt >= :now")
    List<Object[]> reviewWindowForCreator(@Param("creatorId") Long creatorId, @Param("now") LocalDateTime now);

    /** 「지급 예정」 KPI 의 차감 표시 — 브랜드 행이 예정인 정산 중 차감이 반영된 것이 있는가. */
    @Query("SELECT COUNT(s) > 0 FROM Settlement s, SettlementPayout p WHERE p.settlementId = s.id "
            + "AND s.market.id = :marketId AND s.brandClawbackAmount > 0 "
            + "AND p.payee = showroomz.domain.settlement.type.SettlementPayee.BRAND "
            + "AND p.status = showroomz.domain.settlement.type.PayoutStatus.SCHEDULED")
    boolean existsScheduledWithBrandClawback(@Param("marketId") Long marketId);

    // ------------------------------------------------------------------ 배치(3절)

    /** 자동 확정 대상(3-2) — 확인 마감이 지난 정산 확인 중. */
    @Query("SELECT s.id FROM Settlement s "
            + "WHERE s.status = showroomz.domain.settlement.type.SettlementStatus.REVIEWING AND s.reviewDueAt < :now "
            + "ORDER BY s.reviewDueAt ASC, s.id ASC")
    List<Long> findIdsToAutoConfirm(@Param("now") LocalDateTime now, Pageable pageable);

    /**
     * 생성 대상(2-1) — 종료(ENDED)됐고 정산이 없으며 결제된 하위주문이 하나라도 있는 공구. 판매 0건 공구는 정산을 만들지 않으므로
     * (공통 결정 #16) 처음부터 고르지 않는다 — 영구 미생성 공구가 쌓여 회차 상한을 잡아먹지 않게.
     */
    @Query("SELECT g.id FROM GroupBuy g "
            + "WHERE g.status = showroomz.domain.groupbuy.type.GroupBuyStatus.ENDED "
            + "AND NOT EXISTS (SELECT s.id FROM Settlement s WHERE s.groupBuy = g) "
            + "AND EXISTS (SELECT d.id FROM OrderDeliveryGroup d WHERE d.groupBuy = g AND d.order.paidAt IS NOT NULL) "
            + "ORDER BY g.endedAt ASC, g.id ASC")
    List<Long> findGroupBuyIdsToGenerate(Pageable pageable);

    // ------------------------------------------------------------------ 어드민 07a(7-3)

    /** 상태별 건수 — [status, count]. 탭 숫자 · GNB 배지. */
    @Query("SELECT s.status, COUNT(s) FROM Settlement s GROUP BY s.status")
    List<Object[]> countAllByStatus();
}
