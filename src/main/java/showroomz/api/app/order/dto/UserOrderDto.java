package showroomz.api.app.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.order.type.UserOrderAction;
import showroomz.domain.order.type.UserOrderItemStatus;
import showroomz.domain.order.type.UserOrderTone;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C10 주문 내역 · C10-1 주문 상세의 항목 행 DTO(C10 설계서 1-5). 목록과 상세가 같은 행을 쓴다.
 * 라벨·색·보조 문구·버튼은 서버가 내린다(0-4). 시각은 기존 주문 API 와 같은 규약({@link OrderDto#TIME_PATTERN} · KST).
 */
public class UserOrderDto {

    public enum NoticeType {
        /** 구매확정 기한 안내(C10-1 1d). */
        CONFIRM_DUE,
        /** 준비중 취소는 요청으로 접수(C10-1 1b). */
        CANCEL_BY_REQUEST
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "항목 버튼 — 서버가 내린 것만 그린다")
    public static class Action {
        private UserOrderAction type;
        @Schema(example = "주문 취소")
        private String label;
        @Schema(description = "false 면 비활성으로 그린다 — label 이 사유를 담는다(「교환 불가 (재고 없음)」)")
        private Boolean enabled;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "취소 요청 반려 줄 — 「취소 요청 반려 · 사유 보기 ›」. 사유 본문은 취소 상세가 준다")
    public static class CancelRejection {
        private Long cancelRequestId;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime rejectedAt;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "반품·교환 클레임 — 반품·교환 모듈 배포 전에는 항상 null")
    public static class Claim {
        private Long claimId;
        @Schema(description = "RETURN | EXCHANGE", example = "RETURN")
        private String type;
        private String claimStatus;
        @Schema(description = "신청 수량 — 주문 수량과 다를 수 있다")
        private Integer quantity;
        @Schema(nullable = true)
        private String exchangeOptionName;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "반품·교환 반려 줄 — 반품·교환 모듈 배포 전에는 항상 null")
    public static class ClaimRejection {
        private Long claimId;
        @Schema(description = "RETURN | EXCHANGE", example = "RETURN")
        private String type;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime rejectedAt;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "원시 시각 — statusSub 와 다른 형식이 필요할 때. 전부 nullable")
    public static class Dates {
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime shipDueAt;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime shippedAt;
        @Schema(description = "도착 예정일 — 배송중 · 집화 후 · 예정일이 지나지 않았을 때만", example = "2026-09-17")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate arrivalDueDate;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime deliveredAt;
        @Schema(description = "구매확정 예정 — 배송완료 상태에서만")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime confirmDueAt;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime confirmedAt;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime cancelledAt;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "주문 항목 행 — 목록·상세 공용")
    public static class ItemRow {
        private Long orderProductId;
        private Long productId;
        private Long variantId;
        @Schema(description = "브랜드명 — 주문 시점 스냅샷", example = "라보에이치", nullable = true)
        private String brandName;
        private String productName;
        private String optionName;
        private Integer quantity;
        @Schema(description = "환불로 끝난 수량 — 0이면 그리지 않는다", example = "0")
        private Integer returnedQuantity;
        private String thumbnailUrl;

        private UserOrderItemStatus status;
        @Schema(example = "배송중")
        private String statusLabel;
        private UserOrderTone statusTone;
        @Schema(description = "상태 보조 문구 — 완성 문자열", example = "09.14 발송", nullable = true)
        private String statusSub;
        @Schema(description = "행 탈색 — 취소·반품으로 끝난 항목")
        private Boolean dimmed;

        @Schema(description = "항목 금액 — 취소면 환불 대상액", example = "24900")
        private Long amount;
        @Schema(example = "24,900원")
        private String amountLabel;

        @Schema(nullable = true)
        private CancelRejection cancelRejection;
        @Schema(description = "검토 중인 취소 요청 — status=CANCEL_REQUESTED 일 때", nullable = true)
        private Long cancelRequestId;
        @Schema(nullable = true)
        private Claim claim;
        @Schema(nullable = true)
        private ClaimRejection claimRejection;

        private Dates dates;
        private List<Action> actions;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "주문 내역 목록의 묶음 — 주문 1건 + 항목 행")
    public static class OrderCard {
        private Long orderId;
        @Schema(example = "20260912-000201")
        private String orderNumber;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime orderedAt;
        private List<ItemRow> items;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "마스킹된 배송지 — C10-1 은 이것만 그린다(스크린샷 공유 대비)")
    public static class MaskedAddress {
        @Schema(example = "김수*")
        private String recipientName;
        @Schema(example = "010-****-5678")
        private String phoneNumber;
        private String address;
        @Schema(description = "상세 주소 — 있으면 통째로 ******, 없으면 null", example = "******", nullable = true)
        private String detailAddress;
        @Schema(description = "배송 요청사항", nullable = true)
        private String memo;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "주문 상세 상단 안내 — 조건 판정은 서버, 문구는 앱")
    public static class Notice {
        private NoticeType type;
        private UserOrderTone tone;
        @Schema(description = "CONFIRM_DUE 의 기한 — 가장 이른 구매확정 예정", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime date;
    }
}
