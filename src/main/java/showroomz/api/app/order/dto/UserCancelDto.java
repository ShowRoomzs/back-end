package showroomz.api.app.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.domain.order.type.CancelRequestReason;

import java.time.LocalDateTime;
import java.util.List;

/** 소비자 취소 요청 · 취소 상세(C10 1b~1d · 1009 기획 수정본 3-1). */
public final class UserCancelDto {

    private UserCancelDto() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "취소 요청 — 한 하위주문(브랜드)의 결제된 항목 중 고른 것. 항목 전량이다")
    public static class CreateRequest {
        @NotNull
        @Schema(description = "하위주문(배송 그룹) — 주문 상세 items[].deliveryGroupId", example = "911")
        private Long deliveryGroupId;

        @NotEmpty
        @Schema(description = "취소할 항목 — 같은 하위주문의 결제된 항목")
        private List<Long> orderProductIds;

        @NotNull
        @Schema(description = "사유 — CHANGE_OF_MIND · ORDER_MISTAKE · PAYMENT_CHANGE · ETC", example = "CHANGE_OF_MIND")
        private CancelRequestReason reasonCode;

        @Size(max = 300)
        @Schema(description = "상세 사유 — ETC 면 필수 · 300자", example = "색상을 잘못 골랐어요", nullable = true)
        private String reasonDetail;
    }

    public enum Kind {
        /** 소비자 취소 요청 — 브랜드가 확인한다. */
        REQUEST,
        /** 준비 시작 전 소비자 직접 취소(주문 전체 · PG 즉시 환불). */
        CONSUMER_CANCEL,
        /** 브랜드 직권 취소(품절 · 하자 등). */
        SELLER_CANCEL
    }

    public enum Phase {
        /** 브랜드 확인 중 — 응답 기한까지 확인하지 않으면 자동 취소된다. */
        REVIEWING,
        /** 취소 완료 — 브랜드 승인 · 자동 승인 · 직접 취소 · 직권 취소. */
        CANCELLED,
        /** 브랜드가 거부했다 — 상품은 발송된다. */
        REJECTED
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "취소 상세 — 문구는 서버가 내린다")
    public static class DetailResponse {
        private Long orderId;
        private String orderNumber;
        @Schema(description = "취소 요청이면 그 id — 직접 · 직권 취소는 null", nullable = true)
        private Long cancelRequestId;
        private Kind kind;
        private Phase phase;
        @Schema(example = "확인 중")
        private String phaseLabel;
        @Schema(description = "자동 승인으로 취소됐다(브랜드가 응답 기한 안에 확인하지 않았다)")
        private boolean autoApproved;
        @Schema(description = "상단 안내 문구 — 없으면 빈 목록",
                example = "[\"브랜드가 1영업일(주말·공휴일 제외) 안에 확인하지 않으면 자동으로 취소돼요\"]")
        private List<String> notices;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime requestedAt;
        @Schema(description = "브랜드 응답 기한 — 확인 중일 때만", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime respondDueAt;
        @Schema(description = "취소 · 거부 확정 시각", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime decidedAt;
        @Schema(example = "단순 변심")
        private String reasonLabel;
        @Schema(nullable = true)
        private String reasonDetail;
        @Schema(description = "거부 사유 — 거부됐을 때만", nullable = true)
        private Rejection rejection;
        private List<Item> items;
        @Schema(description = "환불 — 거부 · 확인 중이면 null", nullable = true)
        private Refund refund;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Rejection {
        @Schema(example = "이미 포장·출고가 완료됨")
        private String reasonLabel;
        @Schema(nullable = true)
        private String detail;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long orderProductId;
        private String productName;
        private String optionName;
        private String thumbnailUrl;
        private Integer quantity;
        @Schema(description = "단가 × 수량", example = "24900")
        private Long amount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Refund {
        @Schema(description = "환불 금액 — 전 항목 취소면 배송비 포함", example = "27900")
        private Long amount;
        @Schema(description = "PROCESSING(환불 처리 중) · DONE(환불 완료)", example = "DONE")
        private String status;
        @Schema(example = "환불 완료")
        private String statusLabel;
        @Schema(description = "환불 수단 — 「신한카드 결제 취소」", nullable = true)
        private String methodLabel;
        @Schema(nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime refundedAt;
    }
}
