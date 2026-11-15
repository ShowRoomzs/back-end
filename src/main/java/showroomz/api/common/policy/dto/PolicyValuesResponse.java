package showroomz.api.common.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 여러 앱이 문구에 쓰는 정책 기한(영업일 · 일) — 설정값 그대로(58 계획 6-1 · D4). 정해지지 않은 값은 null(FE 「[정책값 미정]」).
 */
@Schema(description = "정책 기한 값 — 설정이 바뀌면 같이 바뀐다")
public record PolicyValuesResponse(
        @Schema(description = "반품 검수 기한 — 반품을 돌려받은 날(회수 배송완료) + N영업일. 기한 안에 판정이 없으면 자동 승인"
                + "(order.claim.inspect-due-business-days · 2026-11-10 · 자동 입고 폐지)", example = "2")
        int claimInspectDueBusinessDays,
        @Schema(description = "검수 승인 후 PG 환불 기한 — N영업일(order.claim.refund-due-business-days)", example = "1")
        int claimRefundDueBusinessDays,
        @Schema(description = "추적 정지 판정 — 마지막 추적 이벤트 후 N일 갱신 없음(delivery.tracker.stall-alert-days)", example = "7")
        int trackingStallAlertDays,
        @Schema(description = "소비자 선택(재발송 · 환불) 개방 — 추적 정지 판정 + N일(delivery.tracker.consumer-choice-days). "
                + "마지막 추적 이벤트 기준으로는 trackingStallAlertDays + 이 값", example = "7")
        int stallConsumerChoiceDays,
        @Schema(description = "정산 확인 기간 — 생성 다음 영업일부터 N영업일(settlement.review-business-days)", example = "3")
        int settlementReviewBusinessDays,
        @Schema(description = "지급 예정일 — 확정일 + N영업일(settlement.payout-business-days)", example = "3")
        int settlementPayoutBusinessDays,
        @Schema(description = "정산 조정 합의 기한 — 개설 + N영업일(settlement.adjustment.deadline-business-days)", example = "10")
        int adjustmentDeadlineBusinessDays,
        @Schema(description = "고정비 입금 기한 N영업일 — 정책값 미정이면 null", nullable = true)
        Integer fixedFeePaymentBusinessDays,
        @Schema(description = "계정 이용 정지 이의 신청 기한 — 통지 + N영업일(member.sanction.appeal-business-days)", example = "3")
        int sanctionAppealBusinessDays,
        @Schema(description = "계정 이용 정지 소명 검토 N영업일 — 정책값 미정이면 null(member.sanction.appeal-review-business-days)",
                nullable = true)
        Integer sanctionAppealReviewBusinessDays,
        @Schema(description = "취소 요청 응답 기한 — 요청 + N영업일. 자동 승인 없음 — 넘기면 운영팀이 브랜드 확인 후 취소(order.cancel-request-respond-business-days)",
                example = "2")
        int cancelRequestRespondBusinessDays,
        @Schema(description = "인플루언서 정산 계좌 확인 N영업일 — 정책값 미정이면 null", nullable = true)
        Integer creatorAccountReviewBusinessDays,
        @Schema(description = "공구 노출 중지 소명 기한 — 통지 + N영업일(groupbuy.suspension.appeal-business-days)", example = "3")
        int groupBuySuspensionAppealBusinessDays) {
}
