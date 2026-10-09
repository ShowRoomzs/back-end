package showroomz.domain.order.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.type.ClaimRejectReason;
import showroomz.domain.order.type.ClaimResult;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryCarrier;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 클레임 상태 전이(35 설계서 2절) — <b>전부 조건부 UPDATE</b>다. 현재 상태를 WHERE 에 넣은 UPDATE 의 1행 확인이 경합의
 * 최종 방어선이고, JPA 더티 체킹으로 전이하지 않는다. 역방향 전이 메서드가 하나도 없다. 모든 전이가
 * {@code stage_entered_at}을 같이 쓴다.
 *
 * <p>「진행 중」(수량 점유 · 화면의 진행 표시)과 「보류」(구매확정을 세우는 것)는 다른 조건이다 —
 * {@link #existsOpenByDeliveryGroupId} / {@link #existsConfirmBlockingByDeliveryGroupId} 둘로 고정해 호출부가
 * 섞어 쓰지 않게 한다(3-6).
 */
public interface OrderClaimRepository extends JpaRepository<OrderClaim, Long>, OrderClaimRepositoryCustom {

    String COMPLETED = "showroomz.domain.order.type.ClaimStatus.COMPLETED";
    String CANCELLED = "showroomz.domain.order.type.ClaimResult.CANCELLED";
    String REFUND_PENDING = "showroomz.domain.order.type.ClaimStatus.REFUND_PENDING";

    // ------------------------------------------------------------------ 조회

    @Query("SELECT c FROM OrderClaim c WHERE c.collection.id = :collectionId ORDER BY c.id ASC")
    List<OrderClaim> findByCollectionId(@Param("collectionId") Long collectionId);

    /** 소비자 앱 주문 내역(C10 설계서 2-4 #6) — 그 주문들의 클레임 전부를 요청과 함께 IN 1번. 상태 조건 없이 읽는다. */
    @Query("SELECT c FROM OrderClaim c JOIN FETCH c.collection WHERE c.orderId IN :orderIds ORDER BY c.id ASC")
    List<OrderClaim> findByOrderIdsWithCollection(@Param("orderIds") Collection<Long> orderIds);

    @Query("SELECT c FROM OrderClaim c WHERE c.collection.id IN :collectionIds ORDER BY c.id ASC")
    List<OrderClaim> findByCollectionIds(@Param("collectionIds") Collection<Long> collectionIds);

    /** 파트너센터 상세 — 내 마켓 것만. 남의 마켓 클레임은 없는 것이다(존재 비노출). */
    @Query("SELECT c FROM OrderClaim c JOIN FETCH c.collection JOIN FETCH c.deliveryGroup g JOIN FETCH g.order "
            + "JOIN FETCH c.orderProduct WHERE c.id = :id AND c.marketId = :marketId "
            + "AND c.status <> showroomz.domain.order.type.ClaimStatus.PAYMENT_PENDING")
    java.util.Optional<OrderClaim> findOwned(@Param("id") Long id, @Param("marketId") Long marketId);

    /** 요약 — [상태, 유형, 건수]. 탭 카운트 · KPI · 유형 카운트를 한 번에 푼다. */
    @Query("SELECT c.status, c.type, COUNT(c) FROM OrderClaim c WHERE (:marketId IS NULL OR c.marketId = :marketId) "
            + "GROUP BY c.status, c.type")
    List<Object[]> countByStatusAndType(@Param("marketId") Long marketId);

    /** 기한 초과 — 회수 대기 방치(기한 ①) + 검수 기한 경과(기한 ②). */
    @Query("SELECT COUNT(c) FROM OrderClaim c WHERE (:marketId IS NULL OR c.marketId = :marketId) AND ("
            + "(c.status = showroomz.domain.order.type.ClaimStatus.REQUESTED AND c.collectDueAt < :now) OR "
            + "(c.status = showroomz.domain.order.type.ClaimStatus.RECEIVED AND c.inspectDueAt < :now))")
    long countOverdue(@Param("marketId") Long marketId, @Param("now") LocalDateTime now);

    /**
     * 주문 관리 화면의 오버레이(35 설계서 5-2) — [하위주문 id, 진행 중 건수, 그중 보류 건수]. 결제 대기는 세지 않는다.
     * 진행 중은 거절 보류를 포함하고, 구매확정 D-N 이 비는 것은 보류 건수가 있을 때뿐이다.
     */
    @Query("SELECT c.deliveryGroup.id, COUNT(c), SUM(CASE WHEN c.rejectedAt IS NULL THEN 1 ELSE 0 END) "
            + "FROM OrderClaim c WHERE c.deliveryGroup.id IN :deliveryGroupIds "
            + "AND c.status NOT IN (" + COMPLETED + ", showroomz.domain.order.type.ClaimStatus.PAYMENT_PENDING) "
            + "GROUP BY c.deliveryGroup.id")
    List<Object[]> countOpenByDeliveryGroupIds(@Param("deliveryGroupIds") Collection<Long> deliveryGroupIds);

    /** 어드민 주문 상세 — 하위주문들의 진행 중 클레임(종결 · 결제 대기 제외). 06b 상세 링크용(37 설계서 8절 #5). */
    @Query("SELECT c FROM OrderClaim c JOIN FETCH c.deliveryGroup g WHERE g.id IN :deliveryGroupIds "
            + "AND c.status NOT IN (" + COMPLETED + ", showroomz.domain.order.type.ClaimStatus.PAYMENT_PENDING) "
            + "ORDER BY c.id ASC")
    List<OrderClaim> findOpenByDeliveryGroupIds(@Param("deliveryGroupIds") Collection<Long> deliveryGroupIds);

    /** 진행 중 — 종결 전 전부(거절 보류·거절 반송 포함). */
    @Query("SELECT COUNT(c) > 0 FROM OrderClaim c WHERE c.deliveryGroup.id = :deliveryGroupId "
            + "AND c.status <> " + COMPLETED)
    boolean existsOpenByDeliveryGroupId(@Param("deliveryGroupId") Long deliveryGroupId);

    /** 보류 — 진행 중이고 거절되지 않은 것. 이것이 있는 하위주문은 구매확정하지 않는다. */
    @Query("SELECT COUNT(c) > 0 FROM OrderClaim c WHERE c.deliveryGroup.id = :deliveryGroupId "
            + "AND c.status <> " + COMPLETED + " AND c.rejectedAt IS NULL")
    boolean existsConfirmBlockingByDeliveryGroupId(@Param("deliveryGroupId") Long deliveryGroupId);

    /**
     * 그 항목에서 다시 신청할 수 없는 수량 — 진행 중 클레임 + 거절된 클레임(종결 뒤에도 돌아오지 않는다 · 앱 클레임
     * 설계서 1-3). 철회·자동 취소된 수량과 교환 완료된 수량은 돌아온다. 환불된 수량은 {@code returned_quantity}가 뺀다 —
     * <b>환불 대기(검수 통과)는 세지 않는다.</b> 통과 순간 {@code returned_quantity}에 이미 올라가 있어 여기서도 세면 두 번 빠진다.
     */
    @Query("SELECT COALESCE(SUM(c.quantity), 0) FROM OrderClaim c WHERE c.orderProduct.id = :orderProductId "
            + "AND (c.status NOT IN (" + COMPLETED + ", " + REFUND_PENDING + ") OR c.rejectedAt IS NOT NULL)")
    long sumOccupiedQuantity(@Param("orderProductId") Long orderProductId);

    /**
     * {@link #sumOccupiedQuantity}의 하위주문 판 — [주문 항목 id, 점유 수량]. 신청 양식이 항목마다 묻지 않게 한다.
     * <b>결제 대기 초안은 세지 않는다</b> — 아직 접수 전이고, 다시 요청하면 신청이 그 초안을 먼저 지운다. 세면 결제에
     * 실패한 소비자가 같은 항목으로 폼을 다시 열 수 없다.
     */
    @Query("SELECT c.orderProduct.id, COALESCE(SUM(c.quantity), 0) FROM OrderClaim c "
            + "WHERE c.deliveryGroup.id = :deliveryGroupId "
            + "AND (c.status NOT IN (" + COMPLETED + ", " + REFUND_PENDING + ", "
            + "showroomz.domain.order.type.ClaimStatus.PAYMENT_PENDING) OR c.rejectedAt IS NOT NULL) "
            + "GROUP BY c.orderProduct.id")
    List<Object[]> sumOccupiedQuantityByDeliveryGroup(@Param("deliveryGroupId") Long deliveryGroupId);

    /** 소비자 앱 — 내 클레임만. 남의 클레임은 없는 것이다(존재 비노출). */
    @Query("SELECT c FROM OrderClaim c JOIN FETCH c.collection JOIN FETCH c.deliveryGroup g JOIN FETCH g.order "
            + "WHERE c.id = :id AND c.userId = :userId")
    java.util.Optional<OrderClaim> findOwnedByUser(@Param("id") Long id, @Param("userId") Long userId);

    /** 결제 대기 초안의 삭제(전이 0b) — 아직 접수된 적 없는 요청이라 행을 지운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM OrderClaim c WHERE c.collection.id = :collectionId "
            + "AND c.status = showroomz.domain.order.type.ClaimStatus.PAYMENT_PENDING")
    int deletePaymentPendingByCollection(@Param("collectionId") Long collectionId);

    // ------------------------------------------------------------------ 묶음(박스) 단위 전이

    /**
     * 묶음 전체를 한 상태에서 다음 상태로 — #2(REQUESTED → COLLECTING · 회수 송장 입력) · 0a(PAYMENT_PENDING →
     * REQUESTED/COLLECTING · 결제 확정) · 8′(REJECT_HOLD → RESHIP_READY · 재발송비 차감·충당). 박스가 하나라 묶음 단위다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.status = :to, c.stageEnteredAt = :now "
            + "WHERE c.collection.id = :collectionId AND c.status = :from")
    int moveByCollection(@Param("collectionId") Long collectionId, @Param("from") ClaimStatus from,
                         @Param("to") ClaimStatus to, @Param("now") LocalDateTime now);

    /** #3 COLLECTING → ARRIVED — 추적상 브랜드 도착. 폴링 당시 송장이 그대로일 때만(그사이 정정됐으면 0행). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.status = showroomz.domain.order.type.ClaimStatus.ARRIVED, c.stageEnteredAt = :now "
            + "WHERE c.collection.id = :collectionId AND c.status = showroomz.domain.order.type.ClaimStatus.COLLECTING "
            + "AND EXISTS (SELECT k FROM OrderClaimCollection k WHERE k.id = :collectionId "
            + "    AND k.carrier = :carrier AND k.trackingNumber = :trackingNumber)")
    int markArrived(@Param("collectionId") Long collectionId, @Param("carrier") DeliveryCarrier carrier,
                    @Param("trackingNumber") String trackingNumber, @Param("now") LocalDateTime now);

    /** #14 REQUESTED → COMPLETED(CANCELLED · INVOICE_EXPIRED) — 회수 송장 등록 기한 경과. 묶음 전체. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.status = " + COMPLETED + ", c.result = " + CANCELLED + ", "
            + "c.cancelReason = showroomz.domain.order.type.ClaimCancelReason.INVOICE_EXPIRED, "
            + "c.completedAt = :now, c.stageEnteredAt = :now "
            + "WHERE c.collection.id = :collectionId AND c.status = showroomz.domain.order.type.ClaimStatus.REQUESTED")
    int expireByCollection(@Param("collectionId") Long collectionId, @Param("now") LocalDateTime now);

    /** #10 REFUND_PENDING → COMPLETED(REFUNDED) — 환불 큐가 요청 단위라 그 요청의 환불 대기 건을 한꺼번에 닫는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.status = " + COMPLETED + ", "
            + "c.result = showroomz.domain.order.type.ClaimResult.REFUNDED, c.completedAt = :now, c.stageEnteredAt = :now "
            + "WHERE c.collection.id = :collectionId "
            + "AND c.status = showroomz.domain.order.type.ClaimStatus.REFUND_PENDING")
    int completeRefundByCollection(@Param("collectionId") Long collectionId, @Param("now") LocalDateTime now);

    /** 환불 집행액 기록 — 요청 단위로 집행된 금액을 항목에 나눠 적는다(완료 탭 「금액」 · 앱 항목 행). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.refundedAmount = :amount WHERE c.id = :id")
    int setRefundedAmount(@Param("id") Long id, @Param("amount") int amount);

    // ------------------------------------------------------------------ 항목 단위 전이

    /** #13 REQUESTED → COMPLETED(CANCELLED · WITHDRAWN) — 소비자 철회. 회수 송장을 넣기 전까지만. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.status = " + COMPLETED + ", c.result = " + CANCELLED + ", "
            + "c.cancelReason = showroomz.domain.order.type.ClaimCancelReason.WITHDRAWN, "
            + "c.completedAt = :now, c.stageEnteredAt = :now "
            + "WHERE c.id = :id AND c.userId = :userId AND c.status = showroomz.domain.order.type.ClaimStatus.REQUESTED")
    int withdraw(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * 반려 이의 인용(어드민 06b B2) — 아직 반송 전(반려 보류 · 재발송 대기)인 반려 반품을 환불로 닫는다. 재발송은 없다. 귀책은
     * 브랜드로 돌린다({@code faultChangedToSeller} — 「귀책」 열 · 정산 집계가 본다). 환불액은 운영자 사유 환불 집행으로 나가고
     * 여기서는 예정액을 적는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.status = " + COMPLETED + ", "
            + "c.result = showroomz.domain.order.type.ClaimResult.REFUNDED, c.refundedAmount = :amount, "
            + "c.faultChangedToSeller = true, c.completedAt = :now, c.stageEnteredAt = :now "
            + "WHERE c.id = :id AND c.type = showroomz.domain.order.type.ClaimType.RETURN AND c.rejectedAt IS NOT NULL "
            + "AND c.status IN (showroomz.domain.order.type.ClaimStatus.REJECT_HOLD, "
            + "showroomz.domain.order.type.ClaimStatus.RESHIP_READY)")
    int closeRejectedByDispute(@Param("id") Long id, @Param("amount") int amount, @Param("now") LocalDateTime now);

    /** 어드민 예외 관리 — 검수 기한이 지난 입고 건(06d 처리 지연). */
    @Query("SELECT c FROM OrderClaim c JOIN FETCH c.deliveryGroup g JOIN FETCH g.order WHERE "
            + "c.status = showroomz.domain.order.type.ClaimStatus.RECEIVED AND c.inspectDueAt < :now "
            + "ORDER BY c.inspectDueAt ASC")
    List<OrderClaim> findInspectOverdue(@Param("now") LocalDateTime now, Pageable limit);

    /**
     * 어드민 예외 관리 — 재발송 대기 중인 건(06d 재발송 지연). N영업일은 달력일로 최소 N일이므로 {@code before}(지금 − N일)로 먼저
     * 거르고, 영업일 판정은 서비스가 한다(40 설계서 5절 #3).
     */
    @Query("SELECT c FROM OrderClaim c JOIN FETCH c.deliveryGroup g JOIN FETCH g.order WHERE "
            + "c.status = showroomz.domain.order.type.ClaimStatus.RESHIP_READY AND c.stageEnteredAt < :before "
            + "ORDER BY c.stageEnteredAt ASC")
    List<OrderClaim> findReshipReadyBefore(@Param("before") LocalDateTime before, Pageable limit);

    /** 어드민 예외 관리 — 회수 송장을 넣었는데 24시간 동안 한 번도 조회되지 않은 건(06d 배송 예외). */
    @Query("SELECT c FROM OrderClaim c JOIN FETCH c.collection k JOIN FETCH c.deliveryGroup g JOIN FETCH g.order WHERE "
            + "c.status = showroomz.domain.order.type.ClaimStatus.COLLECTING AND k.invoiceRegisteredAt < :before "
            + "AND k.lastTrackingAt IS NULL ORDER BY k.invoiceRegisteredAt ASC")
    List<OrderClaim> findCollectionUnscanned(@Param("before") LocalDateTime before, Pageable limit);

    /** 운영자 직권 종결 — 미발송 방치 등. 검수 전(REQUESTED · COLLECTING)만. 결과는 거절이 아니라 요청 취소다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.status = " + COMPLETED + ", c.result = " + CANCELLED + ", "
            + "c.cancelReason = showroomz.domain.order.type.ClaimCancelReason.ADMIN, "
            + "c.completedAt = :now, c.stageEnteredAt = :now "
            + "WHERE c.id = :id AND c.status IN (showroomz.domain.order.type.ClaimStatus.REQUESTED, "
            + "showroomz.domain.order.type.ClaimStatus.COLLECTING)")
    int closeByAdmin(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** #4 ARRIVED → RECEIVED — 입고 확인. 추적 스텁 기간에는 {@code from}에 COLLECTING 을 함께 넘긴다(0-7). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.status = showroomz.domain.order.type.ClaimStatus.RECEIVED, c.stageEnteredAt = :now, "
            + "c.receivedAt = :now, c.receivedBy = :sellerId, c.inspectDueAt = :inspectDueAt "
            + "WHERE c.id = :id AND c.marketId = :marketId AND c.status IN :from")
    int markReceived(@Param("id") Long id, @Param("marketId") Long marketId,
                     @Param("from") Collection<ClaimStatus> from, @Param("sellerId") Long sellerId,
                     @Param("inspectDueAt") LocalDateTime inspectDueAt, @Param("now") LocalDateTime now);

    /** #5 · #6 RECEIVED → REFUND_PENDING(반품) / RESHIP_READY(교환) — 검수 통과. 유형이 WHERE 에 있다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.status = :to, c.stageEnteredAt = :now, c.inspectedAt = :now, "
            + "c.inspectedBy = :sellerId "
            + "WHERE c.id = :id AND c.marketId = :marketId AND c.type = :type "
            + "AND c.status = showroomz.domain.order.type.ClaimStatus.RECEIVED")
    int passInspection(@Param("id") Long id, @Param("marketId") Long marketId, @Param("type") ClaimType type,
                       @Param("to") ClaimStatus to, @Param("sellerId") Long sellerId,
                       @Param("now") LocalDateTime now);

    /** #7 RECEIVED → REJECT_HOLD — 검수 거절. 제출 = 즉시 확정이고 되돌리지 않는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.status = showroomz.domain.order.type.ClaimStatus.REJECT_HOLD, "
            + "c.stageEnteredAt = :now, c.inspectedAt = :now, c.inspectedBy = :sellerId, "
            + "c.rejectReasonCode = :reasonCode, c.rejectDetail = :detail, c.rejectedAt = :now, "
            + "c.rejectLegalBasis = :legalBasis, c.rejectConsumerMessage = :consumerMessage, "
            + "c.faultChangedToSeller = :faultToSeller "
            + "WHERE c.id = :id AND c.marketId = :marketId "
            + "AND c.status = showroomz.domain.order.type.ClaimStatus.RECEIVED")
    int rejectInspection(@Param("id") Long id, @Param("marketId") Long marketId,
                         @Param("reasonCode") ClaimRejectReason reasonCode, @Param("detail") String detail,
                         @Param("legalBasis") showroomz.domain.order.type.ClaimRejectLegalBasis legalBasis,
                         @Param("consumerMessage") String consumerMessage,
                         @Param("faultToSeller") boolean faultToSeller,
                         @Param("sellerId") Long sellerId, @Param("now") LocalDateTime now);

    /** 검수 기한 경과 자동 알림 대상 — 입고 확인 뒤 검수 기한이 지났고 오늘 아직 알리지 않음(1009 기획 수정본 8-4). */
    @Query("SELECT c.id FROM OrderClaim c WHERE c.status = showroomz.domain.order.type.ClaimStatus.RECEIVED "
            + "AND c.inspectDueAt IS NOT NULL AND c.inspectDueAt < :now "
            + "AND (c.lastInspectNoticeAt IS NULL OR c.lastInspectNoticeAt < :todayStart) ORDER BY c.id ASC")
    List<Long> findInspectOverdueToNotify(@Param("now") LocalDateTime now,
                                          @Param("todayStart") LocalDateTime todayStart,
                                          org.springframework.data.domain.Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.inspectNoticeCount = c.inspectNoticeCount + 1, c.lastInspectNoticeAt = :now "
            + "WHERE c.id = :id AND (c.lastInspectNoticeAt IS NULL OR c.lastInspectNoticeAt < :todayStart)")
    int recordInspectNotice(@Param("id") Long id, @Param("now") LocalDateTime now,
                            @Param("todayStart") LocalDateTime todayStart);

    /** 일부 반려 — 원래 행을 통과 수량으로 줄인다(검수 직전 상태에서만). 반려 수량은 갈라진 새 행이 든다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.quantity = :quantity WHERE c.id = :id AND c.marketId = :marketId "
            + "AND c.status = showroomz.domain.order.type.ClaimStatus.RECEIVED AND c.quantity > :quantity")
    int shrinkForPartialReject(@Param("id") Long id, @Param("marketId") Long marketId,
                               @Param("quantity") int quantity);

    /** 귀책 변경(브랜드 귀책 인정) — 같은 요청의 클레임 사본 부담 주체를 맞춘다(목록 필터 · 어드민 「귀책」 열). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.feeBearer = showroomz.domain.order.type.ClaimFeeBearer.SELLER "
            + "WHERE c.collection.id = :collectionId")
    int acceptSellerFault(@Param("collectionId") Long collectionId);

    /** #9 RESHIP_READY → RESHIPPING — 재발송 송장 등록. 등록해도 완료가 아니다 — 결과는 도착 때 확정된다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.status = showroomz.domain.order.type.ClaimStatus.RESHIPPING, "
            + "c.stageEnteredAt = :now, c.reshipCarrier = :carrier, c.reshipTrackingNumber = :trackingNumber, "
            + "c.reshipRegisteredAt = :now, c.reshipRegisteredBy = :sellerId "
            + "WHERE c.id = :id AND c.marketId = :marketId "
            + "AND c.status = showroomz.domain.order.type.ClaimStatus.RESHIP_READY")
    int registerReshipment(@Param("id") Long id, @Param("marketId") Long marketId,
                           @Param("carrier") DeliveryCarrier carrier, @Param("trackingNumber") String trackingNumber,
                           @Param("sellerId") Long sellerId, @Param("now") LocalDateTime now);

    /**
     * #11 RESHIPPING → COMPLETED — 재발송 도착(추적). 결과는 호출자가 {@code rejected_at}으로 가른다(EXCHANGED / REJECTED).
     * 폴링 당시 송장이 그대로일 때만.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.status = " + COMPLETED + ", c.result = :result, c.completedAt = :now, "
            + "c.stageEnteredAt = :now, c.reshipDeliveredAt = :deliveredAt "
            + "WHERE c.id = :id AND c.status = showroomz.domain.order.type.ClaimStatus.RESHIPPING "
            + "AND c.reshipCarrier = :carrier AND c.reshipTrackingNumber = :trackingNumber")
    int completeReshipByTracker(@Param("id") Long id, @Param("carrier") DeliveryCarrier carrier,
                                @Param("trackingNumber") String trackingNumber, @Param("result") ClaimResult result,
                                @Param("deliveredAt") LocalDateTime deliveredAt, @Param("now") LocalDateTime now);

    /** 재발송 송장 수정 — 재발송 중만. 등록 시각은 유지하고 추적 값만 리셋한다(새 송장의 이력이 0건부터 다시 쌓인다). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.reshipCarrier = :carrier, c.reshipTrackingNumber = :trackingNumber, "
            + "c.reshipLastTrackingAt = NULL "
            + "WHERE c.id = :id AND c.marketId = :marketId "
            + "AND c.status = showroomz.domain.order.type.ClaimStatus.RESHIPPING")
    int updateReshipment(@Param("id") Long id, @Param("marketId") Long marketId,
                         @Param("carrier") DeliveryCarrier carrier, @Param("trackingNumber") String trackingNumber);

    /** 재발송 추적 갱신 — 폴링 당시 송장이 그대로일 때만. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.reshipLastTrackingAt = :at "
            + "WHERE c.id = :id AND c.status = showroomz.domain.order.type.ClaimStatus.RESHIPPING "
            + "AND c.reshipCarrier = :carrier AND c.reshipTrackingNumber = :trackingNumber")
    int touchReshipTracking(@Param("id") Long id, @Param("carrier") DeliveryCarrier carrier,
                            @Param("trackingNumber") String trackingNumber, @Param("at") LocalDateTime at);

    /** 전역 송장 중복 검사 — 재발송 중인 클레임의 재발송 송장. 종결 건은 겹쳐도 된다(택배사가 번호를 재사용한다). */
    @Query("SELECT c FROM OrderClaim c WHERE c.reshipCarrier = :carrier "
            + "AND c.reshipTrackingNumber = :trackingNumber "
            + "AND c.status = showroomz.domain.order.type.ClaimStatus.RESHIPPING")
    List<OrderClaim> findReshippingByInvoice(@Param("carrier") DeliveryCarrier carrier,
                                             @Param("trackingNumber") String trackingNumber);

    /** 재발송 추적 대상 — id 커서로 이어 읽는다. */
    @Query("SELECT c FROM OrderClaim c WHERE c.status = showroomz.domain.order.type.ClaimStatus.RESHIPPING "
            + "AND c.reshipCarrier IS NOT NULL AND c.reshipTrackingNumber IS NOT NULL AND c.id > :afterId "
            + "ORDER BY c.id ASC")
    List<OrderClaim> findReshipTrackingTargets(@Param("afterId") Long afterId,
                                               org.springframework.data.domain.Pageable pageable);

    /** 재발송 목록 · 업로드 매칭 — 내 마켓의 클레임을 요청 · 하위주문 · 주문 · 주문 항목과 함께. */
    @Query("SELECT c FROM OrderClaim c JOIN FETCH c.collection JOIN FETCH c.deliveryGroup g JOIN FETCH g.order "
            + "JOIN FETCH c.orderProduct WHERE c.marketId = :marketId AND c.id IN :ids ORDER BY c.id ASC")
    List<OrderClaim> findOwnedByIds(@Param("marketId") Long marketId, @Param("ids") Collection<Long> ids);

    /** 재발송 목록의 「선택 없이 열면 전체」 — 내 마켓의 재발송 대기 전부. */
    @Query("SELECT c FROM OrderClaim c JOIN FETCH c.collection JOIN FETCH c.deliveryGroup g JOIN FETCH g.order "
            + "JOIN FETCH c.orderProduct WHERE c.marketId = :marketId "
            + "AND c.status = showroomz.domain.order.type.ClaimStatus.RESHIP_READY ORDER BY c.stageEnteredAt ASC, c.id ASC")
    List<OrderClaim> findReshipReady(@Param("marketId") Long marketId,
                                     org.springframework.data.domain.Pageable pageable);

    /** #11 운영자 직권 — 추적이 놓친 건의 출구(0-7). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.status = " + COMPLETED + ", c.result = :result, c.completedAt = :now, "
            + "c.stageEnteredAt = :now, c.reshipDeliveredAt = :now "
            + "WHERE c.id = :id AND c.status = showroomz.domain.order.type.ClaimStatus.RESHIPPING")
    int completeReshipByAdmin(@Param("id") Long id, @Param("result") ClaimResult result,
                              @Param("now") LocalDateTime now);

    /**
     * #12 REJECT_HOLD → COMPLETED(REJECTED) — 보관 기간 만료 후 폐기 기록. {@code noticeCount}는 가드를 계산할 때 읽은
     * 값이다 — 사이에 고지가 더해져 기한이 밀렸으면 0행이 된다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.status = " + COMPLETED + ", "
            + "c.result = showroomz.domain.order.type.ClaimResult.REJECTED, c.completedAt = :now, "
            + "c.stageEnteredAt = :now, c.disposedAt = :now "
            + "WHERE c.id = :id AND c.status = showroomz.domain.order.type.ClaimStatus.REJECT_HOLD "
            + "AND c.noticeCount = :noticeCount")
    int dispose(@Param("id") Long id, @Param("noticeCount") int noticeCount, @Param("now") LocalDateTime now);

    /** 미결제 고지 집계 캐시 — 거절 보류인 동안만, 읽은 횟수에서 정확히 1 올린다(같은 회차 중복 방지). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderClaim c SET c.noticeCount = c.noticeCount + 1, c.lastNoticeAt = :now "
            + "WHERE c.id = :id AND c.status = showroomz.domain.order.type.ClaimStatus.REJECT_HOLD "
            + "AND c.noticeCount = :expectedCount")
    int recordNotice(@Param("id") Long id, @Param("expectedCount") int expectedCount,
                     @Param("now") LocalDateTime now);

    /**
     * 반려 이의 미처리 건수(어드민 06b 요약 「반려 이의 N건」) — 반려 보류 중이고 걸린 이의 문의가 아직 답변 전인 클레임.
     * 운영자가 인용하면 반려 보류를 벗어나고, 기각하면 문의 답변이 등록돼 빠진다.
     */
    @Query("SELECT COUNT(c) FROM OrderClaim c, showroomz.domain.inquiry.entity.OneToOneInquiry i "
            + "WHERE i.id = c.disputeInquiryId AND c.status = showroomz.domain.order.type.ClaimStatus.REJECT_HOLD "
            + "AND i.status = showroomz.domain.inquiry.type.InquiryStatus.WAITING "
            + "AND (:marketId IS NULL OR c.marketId = :marketId)")
    long countOpenDisputes(@Param("marketId") Long marketId);
}
