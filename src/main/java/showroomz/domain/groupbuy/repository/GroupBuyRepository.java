package showroomz.domain.groupbuy.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GroupBuyRepository extends JpaRepository<GroupBuy, Long>, GroupBuyRepositoryCustom {

    /** 상세 — 계약·상대·브랜드를 함께 올린다. 계약 조건은 복사하지 않고 조회 시 조인한다(설계서 0-3). */
    @Query("SELECT g FROM GroupBuy g "
            + "JOIN FETCH g.contract c "
            + "JOIN FETCH g.market m "
            + "JOIN FETCH g.creator cr "
            + "WHERE g.id = :groupBuyId")
    Optional<GroupBuy> findDetailById(@Param("groupBuyId") Long groupBuyId);

    /**
     * 게이트 판정·실행 API의 행 잠금(설계서 3-2). 브랜드 확인과 운영자 승인이 동시에 들어오면 둘 다
     * 「상대가 아직」으로 읽고 아무도 전이하지 않을 수 있다 — 게이트 두 개를 잇는 판정은 조건부 UPDATE
     * 한 줄로 표현할 수 없어 행을 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GroupBuy g WHERE g.id = :groupBuyId")
    Optional<GroupBuy> findForUpdate(@Param("groupBuyId") Long groupBuyId);

    Optional<GroupBuy> findByContractId(Long contractId);

    // ── 스튜디오(31 설계) — 모든 조회가 creator_id를 조건에 넣는다(0-4) ────────────────

    /** 스튜디오 상세 — 내 공구만. 계약·브랜드·인플루언서를 함께 올린다. */
    @Query("SELECT g FROM GroupBuy g "
            + "JOIN FETCH g.contract c "
            + "JOIN FETCH g.market m "
            + "JOIN FETCH g.creator cr "
            + "WHERE g.id = :groupBuyId AND cr.id = :creatorId")
    Optional<GroupBuy> findDetailByIdAndCreatorId(@Param("groupBuyId") Long groupBuyId,
                                                  @Param("creatorId") Long creatorId);

    /** 스튜디오 실행 API의 행 잠금 — 내 공구만. 게시물 쓰기의 잠금 순서는 이 행 → {@code group_buy_post}다(31 설계 2-7). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GroupBuy g WHERE g.id = :groupBuyId AND g.creator.id = :creatorId")
    Optional<GroupBuy> findForUpdateByIdAndCreatorId(@Param("groupBuyId") Long groupBuyId,
                                                     @Param("creatorId") Long creatorId);

    @Query("SELECT g.status, COUNT(g) FROM GroupBuy g WHERE g.creator.id = :creatorId GROUP BY g.status")
    List<Object[]> countByStatusForCreator(@Param("creatorId") Long creatorId);

    /**
     * 모든 전이는 조건부 UPDATE(설계서 3-4). 0행이면 다른 요청이 먼저 상태를 바꿨다 —
     * 스케줄러의 종료와 연장 수락, 오픈과 중단 승인 같은 경합에서 하나만 통과한다.
     *
     * <p>status 컬럼만 건드리고 version은 올리지 않는다 — 전이에 딸린 나머지 필드는 같은 트랜잭션에서
     * 엔티티 변경 감지로 쓰이고, 그때 낙관적 락이 정상 동작해야 한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE GroupBuy g SET g.status = :to WHERE g.id = :groupBuyId AND g.status IN :from")
    int transition(@Param("groupBuyId") Long groupBuyId,
                   @Param("from") Collection<GroupBuyStatus> from,
                   @Param("to") GroupBuyStatus to);

    /**
     * 기간 종료 전이 — 상태에 더해 <b>종료 시각이 지났는지를 UPDATE 조건으로 다시 본다</b>(31 설계 5-1).
     * 스케줄러가 대상을 고른 뒤 인플루언서가 연장을 수락했으면 {@code end_at}이 미래로 옮겨 0행이 된다 —
     * 23:54:59 수락과 23:55:00 종료가 겹쳐도 하나만 통과한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE GroupBuy g SET g.status = :to "
            + "WHERE g.id = :groupBuyId AND g.status IN :from AND g.endAt <= :now")
    int transitionIfDue(@Param("groupBuyId") Long groupBuyId,
                        @Param("from") Collection<GroupBuyStatus> from,
                        @Param("to") GroupBuyStatus to,
                        @Param("now") LocalDateTime now);

    /**
     * 연장 수락의 종료일 변경(30 설계 3-4 · 31 설계 5-1). {@code end_at > now}가 수락 기한 = 현재 종료 시각을
     * 서버에서 보장하고, {@code end_at = beforeEndAt}이 요청 시점 이후 종료일이 바뀌지 않았음을 보장한다.
     * 0행이면 호출자가 예외로 트랜잭션 전체(요청 행의 ACCEPTED 포함)를 되돌린다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE GroupBuy g SET g.endAt = :afterEndAt "
            + "WHERE g.id = :groupBuyId AND g.creator.id = :creatorId "
            + "AND g.status = showroomz.domain.groupbuy.type.GroupBuyStatus.IN_PROGRESS "
            + "AND g.endAt = :beforeEndAt AND g.endAt > :now")
    int extendEndAt(@Param("groupBuyId") Long groupBuyId,
                    @Param("creatorId") Long creatorId,
                    @Param("beforeEndAt") LocalDateTime beforeEndAt,
                    @Param("afterEndAt") LocalDateTime afterEndAt,
                    @Param("now") LocalDateTime now);

    /** 파트너 목록(설계서 4-2). 검색은 공구명(계약) · 인플루언서 표시명 · 공구번호. */
    @Query(value = "SELECT g FROM GroupBuy g "
            + "JOIN FETCH g.contract c "
            + "JOIN FETCH g.creator cr "
            + "WHERE g.market.id = :marketId AND g.status IN :statuses "
            + "AND (:pattern IS NULL OR c.title LIKE :pattern OR cr.showroomName LIKE :pattern "
            + "     OR g.groupBuyNumber LIKE :pattern)",
            countQuery = "SELECT COUNT(g) FROM GroupBuy g "
                    + "JOIN g.contract c "
                    + "JOIN g.creator cr "
                    + "WHERE g.market.id = :marketId AND g.status IN :statuses "
                    + "AND (:pattern IS NULL OR c.title LIKE :pattern OR cr.showroomName LIKE :pattern "
                    + "     OR g.groupBuyNumber LIKE :pattern)")
    Page<GroupBuy> searchForSeller(@Param("marketId") Long marketId,
                                   @Param("statuses") Collection<GroupBuyStatus> statuses,
                                   @Param("pattern") String pattern,
                                   Pageable pageable);

    /** 탭 카운트 — 탭 묶음은 서버가 소유하므로 상태별로 받아 서비스가 묶는다(설계서 1-3). */
    @Query("SELECT g.status, COUNT(g) FROM GroupBuy g WHERE g.market.id = :marketId GROUP BY g.status")
    List<Object[]> countByStatus(@Param("marketId") Long marketId);

    /** GNB 배지 ① — 최소 물량 확인 대기(B1 「내 차례인 줄만 경고 톤」). */
    @Query("SELECT COUNT(g) FROM GroupBuy g WHERE g.market.id = :marketId "
            + "AND g.status = showroomz.domain.groupbuy.type.GroupBuyStatus.PREPARING "
            + "AND g.stockConfirmedAt IS NULL")
    long countStockConfirmationPending(@Param("marketId") Long marketId);

    /** GNB 배지 ③ — 종료됐는데 내 측(SELLER) 이행 확인이 없다(B5). */
    @Query("SELECT COUNT(g) FROM GroupBuy g WHERE g.market.id = :marketId "
            + "AND g.status = showroomz.domain.groupbuy.type.GroupBuyStatus.ENDED "
            + "AND NOT EXISTS (SELECT 1 FROM GroupBuyFulfillmentCheck f WHERE f.groupBuy = g "
            + "    AND f.checkerSide = showroomz.domain.groupbuy.type.FulfillmentSide.SELLER)")
    long countFulfillmentCheckPending(@Param("marketId") Long marketId);

    // ── 스케줄러(설계서 3-3) ─────────────────────────────────────────────────

    @Query("SELECT g.id FROM GroupBuy g "
            + "WHERE g.status = showroomz.domain.groupbuy.type.GroupBuyStatus.READY AND g.startAt <= :now "
            + "ORDER BY g.startAt ASC, g.id ASC")
    List<Long> findIdsToOpen(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT g.id FROM GroupBuy g "
            + "WHERE g.status IN (showroomz.domain.groupbuy.type.GroupBuyStatus.IN_PROGRESS, "
            + "                   showroomz.domain.groupbuy.type.GroupBuyStatus.SUSPENSION_SCHEDULED) "
            + "AND g.endAt <= :now "
            + "ORDER BY g.endAt ASC, g.id ASC")
    List<Long> findIdsToEnd(@Param("now") LocalDateTime now, Pageable pageable);

    /** 자동 이행 대상 — 기한이 지났는데 어느 한 측이라도 확인이 없다. */
    @Query("SELECT g.id FROM GroupBuy g "
            + "WHERE g.status = showroomz.domain.groupbuy.type.GroupBuyStatus.ENDED "
            + "AND g.fulfillmentDueAt IS NOT NULL AND g.fulfillmentDueAt <= :now "
            + "AND (SELECT COUNT(f) FROM GroupBuyFulfillmentCheck f WHERE f.groupBuy = g) < 2 "
            + "ORDER BY g.fulfillmentDueAt ASC, g.id ASC")
    List<Long> findIdsToAutoConfirmFulfillment(@Param("now") LocalDateTime now, Pageable pageable);

    // ── 상품 groupBuyStatus 동기화(설계서 1-11) ─────────────────────────────────

    /** 주어진 상품을 담은 <b>활성</b> 공구의 (상품 id, 공구 상태) 쌍. 종결 3종은 상품을 붙들지 않는다. */
    @Query("SELECT ci.product.productId, g.status FROM GroupBuy g "
            + "JOIN g.contract c JOIN c.items ci "
            + "WHERE ci.product.productId IN :productIds AND g.status IN :activeStatuses")
    List<Object[]> findActiveStatusesByProductIds(@Param("productIds") Collection<Long> productIds,
                                                 @Param("activeStatuses") Collection<GroupBuyStatus> activeStatuses);
}
