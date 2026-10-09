package showroomz.domain.order.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.type.OrderProductStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OrderProductRepository extends JpaRepository<OrderProduct, Long> {

    @Query("""
            SELECT op FROM OrderProduct op
            JOIN op.order o
            WHERE o.user.id = :userId
              AND op.status = :status
              AND op.review IS NULL
            ORDER BY op.orderDate DESC
            """)
    List<OrderProduct> findWritableByUserId(
            @Param("userId") Long userId,
            @Param("status") OrderProductStatus status);

    boolean existsByIdAndOrder_User_Id(Long orderProductId, Long userId);

    /**
     * C15-4 탈퇴 차단 판정 — 아직 끝나지 않은 주문 상품 수.
     * 구매 확정(PURCHASE_CONFIRMED)·취소(CANCELLED)를 뺀 나머지가 "진행 중"이다.
     * TODO: 교환·환불 기간까지 진행 중으로 볼지(설계 미결정) 정해지면 조건을 넓힌다.
     */
    @Query("""
            SELECT COUNT(op) FROM OrderProduct op
            JOIN op.order o
            WHERE o.user.id = :userId
              AND op.status NOT IN :finishedStatuses
            """)
    long countOngoingByUserId(
            @Param("userId") Long userId,
            @Param("finishedStatuses") Collection<OrderProductStatus> finishedStatuses);

    /** 주문의 상품 줄 — 재고 복원·응답 조립. */
    @Query("SELECT op FROM OrderProduct op JOIN FETCH op.variant v JOIN FETCH v.product WHERE op.order.id = :orderId ORDER BY op.id ASC")
    List<OrderProduct> findByOrderIdWithVariant(@Param("orderId") Long orderId);

    /**
     * 소비자 앱 주문 내역(C10 설계서 2-4 #2) — 페이지의 주문들의 항목을 배송 그룹과 함께 IN 1번.
     * 리뷰도 함께 올린다 — 역방향 OneToOne 은 지연 로딩이 안 돼, 빼면 항목마다 리뷰 조회가 한 번씩 나간다.
     */
    @Query("SELECT op FROM OrderProduct op LEFT JOIN FETCH op.deliveryGroup JOIN FETCH op.variant v JOIN FETCH v.product "
            + "LEFT JOIN FETCH op.review WHERE op.order.id IN :orderIds ORDER BY op.id ASC")
    List<OrderProduct> findByOrderIdsWithGroup(@Param("orderIds") Collection<Long> orderIds);

    /** 주문 상품 전체의 상태를 한 번에 — 주문 전이의 부수 효과(4-1). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderProduct op SET op.status = :to WHERE op.order.id = :orderId AND op.status IN :from")
    int transitionByOrder(@Param("orderId") Long orderId, @Param("from") Collection<OrderProductStatus> from,
                          @Param("to") OrderProductStatus to);

    /**
     * 결제 대기 예약 수량 — 옵션별(선행 수정 계획서 3-8). 셀러 재고 저장은 「입력값 − 예약」, 조회는 「저장값 + 예약」이다.
     * 재고를 돌려놓은 주문({@code stock_released_at}이 있는 주문)은 세지 않는다.
     */
    @Query("SELECT op.variant.variantId, COALESCE(SUM(op.quantity), 0) FROM OrderProduct op JOIN op.order o "
            + "WHERE op.variant.variantId IN :variantIds "
            + "AND o.status = showroomz.domain.order.type.OrderStatus.PAYMENT_PENDING AND o.stockReleasedAt IS NULL "
            + "GROUP BY op.variant.variantId")
    List<Object[]> sumReservedQuantityByVariantIds(@Param("variantIds") Collection<Long> variantIds);

    /** 상품별 예약 합 — 셀러 상품 목록의 재고 칸. */
    @Query("SELECT op.variant.product.productId, COALESCE(SUM(op.quantity), 0) FROM OrderProduct op JOIN op.order o "
            + "WHERE op.variant.product.productId IN :productIds "
            + "AND o.status = showroomz.domain.order.type.OrderStatus.PAYMENT_PENDING AND o.stockReleasedAt IS NULL "
            + "GROUP BY op.variant.product.productId")
    List<Object[]> sumReservedQuantityByProductIds(@Param("productIds") Collection<Long> productIds);

    // ------------------------------------------------------------------ 하위주문 이행(34 설계서)

    /** 하위주문(그룹)들의 항목 — 목록 행 확장(▸)·상세 항목 표·발주서. */
    @Query("SELECT op FROM OrderProduct op WHERE op.deliveryGroup.id IN :deliveryGroupIds ORDER BY op.id ASC")
    List<OrderProduct> findByDeliveryGroupIds(@Param("deliveryGroupIds") Collection<Long> deliveryGroupIds);

    /** 그룹 전 항목 전이 — 구매확정(PAID → PURCHASE_CONFIRMED)·직권 취소에 쓴다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderProduct op SET op.status = :to WHERE op.deliveryGroup.id = :deliveryGroupId AND op.status IN :from")
    int transitionByGroup(@Param("deliveryGroupId") Long deliveryGroupId,
                          @Param("from") Collection<OrderProductStatus> from, @Param("to") OrderProductStatus to);

    /** 항목 단위 취소 확정 — 1행일 때만 재고를 되돌린다(1회 규칙 · StockReleaser 와 같은 수법). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderProduct op SET op.status = showroomz.domain.order.type.OrderProductStatus.CANCELLED, "
            + "op.cancelType = :cancelType, op.cancelledAt = :now "
            + "WHERE op.id = :orderProductId AND op.status = showroomz.domain.order.type.OrderProductStatus.PAID")
    int cancelItem(@Param("orderProductId") Long orderProductId,
                   @Param("cancelType") showroomz.domain.order.type.OrderCancelType cancelType,
                   @Param("now") LocalDateTime now);

    /** 그룹 전 항목 취소 — 직권 취소(하위주문 전체). 취소된 항목 수를 돌려준다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderProduct op SET op.status = showroomz.domain.order.type.OrderProductStatus.CANCELLED, "
            + "op.cancelType = :cancelType, op.cancelledAt = :now "
            + "WHERE op.deliveryGroup.id = :deliveryGroupId "
            + "AND op.status = showroomz.domain.order.type.OrderProductStatus.PAID")
    int cancelItemsByGroup(@Param("deliveryGroupId") Long deliveryGroupId,
                           @Param("cancelType") showroomz.domain.order.type.OrderCancelType cancelType,
                           @Param("now") LocalDateTime now);

    /** 소비자 전액 취소의 항목 취소 메타 — StockReleaser 가 이미 CANCELLED 로 내린 항목에 유형·시각만 채운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderProduct op SET op.cancelType = :cancelType, op.cancelledAt = :now "
            + "WHERE op.order.id = :orderId AND op.status = showroomz.domain.order.type.OrderProductStatus.CANCELLED "
            + "AND op.cancelType IS NULL")
    int fillCancelMetaByOrder(@Param("orderId") Long orderId,
                              @Param("cancelType") showroomz.domain.order.type.OrderCancelType cancelType,
                              @Param("now") LocalDateTime now);

    /** 그룹의 미취소 항목 수 — 0이면 전 항목 취소라 그룹도 취소 탭으로 간다(§34-8). */
    @Query("SELECT COUNT(op) FROM OrderProduct op WHERE op.deliveryGroup.id = :deliveryGroupId "
            + "AND op.status <> showroomz.domain.order.type.OrderProductStatus.CANCELLED")
    long countActiveByGroup(@Param("deliveryGroupId") Long deliveryGroupId);

    // ------------------------------------------------------------------ 반품·교환(35 설계서)

    /** 클레임 신청의 잔여 수량 검사 — 항목을 id 오름차순으로 잠근다(동시 신청 2건이 같은 수량을 나눠 갖지 못하게). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT op FROM OrderProduct op WHERE op.id IN :ids ORDER BY op.id ASC")
    List<OrderProduct> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    /** 반품 검수 통과 — 반품 수량을 올린다. 주문 수량을 넘으면 0행. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderProduct op SET op.returnedQuantity = op.returnedQuantity + :quantity "
            + "WHERE op.id = :orderProductId AND op.returnedQuantity + :quantity <= op.quantity")
    int addReturnedQuantity(@Param("orderProductId") Long orderProductId, @Param("quantity") int quantity);

    /** 전량 반품된 항목을 RETURNED 로 — 구매확정 배치가 PURCHASE_CONFIRMED 로 올리지 않게 한다. 운영자 개설 하자 반품은 구매확정 항목에서 온다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderProduct op SET op.status = showroomz.domain.order.type.OrderProductStatus.RETURNED "
            + "WHERE op.id = :orderProductId AND op.returnedQuantity >= op.quantity "
            + "AND op.status IN (showroomz.domain.order.type.OrderProductStatus.PAID, "
            + "    showroomz.domain.order.type.OrderProductStatus.PURCHASE_CONFIRMED)")
    int markReturnedIfFull(@Param("orderProductId") Long orderProductId);

    // ------------------------------------------------------------------ 판매 관리 포트(7-2)

    /**
     * 취소·반품 반영 판매 실적 — [주문 수, 금액]. 결제 완료 주문의 취소되지 않은 줄만, 유효 수량
     * ({@code quantity − returned_quantity})으로 센다. 전량 반품(RETURNED) 줄은 뺀다(35 설계서 5-2).
     */
    @Query("SELECT COUNT(DISTINCT op.order.id), COALESCE(SUM(op.price * (op.quantity - op.returnedQuantity)), 0) "
            + "FROM OrderProduct op JOIN op.order o "
            + "WHERE op.groupBuy.id = :groupBuyId AND o.status IN :orderStatuses "
            + "AND op.status NOT IN (showroomz.domain.order.type.OrderProductStatus.CANCELLED, "
            + "showroomz.domain.order.type.OrderProductStatus.RETURNED)")
    List<Object[]> sumSalesByGroupBuy(@Param("groupBuyId") Long groupBuyId,
                                      @Param("orderStatuses") Collection<showroomz.domain.order.type.OrderStatus> orderStatuses);

    /** 상품별 판매 수량 — [productId, quantity]. 유효 수량 기준. */
    @Query("SELECT op.variant.product.productId, COALESCE(SUM(op.quantity - op.returnedQuantity), 0) "
            + "FROM OrderProduct op JOIN op.order o "
            + "WHERE op.groupBuy.id = :groupBuyId AND o.status IN :orderStatuses "
            + "AND op.status NOT IN (showroomz.domain.order.type.OrderProductStatus.CANCELLED, "
            + "showroomz.domain.order.type.OrderProductStatus.RETURNED) "
            + "GROUP BY op.variant.product.productId ORDER BY op.variant.product.productId ASC")
    List<Object[]> sumQuantityByProductForGroupBuy(@Param("groupBuyId") Long groupBuyId,
                                                   @Param("orderStatuses") Collection<showroomz.domain.order.type.OrderStatus> orderStatuses);

    /** 기준 시각 이후 결제 완료 주문 수 — 스튜디오 「숨김 이후 0건」. */
    @Query("SELECT COUNT(DISTINCT op.order.id) FROM OrderProduct op JOIN op.order o "
            + "WHERE op.groupBuy.id = :groupBuyId AND o.status IN :orderStatuses AND o.paidAt >= :since")
    long countOrdersSince(@Param("groupBuyId") Long groupBuyId,
                          @Param("orderStatuses") Collection<showroomz.domain.order.type.OrderStatus> orderStatuses,
                          @Param("since") LocalDateTime since);

    /** 이 공구 주문에 연결된 1:1 문의 수 — {@code OneToOneInquiry.orderId}는 연관이 아니라 id 컬럼이다. */
    @Query("SELECT COUNT(i) FROM OneToOneInquiry i WHERE i.createdAt >= :since "
            + "AND i.orderId IN (SELECT DISTINCT op.order.id FROM OrderProduct op WHERE op.groupBuy.id = :groupBuyId)")
    long countOneToOneInquiriesSince(@Param("groupBuyId") Long groupBuyId, @Param("since") LocalDateTime since);
}
