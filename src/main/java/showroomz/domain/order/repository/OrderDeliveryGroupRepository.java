package showroomz.domain.order.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.TrackingAlert;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 하위주문(배송 그룹) 전이는 전부 <b>조건부 UPDATE</b>다(34 설계서 2절) — 현재 상태를 WHERE 에 넣은 UPDATE 1행
 * 확인이 경합의 최종 방어선이고, JPA 더티 체킹으로 전이하지 않는다. 역방향 전이 메서드가 하나도 없다.
 *
 * <p>준비 시작·송장 등록의 WHERE 에는 「취소 요청 PENDING 없음」과 「살아 있는 결제 취소 선점 없음」이 들어간다 —
 * 엑셀 업로드·다건 선택과 취소 요청·소비자 취소의 레이스를 검사-후-UPDATE 가 아니라 UPDATE WHERE 로 막는다.
 */
public interface OrderDeliveryGroupRepository extends JpaRepository<OrderDeliveryGroup, Long>,
        OrderDeliveryGroupRepositoryCustom {

    @Query("SELECT g FROM OrderDeliveryGroup g LEFT JOIN FETCH g.groupBuy WHERE g.order.id = :orderId ORDER BY g.id ASC")
    List<OrderDeliveryGroup> findByOrderId(@Param("orderId") Long orderId);

    /**
     * 주문 배송지 변경의 잠금(C10 설계서 3-6) — 그 주문의 하위주문 전부를 id 오름차순으로. 준비 시작(#2)과 같은 행을
     * 다투므로, 잠근 뒤 본 상태가 커밋까지 유지된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM OrderDeliveryGroup g WHERE g.order.id = :orderId ORDER BY g.id ASC")
    List<OrderDeliveryGroup> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    /**
     * 발주서 행의 원본(34 설계서 3-1 ③) — 준비 시작 전이 뒤에 하위주문과 주문(배송지)을 잠금 읽기로 다시 읽는다.
     * 일반 SELECT 는 트랜잭션 시작 시점의 스냅숏을 보므로, 그사이 커밋된 배송지 변경을 놓친다.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT g FROM OrderDeliveryGroup g JOIN FETCH g.order WHERE g.id IN :ids ORDER BY g.id ASC")
    List<OrderDeliveryGroup> findAllWithOrderForShare(@Param("ids") Collection<Long> ids);

    /**
     * 클레임 신청의 잠금(35 설계서 3-6) — 하위주문 행만 잠근다(주문·마켓은 잠그지 않는다). 구매확정 배치의 조건부
     * UPDATE 와 같은 행을 다투므로, 배치가 먼저면 신청이 지고 신청이 먼저면 배치의 NOT EXISTS 가 건너뛴다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM OrderDeliveryGroup g WHERE g.id = :id")
    Optional<OrderDeliveryGroup> findForUpdate(@Param("id") Long id);

    @Query("SELECT g FROM OrderDeliveryGroup g JOIN FETCH g.order WHERE g.id = :id AND g.market.id = :marketId")
    Optional<OrderDeliveryGroup> findOwned(@Param("id") Long id, @Param("marketId") Long marketId);

    @Query("SELECT g FROM OrderDeliveryGroup g JOIN FETCH g.order WHERE g.id IN :ids AND g.market.id = :marketId")
    List<OrderDeliveryGroup> findAllOwned(@Param("ids") Collection<Long> ids, @Param("marketId") Long marketId);

    /** 사용자 취소 게이트(설계서 5-2) — 준비 시작 이후 그룹이 하나라도 있으면 바로 취소 불가. */
    @Query("SELECT COUNT(g) FROM OrderDeliveryGroup g WHERE g.order.id = :orderId "
            + "AND g.fulfillmentStatus NOT IN (showroomz.domain.order.type.FulfillmentStatus.PENDING, "
            + "showroomz.domain.order.type.FulfillmentStatus.NEW)")
    long countPreparedByOrder(@Param("orderId") Long orderId);

    /** 전역 송장 중복(§34-5 ③) — 종결 전 상태만. 택배사는 송장번호를 재사용하므로 UNIQUE 가 아니라 서비스 검사다. */
    @Query("SELECT g FROM OrderDeliveryGroup g JOIN FETCH g.order WHERE g.carrier = :carrier "
            + "AND g.trackingNumber = :trackingNumber AND g.fulfillmentStatus IN :statuses")
    List<OrderDeliveryGroup> findActiveByInvoice(@Param("carrier") DeliveryCarrier carrier,
                                                 @Param("trackingNumber") String trackingNumber,
                                                 @Param("statuses") Collection<FulfillmentStatus> statuses);

    /** 엑셀 업로드 매칭(§34-5 E3) — 주문번호는 한 주문이 하위주문 여러 건일 수 있어 하위주문번호와 함께 받는다. */
    @Query("SELECT g FROM OrderDeliveryGroup g JOIN FETCH g.order o WHERE g.market.id = :marketId "
            + "AND o.orderNumber IN :orderNumbers")
    List<OrderDeliveryGroup> findByOrderNumbers(@Param("marketId") Long marketId,
                                                @Param("orderNumbers") Collection<String> orderNumbers);

    @Query("SELECT g FROM OrderDeliveryGroup g JOIN FETCH g.order WHERE g.market.id = :marketId "
            + "AND g.subOrderNumber IN :subOrderNumbers")
    List<OrderDeliveryGroup> findBySubOrderNumbers(@Param("marketId") Long marketId,
                                                   @Param("subOrderNumbers") Collection<String> subOrderNumbers);

    /** 업로드 검증의 전역 중복 — 행마다 조회하면 1,000행에 1,000쿼리다. 번호 IN 으로 모아 메모리에서 택배사까지 맞춘다. */
    @Query("SELECT g FROM OrderDeliveryGroup g JOIN FETCH g.order WHERE g.trackingNumber IN :trackingNumbers "
            + "AND g.fulfillmentStatus IN :statuses")
    List<OrderDeliveryGroup> findActiveByTrackingNumbers(@Param("trackingNumbers") Collection<String> trackingNumbers,
                                                         @Param("statuses") Collection<FulfillmentStatus> statuses);

    // ------------------------------------------------------------------ 전이(설계서 2절)

    /**
     * #1 PENDING → NEW — PAID 전이와 같은 트랜잭션(설계서 5-1). 하위주문번호를 확정한다. 발송기한은 공구가 이미 종결됐을 때만
     * 들어온다 — 종결 훅이 먼저 채운 값이 있으면 덮지 않는다({@code COALESCE}).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.NEW, "
            + "g.subOrderNumber = :subOrderNumber, g.shipDueAt = COALESCE(g.shipDueAt, :shipDueAt) "
            + "WHERE g.id = :id AND g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.PENDING")
    int activate(@Param("id") Long id, @Param("subOrderNumber") String subOrderNumber,
                 @Param("shipDueAt") LocalDateTime shipDueAt);

    /**
     * 발송 기한 경과 자동 알림 대상 — 기한이 지났고 아직 발송 전 · 검토 중 취소 요청 없음(요청은 1영업일 자동 승인이라 대상이
     * 아니다) · 오늘 아직 알리지 않음(1009 기획 수정본 8-4).
     */
    @Query("SELECT g.id FROM OrderDeliveryGroup g WHERE g.shipDueAt IS NOT NULL AND g.shipDueAt < :now "
            + "AND g.fulfillmentStatus IN (showroomz.domain.order.type.FulfillmentStatus.NEW, "
            + "    showroomz.domain.order.type.FulfillmentStatus.PREPARING) "
            + "AND (g.lastOverdueNoticeAt IS NULL OR g.lastOverdueNoticeAt < :todayStart) "
            + "AND NOT EXISTS (SELECT r FROM OrderCancelRequest r WHERE r.deliveryGroup = g "
            + "    AND r.status = showroomz.domain.order.type.CancelRequestStatus.PENDING) ORDER BY g.id ASC")
    List<Long> findShipOverdueToNotify(@Param("now") LocalDateTime now, @Param("todayStart") LocalDateTime todayStart,
                                       Pageable pageable);

    /** 자동 알림 1회 기록 — 같은 날 두 번 세지 않는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.overdueNoticeCount = g.overdueNoticeCount + 1, g.lastOverdueNoticeAt = :now "
            + "WHERE g.id = :id AND (g.lastOverdueNoticeAt IS NULL OR g.lastOverdueNoticeAt < :todayStart)")
    int recordOverdueNotice(@Param("id") Long id, @Param("now") LocalDateTime now,
                            @Param("todayStart") LocalDateTime todayStart);

    /** 배송완료일 정정(어드민 06a B3) — 소비자 수령일 이의. 구매확정 기산점도 같은 날로 되돌린다(교환 재시작이 없을 때). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.deliveredAt = :deliveredAt, "
            + "g.deliveredSource = showroomz.domain.order.type.DeliveredSource.ADMIN, g.deliveredBy = :adminId "
            + "WHERE g.id = :id AND g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.DELIVERED")
    int correctDeliveredAt(@Param("id") Long id, @Param("deliveredAt") LocalDateTime deliveredAt,
                           @Param("adminId") Long adminId);

    /** 어드민 예외 관리 — 발송 기한 경과(06d 처리 지연). 검토 중 취소 요청 건은 1영업일 자동 승인이라 뺀다. */
    @Query("SELECT g FROM OrderDeliveryGroup g JOIN FETCH g.order WHERE g.shipDueAt IS NOT NULL AND g.shipDueAt < :now "
            + "AND g.fulfillmentStatus IN (showroomz.domain.order.type.FulfillmentStatus.NEW, "
            + "    showroomz.domain.order.type.FulfillmentStatus.PREPARING) "
            + "AND NOT EXISTS (SELECT r FROM OrderCancelRequest r WHERE r.deliveryGroup = g "
            + "    AND r.status = showroomz.domain.order.type.CancelRequestStatus.PENDING) ORDER BY g.shipDueAt ASC")
    List<OrderDeliveryGroup> findShipOverdueForAdmin(@Param("now") LocalDateTime now);

    /**
     * 어드민 예외 관리 — 배송 이상(집화 확인 필요 · 추적 정지 · 반송 중 · 06d 배송 예외). 반송은 완료 감지 뒤에도 상태가
     * {@code RETURNING}에 머무르므로(환불로 종결) 완료가 감지된 건은 뺀다 — 결과는 환불 관리의 몫이다.
     */
    @Query("SELECT g FROM OrderDeliveryGroup g JOIN FETCH g.order WHERE "
            + "(g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.SHIPPING AND g.trackingAlert IS NOT NULL) "
            + "OR (g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.RETURNING "
            + "AND g.returnCompletedAt IS NULL) ORDER BY g.id ASC")
    List<OrderDeliveryGroup> findDeliveryExceptionsForAdmin();

    /** 구매확정 기산점 직접 설정 — 배송완료일 정정이 기산점을 같은 만큼 옮길 때(어드민 06a B3). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.confirmRestartAt = :at WHERE g.id = :id")
    int setConfirmRestartAt(@Param("id") Long id, @Param("at") LocalDateTime at);

    /** 운영자 대행 송장 등록(어드민 06a B4) — 브랜드 송장 등록과 같은 조건 · 마켓 조건 없음. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.SHIPPING, "
            + "g.carrier = :carrier, g.trackingNumber = :trackingNumber, g.shippedAt = :now "
            + "WHERE g.id = :id "
            + "AND g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.PREPARING "
            + "AND NOT EXISTS (SELECT r FROM OrderCancelRequest r WHERE r.deliveryGroup = g "
            + "    AND r.status = showroomz.domain.order.type.CancelRequestStatus.PENDING) "
            + "AND NOT EXISTS (SELECT p FROM Payment p WHERE p.order = g.order "
            + "    AND p.status = showroomz.domain.payment.type.PaymentStatus.CANCEL_REQUESTED)")
    int registerInvoiceByAdmin(@Param("id") Long id, @Param("carrier") DeliveryCarrier carrier,
                               @Param("trackingNumber") String trackingNumber, @Param("now") LocalDateTime now);

    /** 운영자 대행 직권 취소(어드민 06a B4 · B5 미발송분) — 브랜드 직권 취소와 같은 조건 · 마켓 조건 없음. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.statusAtCancel = g.fulfillmentStatus, "
            + "g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.CANCELLED, "
            + "g.cancelledAt = :now, g.cancelType = showroomz.domain.order.type.OrderCancelType.SELLER_DIRECT, "
            + "g.cancelReasonCode = :reasonCode, g.cancelReasonDetail = :reasonDetail "
            + "WHERE g.id = :id "
            + "AND g.fulfillmentStatus IN (showroomz.domain.order.type.FulfillmentStatus.NEW, "
            + "    showroomz.domain.order.type.FulfillmentStatus.PREPARING) "
            + "AND NOT EXISTS (SELECT r FROM OrderCancelRequest r WHERE r.deliveryGroup = g "
            + "    AND r.status = showroomz.domain.order.type.CancelRequestStatus.PENDING) "
            + "AND NOT EXISTS (SELECT p FROM Payment p WHERE p.order = g.order "
            + "    AND p.status = showroomz.domain.payment.type.PaymentStatus.CANCEL_REQUESTED)")
    int cancelByAdmin(@Param("id") Long id,
                      @Param("reasonCode") showroomz.domain.order.type.SellerCancelReason reasonCode,
                      @Param("reasonDetail") String reasonDetail, @Param("now") LocalDateTime now);

    /** 공구 종결 훅의 대상 N 값들 — 아직 기한이 없는 결제 대기 · 신규 · 상품준비중(1009 기획 수정본 1-2). */
    @Query("SELECT DISTINCT g.shipDueBusinessDays FROM OrderDeliveryGroup g WHERE g.groupBuy.id = :groupBuyId "
            + "AND g.shipDueAt IS NULL AND g.fulfillmentStatus IN (showroomz.domain.order.type.FulfillmentStatus.PENDING, "
            + "showroomz.domain.order.type.FulfillmentStatus.NEW, showroomz.domain.order.type.FulfillmentStatus.PREPARING)")
    List<Integer> findShipDueBusinessDaysAwaitingDue(@Param("groupBuyId") Long groupBuyId);

    /**
     * 공구 종결 시 발송기한 확정 — 같은 N 의 그룹을 한 번에. 공구 종결 트랜잭션 안이라 영속성 컨텍스트를 비우지 않는다
     * (종결 경로가 이어서 공구 엔티티를 고친다).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.shipDueAt = :shipDueAt WHERE g.groupBuy.id = :groupBuyId "
            + "AND g.shipDueBusinessDays = :businessDays AND g.shipDueAt IS NULL "
            + "AND g.fulfillmentStatus IN (showroomz.domain.order.type.FulfillmentStatus.PENDING, "
            + "showroomz.domain.order.type.FulfillmentStatus.NEW, showroomz.domain.order.type.FulfillmentStatus.PREPARING)")
    int assignShipDue(@Param("groupBuyId") Long groupBuyId, @Param("businessDays") Integer businessDays,
                      @Param("shipDueAt") LocalDateTime shipDueAt);

    /** #2 NEW → PREPARING — 소비자 단순 취소권 종료(약관 제17조②). 되돌리기 없음. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.PREPARING, "
            + "g.prepareStartedAt = :now, g.prepareStartedBy = :sellerId "
            + "WHERE g.id = :id AND g.market.id = :marketId "
            + "AND g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.NEW "
            + "AND NOT EXISTS (SELECT r FROM OrderCancelRequest r WHERE r.deliveryGroup = g "
            + "    AND r.status = showroomz.domain.order.type.CancelRequestStatus.PENDING) "
            + "AND NOT EXISTS (SELECT p FROM Payment p WHERE p.order = g.order "
            + "    AND p.status = showroomz.domain.payment.type.PaymentStatus.CANCEL_REQUESTED)")
    int startPreparation(@Param("id") Long id, @Param("marketId") Long marketId, @Param("sellerId") Long sellerId,
                         @Param("now") LocalDateTime now);

    /** #3 PREPARING → SHIPPING — 송장 등록 확정. {@code shipped_at}이 발송기한 판정값이다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.SHIPPING, "
            + "g.carrier = :carrier, g.trackingNumber = :trackingNumber, g.shippedAt = :now "
            + "WHERE g.id = :id AND g.market.id = :marketId "
            + "AND g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.PREPARING "
            + "AND NOT EXISTS (SELECT r FROM OrderCancelRequest r WHERE r.deliveryGroup = g "
            + "    AND r.status = showroomz.domain.order.type.CancelRequestStatus.PENDING) "
            + "AND NOT EXISTS (SELECT p FROM Payment p WHERE p.order = g.order "
            + "    AND p.status = showroomz.domain.payment.type.PaymentStatus.CANCEL_REQUESTED)")
    int registerInvoice(@Param("id") Long id, @Param("marketId") Long marketId,
                        @Param("carrier") DeliveryCarrier carrier, @Param("trackingNumber") String trackingNumber,
                        @Param("now") LocalDateTime now);

    /**
     * 송장 수정(§34-6) — 배송중만 · 반송중 불가. {@code shipped_at}은 유지한다(수정으로 기한 위반이 세탁되면 안 된다).
     * 알림·최종 갱신·집화 시각을 리셋해 감시 배치가 새 송장 기준으로 다시 판정한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.carrier = :carrier, g.trackingNumber = :trackingNumber, "
            + "g.trackingAlert = NULL, g.lastTrackingAt = NULL, g.pickedUpAt = NULL "
            + "WHERE g.id = :id AND g.market.id = :marketId "
            + "AND g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.SHIPPING")
    int updateInvoice(@Param("id") Long id, @Param("marketId") Long marketId,
                      @Param("carrier") DeliveryCarrier carrier, @Param("trackingNumber") String trackingNumber);

    /**
     * #7 직권 취소(E5) — NEW·PREPARING 허용(설계서 0-7) · 취소 요청 PENDING 이 걸려 있으면 선처리 요구로 0행.
     * 소비자 취소가 PG 응답 대기(CANCEL_REQUESTED)인 주문도 0행이다 — 그 취소가 수렴하면서 재고·환불을 처리하므로,
     * 여기서 겹치면 재고가 두 번 돌아가고 PG 환불과 별개로 운영자 환불 큐까지 쌓인다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.statusAtCancel = g.fulfillmentStatus, "
            + "g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.CANCELLED, "
            + "g.cancelledAt = :now, g.cancelType = showroomz.domain.order.type.OrderCancelType.SELLER_DIRECT, "
            + "g.cancelReasonCode = :reasonCode, g.cancelReasonDetail = :reasonDetail "
            + "WHERE g.id = :id AND g.market.id = :marketId "
            + "AND g.fulfillmentStatus IN (showroomz.domain.order.type.FulfillmentStatus.NEW, "
            + "    showroomz.domain.order.type.FulfillmentStatus.PREPARING) "
            + "AND NOT EXISTS (SELECT r FROM OrderCancelRequest r WHERE r.deliveryGroup = g "
            + "    AND r.status = showroomz.domain.order.type.CancelRequestStatus.PENDING) "
            + "AND NOT EXISTS (SELECT p FROM Payment p WHERE p.order = g.order "
            + "    AND p.status = showroomz.domain.payment.type.PaymentStatus.CANCEL_REQUESTED)")
    int cancelDirect(@Param("id") Long id, @Param("marketId") Long marketId,
                     @Param("reasonCode") showroomz.domain.order.type.SellerCancelReason reasonCode,
                     @Param("reasonDetail") String reasonDetail, @Param("now") LocalDateTime now);

    /** #7 취소 요청 전 항목 승인 — 전 항목 승인일 때만 취소 탭(§34-8). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.statusAtCancel = g.fulfillmentStatus, "
            + "g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.CANCELLED, "
            + "g.cancelledAt = :now, g.cancelType = showroomz.domain.order.type.OrderCancelType.REQUEST_APPROVED "
            + "WHERE g.id = :id AND g.fulfillmentStatus IN (showroomz.domain.order.type.FulfillmentStatus.NEW, "
            + "    showroomz.domain.order.type.FulfillmentStatus.PREPARING)")
    int cancelByRequestApproval(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** #7 소비자 전액 취소 — PG 취소 확인 뒤 주문 전이와 같은 트랜잭션에서(설계서 5-2). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.statusAtCancel = g.fulfillmentStatus, "
            + "g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.CANCELLED, "
            + "g.cancelledAt = :now, g.cancelType = showroomz.domain.order.type.OrderCancelType.CONSUMER "
            + "WHERE g.order.id = :orderId AND g.fulfillmentStatus IN "
            + "(showroomz.domain.order.type.FulfillmentStatus.NEW, showroomz.domain.order.type.FulfillmentStatus.PREPARING)")
    int cancelByConsumer(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);

    // ------------------------------------------------------------------ 감시 배치(설계서 3-3 · 3-4)
    //
    // 추적 반영 전이는 전부 「폴링 당시의 송장(carrier · tracking_number)」을 WHERE 에 넣는다 — 폴링과 반영 사이에
    // 셀러가 송장을 고치면 구 송장의 결과(배송완료·반송·이벤트 시각)가 새 송장에 덮이면 안 된다(N11).

    /** 첫 이벤트가 곧 집화다(집화 전에는 추적 데이터가 없다) — {@code picked_up_at}은 비어 있을 때 1회만 적는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.lastTrackingAt = :at, g.pickedUpAt = COALESCE(g.pickedUpAt, :at) "
            + "WHERE g.id = :id "
            + "AND g.carrier = :carrier AND g.trackingNumber = :trackingNumber "
            + "AND g.fulfillmentStatus IN (showroomz.domain.order.type.FulfillmentStatus.SHIPPING, "
            + "    showroomz.domain.order.type.FulfillmentStatus.RETURNING)")
    int touchTracking(@Param("id") Long id, @Param("carrier") DeliveryCarrier carrier,
                      @Param("trackingNumber") String trackingNumber, @Param("at") LocalDateTime at);

    /**
     * 택배사별 실제 소요일 표본 — [carrier, picked_up_at, delivered_at]. 추적이 확인한 배송완료만(직권 처리는 실제 도착 시각이 아니다).
     * 집계는 {@code DeliveryArrivalEstimator}가 한다.
     */
    @Query("SELECT g.carrier, g.pickedUpAt, g.deliveredAt FROM OrderDeliveryGroup g "
            + "WHERE g.deliveredAt >= :since AND g.pickedUpAt IS NOT NULL AND g.carrier IS NOT NULL "
            + "AND g.deliveredSource = showroomz.domain.order.type.DeliveredSource.TRACKER")
    List<Object[]> findTransitSamples(@Param("since") LocalDateTime since);

    /** #5 SHIPPING → DELIVERED — 자동 확인. 운영자 직권은 어드민 모듈이 별도 메서드로 간다(범위 밖). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.DELIVERED, "
            + "g.deliveredAt = :deliveredAt, g.deliveredSource = showroomz.domain.order.type.DeliveredSource.TRACKER, "
            + "g.trackingAlert = NULL "
            + "WHERE g.id = :id AND g.carrier = :carrier AND g.trackingNumber = :trackingNumber "
            + "AND g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.SHIPPING")
    int markDeliveredByTracker(@Param("id") Long id, @Param("carrier") DeliveryCarrier carrier,
                               @Param("trackingNumber") String trackingNumber,
                               @Param("deliveredAt") LocalDateTime deliveredAt);

    /** #4 SHIPPING → RETURNING — 반송 코드 감지. 구매확정 타이머 취소는 구조적이다(DELIVERED 가 아니므로). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.RETURNING, "
            + "g.returnDetectedAt = :at, g.trackingAlert = NULL "
            + "WHERE g.id = :id AND g.carrier = :carrier AND g.trackingNumber = :trackingNumber "
            + "AND g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.SHIPPING")
    int markReturning(@Param("id") Long id, @Param("carrier") DeliveryCarrier carrier,
                      @Param("trackingNumber") String trackingNumber, @Param("at") LocalDateTime at);

    /** 반송 완료 입고 — 1회만(환불 큐 중복 편입 방지). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.returnCompletedAt = :at WHERE g.id = :id "
            + "AND g.carrier = :carrier AND g.trackingNumber = :trackingNumber "
            + "AND g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.RETURNING "
            + "AND g.returnCompletedAt IS NULL")
    int markReturnCompleted(@Param("id") Long id, @Param("carrier") DeliveryCarrier carrier,
                            @Param("trackingNumber") String trackingNumber, @Param("at") LocalDateTime at);

    /** 배지 설정 — 같은 값이면 0행(이력 1회 규칙). STALLED 는 PICKUP_UNCONFIRMED 를 덮어쓸 수 있다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.trackingAlert = :alert WHERE g.id = :id "
            + "AND g.carrier = :carrier AND g.trackingNumber = :trackingNumber "
            + "AND g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.SHIPPING "
            + "AND (g.trackingAlert IS NULL OR g.trackingAlert <> :alert)")
    int setTrackingAlert(@Param("id") Long id, @Param("carrier") DeliveryCarrier carrier,
                         @Param("trackingNumber") String trackingNumber, @Param("alert") TrackingAlert alert);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.trackingAlert = NULL WHERE g.id = :id "
            + "AND g.carrier = :carrier AND g.trackingNumber = :trackingNumber AND g.trackingAlert IS NOT NULL")
    int clearTrackingAlert(@Param("id") Long id, @Param("carrier") DeliveryCarrier carrier,
                           @Param("trackingNumber") String trackingNumber);

    /**
     * #6 DELIVERED → CONFIRMED — 배송완료 + 7일(약관 제19조①). 기준 시각은 교환 재발송이 도착했으면 그 시각이다
     * ({@code confirm_restart_at} · 35 설계서 3-6). <b>보류 클레임</b>(진행 중이고 거절되지 않은 반품·교환)이 있으면
     * 확정하지 않는다 — 거절 보류·거절 반송만 남은 하위주문은 확정된다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.CONFIRMED, "
            + "g.confirmedAt = :now "
            + "WHERE g.id = :id AND g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.DELIVERED "
            + "AND g.confirmPausedAt IS NULL "
            + "AND COALESCE(g.confirmRestartAt, g.deliveredAt) <= :threshold "
            + "AND NOT EXISTS (SELECT c FROM OrderClaim c WHERE c.deliveryGroup = g "
            + "    AND c.status <> showroomz.domain.order.type.ClaimStatus.COMPLETED AND c.rejectedAt IS NULL)")
    int confirmPurchase(@Param("id") Long id, @Param("now") LocalDateTime now,
                        @Param("threshold") LocalDateTime threshold);

    /**
     * 교환 재발송 도착 — 구매확정 N일을 그 시각부터 다시 센다(35 설계서 3-4 · §35-7). 최초 배송완료 시각은 덮지 않는다.
     * 같은 하위주문에 교환이 여럿이면 가장 늦은 도착이 기준이다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.confirmRestartAt = :at "
            + "WHERE g.id = :id AND (g.confirmRestartAt IS NULL OR g.confirmRestartAt < :at)")
    int restartConfirmTimer(@Param("id") Long id, @Param("at") LocalDateTime at);

    /** 구매확정 타이머 정지 — 배송완료이고 아직 멈추지 않았을 때만(먼저 멈춘 시각을 유지한다 · 1009 기획 수정본 4-2). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.confirmPausedAt = :now WHERE g.id = :id "
            + "AND g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.DELIVERED "
            + "AND g.confirmPausedAt IS NULL")
    int pauseConfirmTimer(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 구매확정 타이머 재개 — 정지한 시간만큼 민 기산점을 적고 정지를 푼다. 읽은 정지 시각을 WHERE 에 넣어 두 재개가 겹치면
     * 하나만 통과한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderDeliveryGroup g SET g.confirmRestartAt = :base, g.confirmPausedAt = :pausedAt2 "
            + "WHERE g.id = :id AND g.confirmPausedAt = :pausedAt")
    int resumeConfirmTimer(@Param("id") Long id, @Param("pausedAt") LocalDateTime pausedAt,
                           @Param("base") LocalDateTime base, @Param("pausedAt2") LocalDateTime nextPausedAt);

    /**
     * 타이머를 멈춰 두는 진행 중 클레임이 있는가 — 반려되지 않았고 종결 전 · 결제 대기(아직 접수 전) 제외.
     * 확정 조건의 NOT EXISTS 와 같은 기준이다(결제 대기만 다르다 — 확정은 안전하게 막고, 정지는 접수된 것만 센다).
     */
    @Query("SELECT COUNT(c) > 0 FROM OrderClaim c WHERE c.deliveryGroup.id = :id "
            + "AND c.status <> showroomz.domain.order.type.ClaimStatus.COMPLETED "
            + "AND c.status <> showroomz.domain.order.type.ClaimStatus.PAYMENT_PENDING AND c.rejectedAt IS NULL")
    boolean existsTimerHoldingClaim(@Param("id") Long id);

    // ------------------------------------------------------------------ 배치 대상

    /** 추적 대상 — id 커서({@code afterId} 초과)로 이어 읽는다. 첫 페이지는 0. */
    @Query("SELECT g FROM OrderDeliveryGroup g WHERE g.fulfillmentStatus IN :statuses "
            + "AND g.carrier IS NOT NULL AND g.trackingNumber IS NOT NULL AND g.id > :afterId ORDER BY g.id ASC")
    List<OrderDeliveryGroup> findTrackingTargets(@Param("statuses") Collection<FulfillmentStatus> statuses,
                                                 @Param("afterId") Long afterId, Pageable pageable);

    /** 구매확정 대상 — {@link #confirmPurchase}와 같은 조건(보류 클레임이 있는 하위주문은 회차마다 다시 집히지 않는다). */
    @Query("SELECT g.id FROM OrderDeliveryGroup g "
            + "WHERE g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.DELIVERED "
            + "AND g.confirmPausedAt IS NULL "
            + "AND COALESCE(g.confirmRestartAt, g.deliveredAt) <= :threshold "
            + "AND NOT EXISTS (SELECT c FROM OrderClaim c WHERE c.deliveryGroup = g "
            + "    AND c.status <> showroomz.domain.order.type.ClaimStatus.COMPLETED AND c.rejectedAt IS NULL) "
            + "ORDER BY COALESCE(g.confirmRestartAt, g.deliveredAt) ASC, g.id ASC")
    List<Long> findIdsToConfirm(@Param("threshold") LocalDateTime threshold, Pageable pageable);

    // ------------------------------------------------------------------ 카운트(요약 바 · 탭)

    /**
     * 상태별 건수 — 결제된 적 있는 주문만({@code paid_at}). 결제 후 소비자가 취소한 주문은 {@code orders.status}가
     * CANCELLED 지만 취소 탭에 있어야 한다. 탭 카운트·요약 바는 같은 응답에서 같은 데이터를 읽는다(동시 갱신).
     */
    @Query("SELECT g.fulfillmentStatus, COUNT(g) FROM OrderDeliveryGroup g WHERE g.market.id = :marketId "
            + "AND g.order.paidAt IS NOT NULL GROUP BY g.fulfillmentStatus")
    List<Object[]> countByStatus(@Param("marketId") Long marketId);

    /**
     * 검토 중 취소 요청이 걸린 그룹 수 — 이행 상태별. NEW·PREPARING 탭에서 빼고 취소 요청 탭에 더한다.
     * 취소 요청 탭 목록과 같은 기준(작업 큐 상태 · 결제된 주문)만 센다 — 작업 큐 밖에 남은 요청을 세면 카운트와 목록이 어긋난다.
     */
    @Query("SELECT g.fulfillmentStatus, COUNT(r) FROM OrderCancelRequest r JOIN r.deliveryGroup g "
            + "WHERE g.market.id = :marketId AND r.status = showroomz.domain.order.type.CancelRequestStatus.PENDING "
            + "AND g.order.paidAt IS NOT NULL "
            + "AND g.fulfillmentStatus IN (showroomz.domain.order.type.FulfillmentStatus.NEW, "
            + "    showroomz.domain.order.type.FulfillmentStatus.PREPARING) "
            + "GROUP BY g.fulfillmentStatus")
    List<Object[]> countPendingCancelByStatus(@Param("marketId") Long marketId);

    /** 공구별 이행 상태 분포 — 판매 포트 {@code readClosure}(공구 정산 게이트)의 원천(설계서 5-3). */
    @Query("SELECT g.fulfillmentStatus, COUNT(g) FROM OrderDeliveryGroup g WHERE g.groupBuy.id = :groupBuyId "
            + "AND g.order.paidAt IS NOT NULL GROUP BY g.fulfillmentStatus")
    List<Object[]> countByStatusForGroupBuy(@Param("groupBuyId") Long groupBuyId);

    /** 배송 이상 중 배지 분 — 요약 바 「배송 이상」 = 이 값 + RETURNING 카운트 합산(§34-2). */
    @Query("SELECT COUNT(g) FROM OrderDeliveryGroup g WHERE g.market.id = :marketId "
            + "AND g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.SHIPPING "
            + "AND g.trackingAlert IS NOT NULL")
    long countTrackingAlerts(@Param("marketId") Long marketId);
}
