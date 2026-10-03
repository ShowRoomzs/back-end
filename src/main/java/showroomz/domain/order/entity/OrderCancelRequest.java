package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.order.type.CancelRequestReason;
import showroomz.domain.order.type.CancelRequestStatus;
import showroomz.domain.order.type.FulfillmentStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 취소 요청(34 설계서 1-5) — 소비자 앱(C10)이 만들고 파트너센터가 승인·거부한다.
 *
 * <p>이행 상태가 아니라 별도 테이블이다(설계서 0-2) — PENDING 행의 존재가 곧 「취소 요청 탭」 오버레이이고,
 * 승인·거부 후 그룹 상태는 바뀐 적이 없으므로 복귀가 자동이다. 「하위주문당 검토 중 1건」은 생성 컬럼
 * {@code pending_delivery_group_id} UNIQUE 가 DB 에서 보증한다 — 소비자가 연타해도 하나만 남는다.
 *
 * <p>승인·거부는 건별 단건만 — 일괄 API 를 만들지 않는다(§34-8 「건별 근거가 다른 판단을 묶으면 검토가 형식이 된다」).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "order_cancel_request")
public class OrderCancelRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cancel_request_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_group_id", nullable = false)
    private OrderDeliveryGroup deliveryGroup;

    /** 비정규화 — 소비자 앱 조회용. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 30)
    private CancelRequestReason reasonCode;

    /** ETC 만 자유 입력. */
    @Column(name = "reason_detail", length = 300)
    private String reasonDetail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CancelRequestStatus status;

    /** 요청 당시 이행 상태 — 「준비 시작 후 경과」 표기·분쟁 근거. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_at_request", nullable = false, length = 30)
    private FulfillmentStatus statusAtRequest;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    /** 거부 시 필수 — 소비자에게 그대로 전달(약관 제18조①). */
    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @OneToMany(mappedBy = "cancelRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderCancelRequestItem> items = new ArrayList<>();

    @Builder
    public OrderCancelRequest(OrderDeliveryGroup deliveryGroup, Order order, Long requestedBy,
                              CancelRequestReason reasonCode, String reasonDetail,
                              FulfillmentStatus statusAtRequest, LocalDateTime requestedAt) {
        this.deliveryGroup = deliveryGroup;
        this.order = order;
        this.requestedBy = requestedBy;
        this.reasonCode = reasonCode;
        this.reasonDetail = reasonDetail;
        this.status = CancelRequestStatus.PENDING;
        this.statusAtRequest = statusAtRequest;
        this.requestedAt = requestedAt;
    }

    public void addItem(OrderCancelRequestItem item) {
        items.add(item);
    }

    public int totalRefundAmount() {
        return items.stream().mapToInt(OrderCancelRequestItem::getRefundAmount).sum();
    }
}
