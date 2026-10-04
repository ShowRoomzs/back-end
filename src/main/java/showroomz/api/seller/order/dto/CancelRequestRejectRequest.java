package showroomz.api.seller.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 취소 요청 거부(E7) — 사유 필수 · 소비자에게 그대로 전달(약관 제18조①) · 전 항목 배송 진행. */
public record CancelRequestRejectRequest(
        @Schema(description = "소비자에게 그대로 전달되는 거부 사유 — 필수 · 500자",
                example = "이미 출고 작업이 끝나 취소가 어렵습니다. 수령 후 반품으로 접수해 주세요.")
        @NotBlank @Size(max = 500) String reason
) {
}
