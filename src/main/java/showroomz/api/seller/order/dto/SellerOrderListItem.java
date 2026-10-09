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
        @Schema(description = "하위주문 id — 상세·액션 API의 경로/바디 키", example = "1024") Long deliveryGroupId,
        @Schema(description = "주문번호 — 결제일(YYYYMMDD)-순번 6자리", example = "20261003-000123") String orderNumber,
        @Schema(description = "하위주문번호 — 주문번호-브랜드 순번 2자리", example = "20261003-000123-01") String subOrderNumber,
        @Schema(description = "공구명(계약명) — 공구 없는 백필 주문은 null", example = "글로우 크림 앵콜 공구", nullable = true)
        String groupBuyName,
        @Schema(description = "소비자명 — 전체 표기(rev.6). 발주서·송장에 실명을 그대로 쓴다", example = "김민지") String recipientName,
        @Schema(description = "「상품명 외 N건」 — 항목 1개면 상품명만", example = "글로우 크림 50ml 외 1건") String productSummary,
        @Schema(description = "총 수량 — 취소 항목 포함", example = "3") int totalQuantity,
        @Schema(description = "이행 상태 — NEW · PREPARING · SHIPPING · RETURNING · DELIVERED · CONFIRMED · CANCELLED", example = "PREPARING")
        FulfillmentStatus status,
        @Schema(description = "상태 배지 문구", example = "상품준비중") String statusLabel,
        @Schema(description = "상태 배지 색 — NEUTRAL · INFO · WARNING · SUCCESS · DANGER", example = "INFO") OrderBadgeTone statusTone,
        Overlays overlays,
        @Schema(description = "주문일시 = 결제완료 시각", example = "2026-10-01T14:22:05Z") LocalDateTime orderedAt,
        @Schema(description = "발송기한 = 공구 마감 + N영업일. 발송된 건은 FE 가 「—」로 그린다. **공구 진행 중이면 null**(마감 전에는 "
                + "기한이 없다 — FE 는 「마감 후 N영업일」로 그린다)", example = "2026-10-04T23:59:59Z", nullable = true)
        LocalDateTime shipDueAt,
        @Schema(description = "이 주문에 적용된 발송 기한 N(영업일) — 주문 시점 값. 브랜드가 설정을 바꿔도 그대로다", example = "3")
        int shipDueBusinessDays,
        @Schema(description = "택배사 — 송장 등록 전이면 null", example = "CJ", nullable = true) DeliveryCarrier carrier,
        @Schema(description = "택배사 표시명", example = "CJ대한통운", nullable = true) String carrierLabel,
        @Schema(description = "송장번호 — 송장 등록 전이면 null", example = "640012345678", nullable = true) String trackingNumber,
        @Schema(description = "발송 처리(송장 등록 확정) 시각 — 송장 수정으로 바뀌지 않는다. 추적 기록이 아직 없는 「집화 확인 필요」 행은 "
                + "`lastTrackingAt`이 null 이라 이 값을 최종 갱신 칸에 쓴다", example = "2026-10-03T11:20:00Z", nullable = true)
        LocalDateTime shippedAt,
        @Schema(description = "배송중 열은 최종 위치 대신 최종 갱신 — 갱신이 멈춘 것이 문제 신호다",
                example = "2026-10-03T09:41:00Z", nullable = true) LocalDateTime lastTrackingAt,
        @Schema(description = "배송완료 시각", example = "2026-10-05T16:10:00Z", nullable = true) LocalDateTime deliveredAt,
        @Schema(description = "자동 확인 / 운영자 처리 — 항상 병기(§34-7)", example = "자동 확인", nullable = true)
        String deliveredSourceLabel,
        @Schema(description = "구매확정 예정 D-N — 배송완료 탭의 주인공. `DELIVERED`가 아니면 null", example = "5", nullable = true)
        Integer confirmRemainingDays,
        @Schema(description = "구매확정 시각", example = "2026-10-12T16:10:00Z", nullable = true) LocalDateTime confirmedAt,
        @Schema(description = "그룹 몫 결제금액(판매가 합 + 배송비) — 구매확정 탭만 쓴다", example = "84400") Integer paidAmount,
        @Schema(description = "정산 — 정산 모듈 전이라 null. 0이 아니다(설계서 0-6)", nullable = true) String settlementLabel,
        @Schema(description = "취소 시각 — 하위주문 전체가 취소됐을 때만", example = "2026-10-02T10:05:00Z", nullable = true)
        LocalDateTime cancelledAt,
        @Schema(description = "취소 탭 「취소 사유」 열 — 소비자 취소/승인/직권", example = "브랜드 직권 취소", nullable = true)
        String cancelTypeLabel,
        @Schema(description = "검토 중 취소 요청 — 없으면 null", nullable = true) CancelRequestSummary cancelRequest,
        @Schema(description = "행 확장(▸) 주문 항목 미리보기") List<Item> items
) {

    /** 오버레이 — 이행 상태와 별 축. 한 행에 발송기한 경과와 배송 이상이 둘 다 뜰 수 있다(§34-6). */
    @Schema(name = "SellerOrderOverlays")
    public record Overlays(
            @Schema(description = "검토 중 취소 요청 있음 — 취소 요청 탭으로 간다", example = "false") boolean cancelRequested,
            @Schema(description = "배송 이상 — PICKUP_UNCONFIRMED 집화 확인 필요 · STALLED 추적 정지. 없으면 null",
                    example = "PICKUP_UNCONFIRMED", nullable = true) TrackingAlert trackingAlert,
            @Schema(description = "배송 이상 배지 문구", example = "집화 확인 필요", nullable = true) String trackingAlertLabel,
            @Schema(description = "발송기한 경과 — 브랜드 귀책(위험). 신규·상품준비중에서만 판정", example = "false") boolean shipOverdue,
            @Schema(description = "진행 중인 반품·교환 건수(거절 보류 포함) — 배송완료 탭에서 구매확정 D-N 이 왜 비었는지 설명한다. "
                    + "D-N 이 비는 것은 거절되지 않은 진행 중 건이 있을 때뿐이다", example = "0") int openClaimCount
    ) {
    }

    @Schema(name = "SellerOrderCancelRequestSummary")
    public record CancelRequestSummary(
            @Schema(description = "취소 요청 id — 승인·거부 API 경로 키", example = "77") Long cancelRequestId,
            @Schema(description = "소비자가 고른 사유", example = "단순 변심") String reasonLabel,
            @Schema(description = "소비자 상세 사유 — 미입력이면 null", example = "색상을 잘못 골랐어요", nullable = true) String reasonDetail,
            @Schema(description = "요청 시각", example = "2026-10-02T08:30:00Z") LocalDateTime requestedAt,
            @Schema(description = "경과 — 요청 후 경과 시간(시간 단위 내림)", example = "5") long elapsedHours,
            @Schema(description = "응답 기한 열 — 요청 + 1영업일의 끝. 지나면 자동 승인되고 PG 가 즉시 환불한다. 남은 시간은 FE 가 계산한다",
                    example = "2026-10-05T23:59:59Z") LocalDateTime respondDueAt,
            @Schema(description = "「3건 중 1건 요청 · 남은 2건 발송 대기」", example = "2건 중 1건 요청 · 남은 1건 발송 대기") String summary
    ) {
    }

    @Schema(name = "SellerOrderItem")
    public record Item(
            @Schema(description = "주문 항목 id", example = "5012") Long orderProductId,
            @Schema(description = "상품명", example = "글로우 크림 50ml") String productName,
            @Schema(description = "옵션명 — 단일 상품이면 null", example = "2개 세트", nullable = true) String optionName,
            @Schema(description = "수량", example = "2") int quantity,
            @Schema(description = "공구가(판매 단가)", example = "27200") int price,
            @Schema(description = "금액 = 공구가 × 수량", example = "54400") int amount,
            @Schema(description = "항목 상태 문구 — 취소 / 구매확정 / 그 외는 하위주문 상태 문구", example = "상품준비중") String itemStatusLabel,
            @Schema(description = "취소된 항목", example = "false") boolean cancelled,
            @Schema(description = "검토 중 취소 요청의 대상 항목 — C11 경고 톤", example = "false") boolean cancelRequested
    ) {
    }
}
