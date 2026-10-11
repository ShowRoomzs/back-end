package showroomz.api.seller.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import showroomz.domain.order.type.CancelRejectReason;

/**
 * 취소 요청 거부(E7 · 1009 기획 수정본 3-3) — 사유는 드롭다운(이미 포장·출고 완료 / 택배사 집화 완료 / 주문 제작·맞춤 상품 / 기타)이고
 * 기타면 상세가 필수다. 라벨과 상세가 소비자에게 그대로 전달된다(약관 제18조①). 환불은 일어나지 않는다.
 */
public record CancelRequestRejectRequest(
        @Schema(description = "거부 사유 — ALREADY_PACKED · PICKED_UP · MADE_TO_ORDER · ETC", example = "ALREADY_PACKED")
        CancelRejectReason reasonCode,
        @Schema(description = "상세 사유 — ETC 면 필수 · 그 밖은 선택 · 500자", example = "오늘 오전에 출고 작업이 끝났습니다.",
                nullable = true)
        @Size(max = 500) String detail,
        @Schema(description = "**폐지 예정** — 구 FE 호환. 사유 코드 없이 이 값만 오면 ETC + 상세로 받는다", nullable = true,
                deprecated = true)
        @Size(max = 500) String reason
) {
    public CancelRejectReason resolvedReasonCode() {
        if (reasonCode != null) {
            return reasonCode;
        }
        return reason != null && !reason.isBlank() ? CancelRejectReason.ETC : null;
    }

    public String resolvedDetail() {
        return detail != null && !detail.isBlank() ? detail : reason;
    }
}
