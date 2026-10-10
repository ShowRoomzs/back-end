package showroomz.domain.settlement.adjustment.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustment;
import showroomz.domain.settlement.adjustment.type.AdjustmentStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 정산 조정 협의. 종결 전이는 전부 <b>조건부 UPDATE 한 문장</b>이 승자다(44 이슈 스레드 설계서 0-8) — 마감 직전의 동의와 만료 배치가
 * 겹쳐도 하나만 통과한다. UPDATE 뒤에는 영속성 컨텍스트를 비운다(다시 읽는 행이 낡지 않게).
 */
public interface SettlementAdjustmentRepository extends JpaRepository<SettlementAdjustment, Long> {

    Optional<SettlementAdjustment> findBySettlementId(Long settlementId);

    List<SettlementAdjustment> findBySettlementIdIn(Collection<Long> settlementIds);

    Optional<SettlementAdjustment> findByThreadId(Long threadId);

    List<SettlementAdjustment> findByThreadIdIn(Collection<Long> threadIds);

    /** 커맨드 진입 잠금 — 「제안 두 개가 동시에 seq 2를 받는」 경합을 선검사 단계에서 정리한다(2절). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM SettlementAdjustment a WHERE a.id = :adjustmentId")
    Optional<SettlementAdjustment> findForUpdate(@Param("adjustmentId") Long adjustmentId);

    /** 합의 종결(2-3 ⑤) — 0행이면 만료 배치가 먼저였다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementAdjustment a SET a.status = showroomz.domain.settlement.adjustment.type.AdjustmentStatus.AGREED, "
            + "a.agreedRewardAmount = :amount, a.finalRewardAmount = :amount, a.closedAt = :now "
            + "WHERE a.id = :adjustmentId AND a.status = showroomz.domain.settlement.adjustment.type.AdjustmentStatus.OPEN")
    int closeAsAgreed(@Param("adjustmentId") Long adjustmentId, @Param("amount") long amount,
                      @Param("now") LocalDateTime now);

    /** 기한 만료 종결(6-2 ②) — 간주 규칙이 없다: 원래 금액으로 확정. 0행이면 동의가 먼저였다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementAdjustment a SET a.status = showroomz.domain.settlement.adjustment.type.AdjustmentStatus.EXPIRED, "
            + "a.finalRewardAmount = a.originalRewardAmount, a.closedAt = :now "
            + "WHERE a.id = :adjustmentId AND a.status = showroomz.domain.settlement.adjustment.type.AdjustmentStatus.OPEN "
            + "AND a.deadlineAt < :now")
    int closeAsExpired(@Param("adjustmentId") Long adjustmentId, @Param("now") LocalDateTime now);

    /** D-1 통지 표지(6-1 ①) — 멱등. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementAdjustment a SET a.noticeSentAt = :now WHERE a.id = :adjustmentId "
            + "AND a.noticeSentAt IS NULL "
            + "AND a.status = showroomz.domain.settlement.adjustment.type.AdjustmentStatus.OPEN")
    int markNoticeSent(@Param("adjustmentId") Long adjustmentId, @Param("now") LocalDateTime now);

    // ------------------------------------------------------------------ 배치(6절)

    @Query("SELECT a.id FROM SettlementAdjustment a "
            + "WHERE a.status = showroomz.domain.settlement.adjustment.type.AdjustmentStatus.OPEN "
            + "AND a.noticeSentAt IS NULL AND a.noticeDueAt <= :now ORDER BY a.noticeDueAt ASC, a.id ASC")
    List<Long> findIdsToNotify(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT a.id FROM SettlementAdjustment a "
            + "WHERE a.status = showroomz.domain.settlement.adjustment.type.AdjustmentStatus.OPEN "
            + "AND a.deadlineAt < :now ORDER BY a.deadlineAt ASC, a.id ASC")
    List<Long> findIdsToExpire(@Param("now") LocalDateTime now, Pageable pageable);

    // ------------------------------------------------------------------ 어드민 20b(4절)

    /** 그 쌍에 협의가 있었는가 — 어드민 PAIR 스레드 열람 허용 조건(4-5 · 상태 무관). */
    boolean existsByMarketIdAndCreatorId(Long marketId, Long creatorId);

    /**
     * 이슈 탭 목록(4-1) — 공구명 · 브랜드명 · 쇼룸명 부분 일치 · 마지막 메시지 최신순(메시지 없는 스레드는 맨 아래).
     */
    @Query(value = "SELECT a FROM SettlementAdjustment a, MessageThread t, GroupBuy g JOIN g.contract c, "
            + "Market m, Creator cr "
            + "WHERE t.id = a.threadId AND g.id = a.groupBuyId AND m.id = a.marketId AND cr.id = a.creatorId "
            + "AND a.status IN :statuses "
            + "AND (:pattern IS NULL OR c.title LIKE :pattern OR m.marketName LIKE :pattern "
            + "     OR cr.showroomName LIKE :pattern) "
            + "ORDER BY CASE WHEN t.lastMessageAt IS NULL THEN 1 ELSE 0 END ASC, t.lastMessageAt DESC, a.id DESC",
            countQuery = "SELECT COUNT(a) FROM SettlementAdjustment a, GroupBuy g JOIN g.contract c, "
                    + "Market m, Creator cr "
                    + "WHERE g.id = a.groupBuyId AND m.id = a.marketId AND cr.id = a.creatorId "
                    + "AND a.status IN :statuses "
                    + "AND (:pattern IS NULL OR c.title LIKE :pattern OR m.marketName LIKE :pattern "
                    + "     OR cr.showroomName LIKE :pattern)")
    Page<SettlementAdjustment> searchForAdmin(@Param("statuses") Collection<AdjustmentStatus> statuses,
                                              @Param("pattern") String pattern, Pageable pageable);

    @Query("SELECT a.status, COUNT(a) FROM SettlementAdjustment a GROUP BY a.status")
    List<Object[]> countByStatus();

    /** 어드민 07a 조정 협의 탭 툴바 — 가장 이른 합의 기한(7-2). */
    @Query("SELECT MIN(a.deadlineAt) FROM SettlementAdjustment a "
            + "WHERE a.status = showroomz.domain.settlement.adjustment.type.AdjustmentStatus.OPEN")
    LocalDateTime findEarliestOpenDeadline();
}
