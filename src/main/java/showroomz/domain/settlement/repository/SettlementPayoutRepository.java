package showroomz.domain.settlement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 3자 분배 행. 상태 · 계좌 · 결과의 쓰기는 <b>전부 조건부 UPDATE</b>다(44 어드민 설계서 3-3 · 7-5) — 0행이면 다른 요청이 먼저
 * 바꿨다. UPDATE 뒤에는 영속성 컨텍스트를 비운다 — 같은 트랜잭션에서 다시 읽는 행이 낡은 값이면 상태 파생이 틀린다.
 */
public interface SettlementPayoutRepository extends JpaRepository<SettlementPayout, Long> {

    /** 정산의 수취자 행 — 생성 순서(BRAND · CREATOR · PLATFORM). */
    @Query("SELECT p FROM SettlementPayout p WHERE p.settlementId = :settlementId ORDER BY p.id ASC")
    List<SettlementPayout> findBySettlementId(@Param("settlementId") Long settlementId);

    @Query("SELECT p FROM SettlementPayout p WHERE p.settlementId IN :settlementIds ORDER BY p.id ASC")
    List<SettlementPayout> findBySettlementIdIn(@Param("settlementIds") Collection<Long> settlementIds);

    Optional<SettlementPayout> findBySettlementIdAndPayee(Long settlementId, SettlementPayee payee);

    /** 상태만 — 확정(WAITING · HELD → …) · 조정 보류(WAITING → HELD) · 증빙 확인(BLOCKED → SCHEDULED). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.status = :to, p.dueDate = :dueDate "
            + "WHERE p.id = :payoutId AND p.status IN :from")
    int updateStatusWhere(@Param("payoutId") Long payoutId,
                          @Param("from") Collection<PayoutStatus> from,
                          @Param("to") PayoutStatus to,
                          @Param("dueDate") LocalDate dueDate);

    /** 정산의 전 수취자 행을 한 번에 — 조정 협의 보류(전 수취자 WAITING → HELD · 4-2). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.status = :to WHERE p.settlementId = :settlementId AND p.status IN :from")
    int updateAllStatusWhere(@Param("settlementId") Long settlementId,
                             @Param("from") Collection<PayoutStatus> from,
                             @Param("to") PayoutStatus to);

    // ------------------------------------------------------------------ 요약(파트너 · 스튜디오 KPI)

    /** 마켓의 수취자 행 집계 — [상태, 건수, 금액 합, 가장 이른 예정일]. 파트너는 브랜드 행만 본다. */
    @Query("SELECT p.status, COUNT(p), COALESCE(SUM(p.amount), 0), MIN(p.dueDate) "
            + "FROM SettlementPayout p, Settlement s WHERE p.settlementId = s.id "
            + "AND s.market.id = :marketId AND p.payee = :payee GROUP BY p.status")
    List<Object[]> aggregateForMarket(@Param("marketId") Long marketId, @Param("payee") SettlementPayee payee);

    /** 인플루언서의 수취자 행 집계 — 스튜디오는 내 행(CREATOR)만 본다. */
    @Query("SELECT p.status, COUNT(p), COALESCE(SUM(p.amount), 0), MIN(p.dueDate) "
            + "FROM SettlementPayout p, Settlement s WHERE p.settlementId = s.id "
            + "AND s.creator.id = :creatorId AND p.payee = :payee GROUP BY p.status")
    List<Object[]> aggregateForCreator(@Param("creatorId") Long creatorId, @Param("payee") SettlementPayee payee);

    // ------------------------------------------------------------------ 지급(3-3)

    /** 지급 배치 대상 — 예정일이 된 지급 예정 행이 있는 정산. */
    @Query("SELECT DISTINCT p.settlementId FROM SettlementPayout p "
            + "WHERE p.status = showroomz.domain.settlement.type.PayoutStatus.SCHEDULED AND p.dueDate <= :today "
            + "ORDER BY p.settlementId ASC")
    List<Long> findSettlementIdsDue(@Param("today") LocalDate today, org.springframework.data.domain.Pageable pageable);

    /** PG 결과 대기(REQUESTED) 행이 있는 정산 — PG 모드의 결과 폴링(3-3). */
    @Query("SELECT DISTINCT p.settlementId FROM SettlementPayout p "
            + "WHERE p.status = showroomz.domain.settlement.type.PayoutStatus.REQUESTED ORDER BY p.settlementId ASC")
    List<Long> findSettlementIdsRequested(org.springframework.data.domain.Pageable pageable);

    /** 보류 재판정 대상(5-4) — 인플루언서 행 BLOCKED. */
    @Query("SELECT p FROM SettlementPayout p WHERE p.payee = showroomz.domain.settlement.type.SettlementPayee.CREATOR "
            + "AND p.status = showroomz.domain.settlement.type.PayoutStatus.BLOCKED ORDER BY p.id ASC")
    List<SettlementPayout> findBlockedCreatorPayouts(org.springframework.data.domain.Pageable pageable);

