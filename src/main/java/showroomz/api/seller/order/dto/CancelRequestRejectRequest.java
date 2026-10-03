package showroomz.api.seller.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 취소 요청 거부(E7) — 사유 필수 · 소비자에게 그대로 전달(약관 제18조①) · 전 항목 배송 진행. */
public record CancelRequestRejectRequest(
        @Schema(description = "소비자에게 그대로 전달되는 거부 사유")
        @NotBlank @Size(max = 500) String reason
) {
}
