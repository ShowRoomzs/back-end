package showroomz.api.seller.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 반품·교환 상세 모달(35 설계서 4-3). 목록 행과 같은 값은 {@code summary}에 있고, 여기는 상세에서만 보이는 것을 더한다.
 * 단계마다 없는 블록은 null 이다. 재발송 수취 정보(원문)는 싣지 않는다 — 반출은 로그가 남는 엑셀 경로 하나로 모은다.
 */
public record SellerClaimDetailResponse(
        SellerClaimListItem summary,
        @Schema(description = "주문번호", example = "20261003-000123") String orderNumber,
        @Schema(description = "하위주문 id — 주문 상세로 가는 키", example = "1024") Long deliveryGroupId,
        @Schema(description = "소비자 연락처 — 마스킹", example = "010-****-4412", nullable = true) String consumerPhone,
        @Schema(description = "소비자가 적은 상세 내용 — 브랜드 부담 사유는 필수", nullable = true) String reasonDetail,
        @Schema(description = "교환 재발송비 청구 여부 — 교환만. 고객 귀책이면 요청 때 결제했다", nullable = true)
        Boolean exchangeFeeCharged,
        @Schema(description = "환불 예정 — 반품만", nullable = true) Refund refund,
        @Schema(description = "소비자 첨부 사진") List<String> consumerAttachments,
        @Schema(description = "브랜드 거절 증빙") List<String> sellerEvidences,
        @Schema(description = "거절 상세 설명 — 소비자에게 그대로 전달된다", nullable = true) String rejectDetail,
        @Schema(description = "처리 결과 — 완료 탭 단계만", nullable = true) Result result,
        @Schema(description = "미결제 고지 회차 — 거절 보류만") List<Notice> notices,
        @Schema(description = "처리 이력 — 최신순") List<HistoryItem> history,
        Actions actions
) {

    /** 환불 예정액은 요청(박스) 단위다 — 배송비 차감이 요청당 한 번이라 항목마다 반복해 보이면 안 된다. */
    @Schema(name = "SellerClaimRefund")
    public record Refund(
            @Schema(description = "이 항목의 상품 금액(단가 × 신청 수량)", example = "27200") long itemAmount,
            @Schema(description = "요청의 반품 배송비 차감액 — 요청당 한 번", example = "3000") int requestDeduction,
            @Schema(description = "요청의 환불 예정액 — 판정이 다 끝났으면 확정액", example = "24200") long requestExpectedAmount,
            @Schema(description = "산정 근거 문구", example = "상품 금액 − 최초 배송비") String basisLabel
    ) {
    }

    @Schema(name = "SellerClaimResult")
    public record Result(
            @Schema(description = "재발송 도착 시각 — 교환 완료 · 반송 완료", nullable = true) LocalDateTime reshipDeliveredAt,
            @Schema(description = "구매확정 재시작 예정 — 교환 완료만", nullable = true) LocalDateTime confirmDueAt,
            @Schema(description = "거절 종결의 방식 — RETURNED 반송 완료 · DISPOSED 보관 기간 만료 후 폐기", example = "RETURNED",
                    nullable = true) String rejectionEnd,
            @Schema(description = "폐기 기록 시각", nullable = true) LocalDateTime disposedAt
    ) {
    }

    @Schema(name = "SellerClaimNotice")
    public record Notice(
            @Schema(description = "회차", example = "1") int seq,
            LocalDateTime notifiedAt,
            @Schema(description = "고지 수단", nullable = true) String channel
    ) {
    }

    @Schema(name = "SellerClaimHistoryItem")
    public record HistoryItem(
            @Schema(example = "RECEIVED") String eventType,
            @Schema(example = "입고 확인") String eventLabel,
            @Schema(example = "SELLER") String actorType,
            @Schema(example = "브랜드") String actorLabel,
            @Schema(nullable = true) String detail,
            LocalDateTime occurredAt
    ) {
    }

    /** 버튼 노출의 정본은 서버다. */
    @Schema(name = "SellerClaimDetailActions")
    public record Actions(
            boolean canConfirmReceipt,
            boolean canPass,
            boolean canReject,
            boolean canRegisterReshipment,
            boolean canUpdateReshipment
    ) {
    }
}
