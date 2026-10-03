package showroomz.api.seller.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderBadgeTone;
import showroomz.domain.order.type.TrackingAlert;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 목록 한 행 — 탭 공통 superset(34 설계서 4-1). 탭별 컬럼 차이는 FE 가 고른다.
 * 연락처·주소·배송 요청사항은 싣지 않는다(§34-11) — 상세·발주서에만.
 */
public record SellerOrderListItem(
        Long deliveryGroupId,
        String orderNumber,
        String subOrderNumber,
        String groupBuyName,
        @Schema(description = "소비자명 — 전체 표기(rev.6). 발주서·송장에 실명을 그대로 쓴다") String recipientName,
        @Schema(description = "「상품명 외 N건」") String productSummary,
        int totalQuantity,
        FulfillmentStatus status,
        String statusLabel,
        OrderBadgeTone statusTone,
        Overlays overlays,
        @Schema(description = "주문일시 = 결제완료 시각") LocalDateTime orderedAt,
        @Schema(description = "발송기한 — 발송된 건은 FE 가 「—」로 그린다") LocalDateTime shipDueAt,
        DeliveryCarrier carrier,
        String carrierLabel,
        String trackingNumber,
        @Schema(description = "배송중 열은 최종 위치 대신 최종 갱신 — 갱신이 멈춘 것이 문제 신호다") LocalDateTime lastTrackingAt,
        LocalDateTime deliveredAt,
        @Schema(description = "자동 확인 / 운영자 처리 — 항상 병기(§34-7)") String deliveredSourceLabel,
        @Schema(description = "구매확정 예정 D-N — 배송완료 탭의 주인공") Integer confirmRemainingDays,
        LocalDateTime confirmedAt,
        @Schema(description = "그룹 몫 결제금액(판매가 합 + 배송비) — 구매확정 탭만 쓴다") Integer paidAmount,
        @Schema(description = "정산 — 정산 모듈 전이라 null. 0이 아니다(설계서 0-6)") String settlementLabel,
        LocalDateTime cancelledAt,
        @Schema(description = "취소 탭 「취소 사유」 열 — 소비자 취소/승인/직권") String cancelTypeLabel,
        CancelRequestSummary cancelRequest,
        @Schema(description = "행 확장(▸) 주문 항목 미리보기") List<Item> items
) {

    /** 오버레이 — 이행 상태와 별 축. 한 행에 발송기한 경과와 배송 이상이 둘 다 뜰 수 있다(§34-6). */
    public record Overlays(
            boolean cancelRequested,
            TrackingAlert trackingAlert,
            String trackingAlertLabel,
            @Schema(description = "발송기한 경과 — 브랜드 귀책(위험)") boolean shipOverdue
    ) {
    }

    public record CancelRequestSummary(
            Long cancelRequestId,
            String reasonLabel,
            String reasonDetail,
            LocalDateTime requestedAt,
            @Schema(description = "경과 열") long elapsedHours,
            @Schema(description = "「3건 중 1건 요청 · 남은 2건 발송 대기」") String summary
    ) {
    }

    public record Item(
            Long orderProductId,
            String productName,
            String optionName,
            int quantity,
            @Schema(description = "공구가(판매 단가)") int price,
            int amount,
            String itemStatusLabel,
            boolean cancelled,
            @Schema(description = "검토 중 취소 요청의 대상 항목 — C11 경고 톤") boolean cancelRequested
    ) {
    }
}