    /**
     * 확정 — 브랜드 계좌 고정(「이미 확정된 정산 회차는 기존 계좌로 지급」 · 기본정보 §16-4). 지시 전(SCHEDULED)이고 아직
     * 스냅샷이 없을 때만 — 지시({@link #markRequested})는 이 값을 그대로 쓴다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.bankName = :bankName, p.accountNumberEnc = :accountNumberEnc, "
            + "p.accountHolder = :accountHolder "
            + "WHERE p.id = :payoutId AND p.status = showroomz.domain.settlement.type.PayoutStatus.SCHEDULED "
            + "AND p.accountNumberEnc IS NULL")
    int pinAccount(@Param("payoutId") Long payoutId, @Param("bankName") String bankName,
                   @Param("accountNumberEnc") String accountNumberEnc, @Param("accountHolder") String accountHolder);

    /** 지시 — 계좌 스냅샷(브랜드는 확정 때 고정한 값 · 그 밖은 지시 시점 · 0-5)과 함께 SCHEDULED → REQUESTED. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.status = showroomz.domain.settlement.type.PayoutStatus.REQUESTED, "
            + "p.bankName = :bankName, p.accountNumberEnc = :accountNumberEnc, p.accountHolder = :accountHolder, "
            + "p.requestedAt = :requestedAt "
            + "WHERE p.id = :payoutId AND p.status = showroomz.domain.settlement.type.PayoutStatus.SCHEDULED")
    int markRequested(@Param("payoutId") Long payoutId, @Param("bankName") String bankName,
                      @Param("accountNumberEnc") String accountNumberEnc, @Param("accountHolder") String accountHolder,
                      @Param("requestedAt") LocalDateTime requestedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.status = showroomz.domain.settlement.type.PayoutStatus.PAID, "
            + "p.pgReference = :pgReference, p.paidAt = :paidAt, p.failCode = NULL, p.failReason = NULL "
            + "WHERE p.id = :payoutId AND p.status IN (showroomz.domain.settlement.type.PayoutStatus.SCHEDULED, "
            + "    showroomz.domain.settlement.type.PayoutStatus.REQUESTED)")
    int markPaid(@Param("payoutId") Long payoutId, @Param("pgReference") String pgReference,
                 @Param("paidAt") LocalDateTime paidAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.status = showroomz.domain.settlement.type.PayoutStatus.FAILED, "
            + "p.pgReference = COALESCE(:pgReference, p.pgReference), p.failedAt = :failedAt, "
            + "p.failCode = :failCode, p.failReason = :failReason "
            + "WHERE p.id = :payoutId AND p.status IN (showroomz.domain.settlement.type.PayoutStatus.SCHEDULED, "
            + "    showroomz.domain.settlement.type.PayoutStatus.REQUESTED)")
    int markFailed(@Param("payoutId") Long payoutId, @Param("pgReference") String pgReference,
                   @Param("failCode") String failCode, @Param("failReason") String failReason,
                   @Param("failedAt") LocalDateTime failedAt);

    /** PG 가 결과를 미뤘다 — 참조번호만 적고 REQUESTED 로 둔다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.pgReference = :pgReference "
            + "WHERE p.id = :payoutId AND p.status = showroomz.domain.settlement.type.PayoutStatus.REQUESTED")
    int recordReference(@Param("payoutId") Long payoutId, @Param("pgReference") String pgReference);

    /**
     * 재분배(7-5 M3) — FAILED → SCHEDULED(오늘) · 횟수 + 1 · 계좌 재스냅샷(현재 회원 정보면 새 값 · 이전 계좌면 그대로).
     * 금액은 손대지 않는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.status = showroomz.domain.settlement.type.PayoutStatus.SCHEDULED, "
            + "p.dueDate = :dueDate, p.attempt = p.attempt + 1, "
            + "p.bankName = :bankName, p.accountNumberEnc = :accountNumberEnc, p.accountHolder = :accountHolder "
            + "WHERE p.id = :payoutId AND p.status = showroomz.domain.settlement.type.PayoutStatus.FAILED "
            + "AND p.attempt < :retryLimit")
    int reschedule(@Param("payoutId") Long payoutId, @Param("dueDate") LocalDate dueDate,
                   @Param("bankName") String bankName, @Param("accountNumberEnc") String accountNumberEnc,
                   @Param("accountHolder") String accountHolder, @Param("retryLimit") int retryLimit);

    /** 합의 재계산(4-3) — 금액만 덮어쓴다. 확정 전(WAITING · HELD)에만. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.amount = :amount "
            + "WHERE p.settlementId = :settlementId AND p.payee = :payee "
            + "AND p.status IN (showroomz.domain.settlement.type.PayoutStatus.WAITING, "
            + "                 showroomz.domain.settlement.type.PayoutStatus.HELD)")
    int updateAmountBeforeConfirm(@Param("settlementId") Long settlementId,
                                  @Param("payee") SettlementPayee payee,
                                  @Param("amount") long amount);

    // ------------------------------------------------------------------ 어드민 07a 분배 실패 툴바(7-2)

    /** 분배 실패 행 집계 — [payee, count, amount, earliestFailedAt]. 「미지급 합계 · 실패 수취자 · 최장 경과」. */
    @Query("SELECT p.payee, COUNT(p), COALESCE(SUM(p.amount), 0), MIN(p.failedAt) FROM SettlementPayout p "
            + "WHERE p.status = showroomz.domain.settlement.type.PayoutStatus.FAILED GROUP BY p.payee")
    List<Object[]> aggregateFailed();
}
