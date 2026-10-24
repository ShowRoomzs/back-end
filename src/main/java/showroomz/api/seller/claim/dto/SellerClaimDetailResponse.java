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
        @Schema(description = "[호환 · 1회 릴리스 뒤 제거] `reshipFeeCharged` 와 같은 값 — 1021 결정 노트 기-18 로 「교환비」가 없어졌다",
                nullable = true, deprecated = true)
        Boolean exchangeFeeCharged,
        @Schema(description = "환불 예정 — 반품만", nullable = true) Refund refund,
        @Schema(description = "소비자 첨부 사진") List<String> consumerAttachments,
        @Schema(description = "브랜드 반려 증빙") List<String> sellerEvidences,
        @Schema(description = "반려 상세 설명 — 브랜드 · 어드민 기록용(소비자에게는 consumerMessage 가 간다)", nullable = true) String rejectDetail,
        @Schema(description = "검수 반려 6항목 — 반려됐을 때만(소비자 C10-5와 같은 값)", nullable = true) Rejection rejection,
        @Schema(description = "구매확정 타이머 — 「정지 · 남은 4일」. 반품·교환 요청 시점부터 정지 · 철회되면 남은 일수부터 재개 · "
                + "교환 완료 시 7일 새로 시작. 배송완료 전이면 null", nullable = true) PurchaseConfirm purchaseConfirm,
        @Schema(description = "처리 결과 — 완료 탭 단계만", nullable = true) Result result,
        @Schema(description = "미결제 고지 회차 — 반려 보류만") List<Notice> notices,
        @Schema(description = "처리 이력 — 최신순") List<HistoryItem> history,
        Actions actions,
        @Schema(description = "교환 재발송 배송비 청구 여부 — 교환만. 고객 귀책이면 요청 때 결제했다(1021 기-18 · 구 `exchangeFeeCharged`)",
                nullable = true) Boolean reshipFeeCharged,
        @Schema(description = "검수 판정 · 운임 정산 — 판정이 기록됐거나 운임 정산 행이 있을 때만(1021 설계서 4-3 · 4-4)", nullable = true)
        Inspection inspection
) {

    @Schema(name = "SellerClaimInspection")
    public record Inspection(
            @Schema(description = "PASSED · REASON_OVERTURNED · REJECTED", example = "REASON_OVERTURNED", nullable = true)
            showroomz.domain.order.type.ClaimInspectionResult result,
            @Schema(example = "사유 번복", nullable = true) String resultLabel,
            @Schema(description = "사유 번복 설명 — 소비자에게 그대로 보인다", nullable = true) String explanation,
            @Schema(description = "자동 입고 — 택배 도착 후 N영업일 동안 입고 확인이 없어 시스템이 입고 처리했다") boolean autoReceived,
            @Schema(description = "사유 번복 결제 금액(재발송 배송비 + 착불 운임) — 교환 사유 번복만", nullable = true, example = "6000")
            Integer overturnFee,
            @Schema(description = "운임 정산 — 브랜드 정산 「± 운임 정산」과 같은 행") List<FreightRow> freight
    ) {
    }

    @Schema(name = "SellerClaimFreightRow")
    public record FreightRow(
            showroomz.domain.order.type.FreightAdjustmentTrigger trigger,
            @Schema(example = "사유 번복") String triggerLabel,
            @Schema(description = "CHARGE_CONSUMER(소비자 부담 · 정산 +) · REFUND_CONSUMER(소비자 환급 · 정산 −)", example = "CHARGE_CONSUMER")
            showroomz.domain.order.type.FreightAdjustmentDirection direction,
            @Schema(example = "3000") int collectFreight,
            @Schema(example = "0") int prepaidFreight,
            @Schema(description = "차감했던 최초 배송비 환원 — 표시용(정산 금액에는 들어가지 않는다)", example = "0") int initialFeeRestored,
            @Schema(description = "정산 반영 금액(부호 있음)", example = "3000") long signedAmount,
            @Schema(description = "REFUND_DEDUCTION · CHARGE · REFUND_ADDITION · BANK_TRANSFER", example = "REFUND_DEDUCTION")
            showroomz.domain.order.type.FreightSettledVia settledVia
    ) {
    }

    @Schema(name = "SellerClaimRejection")
    public record Rejection(
            @Schema(description = "법적 근거 코드", example = "ART17_2_2", nullable = true)
            showroomz.domain.order.type.ClaimRejectLegalBasis legalBasis,
            @Schema(description = "법적 근거 문구", example = "전자상거래법 제17조②2호 · 사용·소비로 가치 현저히 감소", nullable = true)
            String legalBasisLabel,
            @Schema(description = "소비자에게 보낸 메시지", nullable = true) String consumerMessage,
            @Schema(description = "브랜드 귀책으로 인정했다 — 반품 배송비 차감 환원 · 반려 재발송비 브랜드 부담", example = "false")
            boolean faultChangedToSeller,
            @Schema(description = "일부 반려로 갈라져 나온 행이면 원래 접수번호 — 원래 행은 통과 수량만 남았다", example = "CLM-3021",
                    nullable = true) String splitFromClaimNumber
    ) {
    }

    @Schema(name = "SellerClaimPurchaseConfirm")
    public record PurchaseConfirm(
            @Schema(description = "정지 중 — 진행 중 반품·교환이 있다", example = "true") boolean paused,
            @Schema(description = "남은 일수(올림) — 정지 중이면 정지 시점 기준으로 멈춘 값", example = "4") Long remainingDays,
            @Schema(description = "확정 예정 시각 — 정지 중이면 null(재개 때 다시 정해진다)", nullable = true) LocalDateTime dueAt
    ) {
    }

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
            @Schema(description = "반려 종결의 방식 — RETURNED 반송 완료 · DISPOSED 보관 기간 만료 후 폐기", example = "RETURNED",
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
            boolean canUpdateReshipment,
            @Schema(description = "[사유 번복] 가능 — 입고 확인 뒤 · 착불(브랜드 귀책)로 신청한 건(1021 설계서 4-4)") boolean canOverturn
    ) {
    }
}
