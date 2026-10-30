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

    /**
     * 브랜드 몫이 끝나지 않은 정산 건수 — 탈퇴 조건 ④(51 설계서 1-5 · D-1). 정산마다 브랜드 행은 1개라 행 수 = 정산 수다. 정산 상태로 세면
     * 브랜드 몫은 이미 지급됐는데 인플루언서 몫 보류 · 분배 실패로 탈퇴가 막힌다.
     */
    @Query("SELECT COUNT(p) FROM SettlementPayout p, Settlement s WHERE p.settlementId = s.id AND s.market.id = :marketId "
            + "AND p.payee = showroomz.domain.settlement.type.SettlementPayee.BRAND "
            + "AND p.status NOT IN (showroomz.domain.settlement.type.PayoutStatus.PAID, "
            + "showroomz.domain.settlement.type.PayoutStatus.NOT_APPLICABLE)")
    long countBrandOpenForMarket(@Param("marketId") Long marketId);

    /**
     * 위 건수의 내역 — [정산 상태, 브랜드 행 상태, 보류 사유, 건수, 가장 이른 지급 예정일, 가장 이른 확인 기한]. 같은 WHERE 라 내역 합계가 판정과
     * 어긋나지 않는다(51 설계서 4-4).
     */
    @Query("SELECT s.status, p.status, p.failCode, COUNT(p), MIN(p.dueDate), MIN(s.reviewDueAt) "
            + "FROM SettlementPayout p, Settlement s WHERE p.settlementId = s.id AND s.market.id = :marketId "
            + "AND p.payee = showroomz.domain.settlement.type.SettlementPayee.BRAND "
            + "AND p.status NOT IN (showroomz.domain.settlement.type.PayoutStatus.PAID, "
            + "showroomz.domain.settlement.type.PayoutStatus.NOT_APPLICABLE) "
            + "GROUP BY s.status, p.status, p.failCode")
    List<Object[]> countBrandOpenByStatus(@Param("marketId") Long marketId);

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

    // ------------------------------------------------------------------ 토스 지급대행(44 토스 설계서 5절)

    Optional<SettlementPayout> findByPgPayoutId(String pgPayoutId);

    Optional<SettlementPayout> findByPgTransferId(String pgTransferId);

    /** 지급 요청 성공(5-2) — REQUESTED 행에 참조 id · PG 지급 id(= 참조번호) · 유형 · 지급일을 적는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.pgTransferId = :pgTransferId, p.pgPayoutId = :pgPayoutId, p.pgReference = :pgPayoutId, "
            + "p.pgScheduleType = :scheduleType, p.pgPayoutDate = :payoutDate, p.failCode = NULL, p.failReason = NULL "
            + "WHERE p.id = :payoutId AND p.status = showroomz.domain.settlement.type.PayoutStatus.REQUESTED")
    int recordRequest(@Param("payoutId") Long payoutId, @Param("pgTransferId") String pgTransferId,
                      @Param("pgPayoutId") String pgPayoutId, @Param("scheduleType") String scheduleType,
                      @Param("payoutDate") LocalDate payoutDate);

    /** 잔액 부족(5-1 ①) — 지시를 되돌려 다음 영업일 예정으로. attempt 는 그대로. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.status = showroomz.domain.settlement.type.PayoutStatus.SCHEDULED, "
            + "p.dueDate = :dueDate, p.requestedAt = NULL, p.pgTransferId = NULL, p.pgPayoutId = NULL, p.pgReference = NULL, "
            + "p.pgScheduleType = NULL, p.pgPayoutDate = NULL, p.failCode = NULL, p.failReason = NULL "
            + "WHERE p.id = :payoutId AND p.status = showroomz.domain.settlement.type.PayoutStatus.REQUESTED")
    int deferRequested(@Param("payoutId") Long payoutId, @Param("dueDate") LocalDate dueDate);

    /** 본인인증 · KYC 필요(5-2 ④) — 지시한 행을 FAILED 대신 BLOCKED 로. 수취자가 풀면 다시 예정으로. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.status = showroomz.domain.settlement.type.PayoutStatus.BLOCKED, "
            + "p.dueDate = NULL, p.requestedAt = NULL, p.pgTransferId = NULL, p.pgPayoutId = NULL, p.pgReference = NULL, "
            + "p.pgScheduleType = NULL, p.pgPayoutDate = NULL, p.failCode = :failCode, p.failReason = :failReason "
            + "WHERE p.id = :payoutId AND p.status = showroomz.domain.settlement.type.PayoutStatus.REQUESTED")
    int blockRequested(@Param("payoutId") Long payoutId, @Param("failCode") String failCode,
                       @Param("failReason") String failReason);

    /** 오늘 예정 행의 정산별 합계(플랫폼 몫 제외) — 잔액 선검사(5-1 ①). [settlementId, sum]. */
    @Query("SELECT p.settlementId, COALESCE(SUM(p.amount), 0) FROM SettlementPayout p "
            + "WHERE p.settlementId IN :settlementIds AND p.status = showroomz.domain.settlement.type.PayoutStatus.SCHEDULED "
            + "AND p.dueDate <= :today AND p.payee <> showroomz.domain.settlement.type.SettlementPayee.PLATFORM "
            + "GROUP BY p.settlementId")
    List<Object[]> sumDueBySettlement(@Param("settlementIds") Collection<Long> settlementIds, @Param("today") LocalDate today);

    /** 정산의 오늘 예정 행(플랫폼 몫 제외) — 연기 대상. */
    @Query("SELECT p FROM SettlementPayout p WHERE p.settlementId = :settlementId "
            + "AND p.status = showroomz.domain.settlement.type.PayoutStatus.SCHEDULED AND p.dueDate <= :today "
            + "AND p.payee <> showroomz.domain.settlement.type.SettlementPayee.PLATFORM ORDER BY p.id ASC")
    List<SettlementPayout> findScheduledDue(@Param("settlementId") Long settlementId, @Param("today") LocalDate today);

    /** 브랜드의 최근 지시 · 지급 합계 — 주당 한도 선검사(5-2 ①). */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM SettlementPayout p, Settlement s WHERE p.settlementId = s.id "
            + "AND s.market.id = :marketId AND p.payee = showroomz.domain.settlement.type.SettlementPayee.BRAND "
            + "AND p.status IN (showroomz.domain.settlement.type.PayoutStatus.REQUESTED, "
            + "    showroomz.domain.settlement.type.PayoutStatus.PAID) AND p.requestedAt >= :since")
    long sumRecentForMarket(@Param("marketId") Long marketId, @Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM SettlementPayout p, Settlement s WHERE p.settlementId = s.id "
            + "AND s.creator.id = :creatorId AND p.payee = showroomz.domain.settlement.type.SettlementPayee.CREATOR "
            + "AND p.status IN (showroomz.domain.settlement.type.PayoutStatus.REQUESTED, "
            + "    showroomz.domain.settlement.type.PayoutStatus.PAID) AND p.requestedAt >= :since")
    long sumRecentForCreator(@Param("creatorId") Long creatorId, @Param("since") LocalDateTime since);

    /**
     * 한 회원 몫의 보류 행 중 주어진 사유 코드 — PG 셀러 사유는 {@code seller.changed} 승인이 즉시 푼다(4-3). 수취자 · 참조 id 로 고른다
     * (브랜드 = {@code market_id} · 인플루언서 = {@code creator_id} · 정합 계획서 R2-1).
     */
    @Query("SELECT p FROM SettlementPayout p, Settlement s WHERE p.settlementId = s.id "
            + "AND p.payee = :payee AND ((p.payee = showroomz.domain.settlement.type.SettlementPayee.BRAND AND s.market.id = :refId) OR (p.payee = showroomz.domain.settlement.type.SettlementPayee.CREATOR AND s.creator.id = :refId)) "
            + "AND p.status = showroomz.domain.settlement.type.PayoutStatus.BLOCKED AND p.failCode IN :failCodes ORDER BY p.id ASC")
    List<SettlementPayout> findBlockedFor(@Param("payee") SettlementPayee payee, @Param("refId") Long refId,
                                          @Param("failCodes") Collection<String> failCodes);

    /** 한 회원 몫의 지급 예정 행 — 계정 정지 · 계좌 확인 · 셀러 대기(KYC_REQUIRED)가 보류로 옮긴다. */
    @Query("SELECT p FROM SettlementPayout p, Settlement s WHERE p.settlementId = s.id "
            + "AND p.payee = :payee AND ((p.payee = showroomz.domain.settlement.type.SettlementPayee.BRAND AND s.market.id = :refId) OR (p.payee = showroomz.domain.settlement.type.SettlementPayee.CREATOR AND s.creator.id = :refId)) "
            + "AND p.status = showroomz.domain.settlement.type.PayoutStatus.SCHEDULED ORDER BY p.id ASC")
    List<SettlementPayout> findScheduledFor(@Param("payee") SettlementPayee payee, @Param("refId") Long refId);

    /** 참조 id 만 적는다(결과 모름 · 지시 중 통신 실패 뒤 복구) — 결과 조회 중 적는 메모(failCode)도 같은 자리. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.pgTransferId = COALESCE(:pgTransferId, p.pgTransferId), "
            + "p.failCode = :failCode, p.failReason = :failReason "
            + "WHERE p.id = :payoutId AND p.status = showroomz.domain.settlement.type.PayoutStatus.REQUESTED")
    int recordTransfer(@Param("payoutId") Long payoutId, @Param("pgTransferId") String pgTransferId,
                       @Param("failCode") String failCode, @Param("failReason") String failReason);

    /** PG 결과 조회로 닫는 지급 완료 — 지급 id 를 참조번호와 함께 적는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.status = showroomz.domain.settlement.type.PayoutStatus.PAID, "
            + "p.pgReference = :pgReference, p.pgPayoutId = :pgReference, p.paidAt = :paidAt, "
            + "p.failCode = NULL, p.failReason = NULL "
            + "WHERE p.id = :payoutId AND p.status IN (showroomz.domain.settlement.type.PayoutStatus.SCHEDULED, "
            + "    showroomz.domain.settlement.type.PayoutStatus.REQUESTED)")
    int markPaidByPg(@Param("payoutId") Long payoutId, @Param("pgReference") String pgReference,
                     @Param("paidAt") LocalDateTime paidAt);

    /**
     * PG 셀러 사유 보류(4-3) — 확정된 행을 사유와 함께 보류. 예정일은 지운다(재판정이 다시 정한다). <b>영속성 컨텍스트를 비운다</b> — PG 호출
     * 결과를 반영하는 짧은 트랜잭션 전용이다. 회원 사유(계정 정지 · 계좌 확인) 보류는 {@link #holdScheduled}를 쓴다({@code PayoutHoldTransitions}).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.status = showroomz.domain.settlement.type.PayoutStatus.BLOCKED, "
            + "p.dueDate = NULL, p.failCode = :failCode, p.failReason = :failReason "
            + "WHERE p.id = :payoutId AND p.status = showroomz.domain.settlement.type.PayoutStatus.SCHEDULED")
    int blockForPartner(@Param("payoutId") Long payoutId, @Param("failCode") String failCode,
                        @Param("failReason") String failReason);

    /**
     * 회원 사유 지급 보류(계정 정지 · 계좌 확인 · 52 설계서 6-1) — {@link #blockForPartner}와 같은 전이지만 <b>영속성 컨텍스트를 비우지
     * 않는다</b>. 정지 · 확정 트랜잭션이 붙들고 있는 마켓 · 정지 행이 준영속으로 떨어지면 그 뒤 변경이 저장되지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.status = showroomz.domain.settlement.type.PayoutStatus.BLOCKED, "
            + "p.dueDate = NULL, p.failCode = :failCode, p.failReason = :failReason "
            + "WHERE p.id = :payoutId AND p.status = showroomz.domain.settlement.type.PayoutStatus.SCHEDULED")
    int holdScheduled(@Param("payoutId") Long payoutId, @Param("failCode") String failCode,
                      @Param("failReason") String failReason);

    /**
     * 회원 사유 보류 해제(52 설계서 6-2 · 53 설계서 4-3) — 그 사유로 보류된 행을 SCHEDULED(예정일)로. {@code includeUncoded} 면 확정 때 정책
     * 보류로 사유 없이 BLOCKED 가 된 행(인플루언서 몫)도 함께 올린다. 영속성 컨텍스트는 비우지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.status = showroomz.domain.settlement.type.PayoutStatus.SCHEDULED, "
            + "p.dueDate = :dueDate, p.failCode = NULL, p.failReason = NULL "
            + "WHERE p.id = :payoutId AND p.status = showroomz.domain.settlement.type.PayoutStatus.BLOCKED "
            + "AND (p.failCode = :failCode OR (:includeUncoded = true AND p.failCode IS NULL))")
    int releaseHold(@Param("payoutId") Long payoutId, @Param("failCode") String failCode,
                    @Param("includeUncoded") boolean includeUncoded, @Param("dueDate") LocalDate dueDate);

    /** 이미 보류 중인 행의 사유 갱신(주민등록번호 보류에 파트너 사유를 덧붙일 때 · 재시도 실패 사유 변경). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.failCode = :failCode, p.failReason = :failReason "
            + "WHERE p.id = :payoutId AND p.status = showroomz.domain.settlement.type.PayoutStatus.BLOCKED")
    int updateBlockReason(@Param("payoutId") Long payoutId, @Param("failCode") String failCode,
                          @Param("failReason") String failReason);

    /** 보류 해제 — 사유를 지우고 지급 예정으로. 다른 보류 사유(주민등록번호)가 남으면 호출자가 사유만 지운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.status = :to, p.dueDate = :dueDate, p.failCode = NULL, p.failReason = NULL "
            + "WHERE p.id = :payoutId AND p.status = showroomz.domain.settlement.type.PayoutStatus.BLOCKED")
    int releaseBlocked(@Param("payoutId") Long payoutId, @Param("to") PayoutStatus to, @Param("dueDate") LocalDate dueDate);

    /** 파트너 사유로 보류된 행(수취자 무관) — 지급 배치 앞단 재판정 대상. */
    @Query("SELECT p FROM SettlementPayout p WHERE p.status = showroomz.domain.settlement.type.PayoutStatus.BLOCKED "
            + "AND p.failCode IN :failCodes ORDER BY p.id ASC")
    List<SettlementPayout> findBlockedWithFailCodes(@Param("failCodes") Collection<String> failCodes,
                                                     org.springframework.data.domain.Pageable pageable);

    /** 결과 대기 행 — 정산 단위로 묶어 PG 에 조회한다. */
    @Query("SELECT p FROM SettlementPayout p WHERE p.status = showroomz.domain.settlement.type.PayoutStatus.REQUESTED "
            + "ORDER BY p.settlementId ASC, p.id ASC")
    List<SettlementPayout> findRequested(org.springframework.data.domain.Pageable pageable);

    /**
     * PG 지급일이 그날인 행 수 · 합계 — 대조 알림(토스 설계서 5-3). PG 목록({@code payoutDateGte = Lte = D})과 같은 기준이라 ① 웹훅이 배치 직후
     * 이미 PAID · FAILED 로 닫은 행 ② 전날 창 밖 재분배로 올라간 SCHEDULED({@code pg_payout_date = D}) 행이 빠지지 않는다. 결과 모름 행
     * ({@code pg_payout_id} 없음)은 PG 에 있는지 모르니 세지 않는다 — 어긋남이 그 행을 가리킨다. [count, sum].
     */
    @Query("SELECT COUNT(p), COALESCE(SUM(p.amount), 0) FROM SettlementPayout p "
            + "WHERE p.pgPayoutDate = :payoutDate AND p.pgPayoutId IS NOT NULL "
            + "AND p.status IN (showroomz.domain.settlement.type.PayoutStatus.REQUESTED, "
            + "    showroomz.domain.settlement.type.PayoutStatus.PAID, showroomz.domain.settlement.type.PayoutStatus.FAILED)")
    List<Object[]> countByPgPayoutDate(@Param("payoutDate") LocalDate payoutDate);

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

    // ------------------------------------------------------------------ 인플루언서 계정 정지(53 설계서 4-3)

    /**
     * 회원 사유 해제가 다시 판정할 보류 행 — 그 사유 코드 행(+ {@code includeUncoded} 면 확정 때 정책 보류로 사유 없이 BLOCKED 가 된 행).
     * PG 셀러 사유 행은 셀러 재판정이 푼다(제외).
     */
    @Query("SELECT p FROM SettlementPayout p, Settlement s WHERE p.settlementId = s.id "
            + "AND p.payee = :payee AND ((p.payee = showroomz.domain.settlement.type.SettlementPayee.BRAND AND s.market.id = :refId) OR (p.payee = showroomz.domain.settlement.type.SettlementPayee.CREATOR AND s.creator.id = :refId)) "
            + "AND p.status = showroomz.domain.settlement.type.PayoutStatus.BLOCKED "
            + "AND (p.failCode = :failCode OR (:includeUncoded = true AND p.failCode IS NULL)) ORDER BY p.id ASC")
    List<SettlementPayout> findRowsForRelease(@Param("payee") SettlementPayee payee, @Param("refId") Long refId,
                                              @Param("failCode") String failCode,
                                              @Param("includeUncoded") boolean includeUncoded);

    /**
     * 스튜디오 계좌 변경 모달(50 설계서 6-1) — 인플루언서 몫 지급 행의 상태별 [status, count, amount]. 예정 · 보류는 변경하면 확인이 끝날 때까지
     * 기다리고, 지시가 나간 행(REQUESTED)은 이전 계좌로 끝난다. 확정 전(WAITING · HELD)은 세지 않는다.
     */
    @Query("SELECT p.status, COUNT(p), COALESCE(SUM(p.amount), 0) FROM SettlementPayout p, Settlement s "
            + "WHERE p.settlementId = s.id AND s.creator.id = :creatorId "
            + "AND p.payee = showroomz.domain.settlement.type.SettlementPayee.CREATOR "
            + "AND p.status IN (showroomz.domain.settlement.type.PayoutStatus.SCHEDULED, "
            + "                 showroomz.domain.settlement.type.PayoutStatus.BLOCKED, "
            + "                 showroomz.domain.settlement.type.PayoutStatus.REQUESTED) GROUP BY p.status")
    List<Object[]> aggregateCreatorPendingPayouts(@Param("creatorId") Long creatorId);

    /** 그 사유는 풀렸지만 다른 보류 사유(세금계산서 · 주민등록번호 · 다른 정지)가 남았다 — 그 사유 코드만 지우고 BLOCKED 로 둔다(재판정이 푼다). */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE SettlementPayout p SET p.failCode = NULL, p.failReason = NULL "
            + "WHERE p.id = :payoutId AND p.status = showroomz.domain.settlement.type.PayoutStatus.BLOCKED "
            + "AND p.failCode = :failCode")
    int clearHoldCode(@Param("payoutId") Long payoutId, @Param("failCode") String failCode);

    /**
     * 지급 조건 「세금계산서 확인 후」(1017 설계서 5-1 {@code reward.payoutCondition}) — 인플루언서 지급 행이 보류(BLOCKED)인데
     * 그 정산의 세금계산서가 아직 검증 전인가. 확정 시 보류 사유는 저장되지 않으므로({@code fail_code} 없음) 문서 상태로 판정한다.
     */
    @Query("SELECT COUNT(p) > 0 FROM SettlementPayout p, Settlement s, SettlementTaxDocument d "
            + "WHERE p.settlementId = s.id AND d.settlementId = s.id AND s.creator.id = :creatorId "
            + "AND p.payee = showroomz.domain.settlement.type.SettlementPayee.CREATOR "
            + "AND p.status = showroomz.domain.settlement.type.PayoutStatus.BLOCKED "
            + "AND d.type = showroomz.domain.settlement.type.TaxDocumentType.CREATOR_TAX_INVOICE "
            + "AND d.clawbackId IS NULL "
            + "AND d.status <> showroomz.domain.settlement.type.TaxDocumentStatus.VERIFIED")
    boolean existsBlockedByUnverifiedTaxInvoice(@Param("creatorId") Long creatorId);
}
