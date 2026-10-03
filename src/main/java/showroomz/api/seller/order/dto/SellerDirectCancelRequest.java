package showroomz.api.seller.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.order.type.SellerCancelReason;

import java.util.List;

/** 직권 취소(E5 · §34-8) — 되돌릴 수 없음 · 환불은 운영자 · 취소율 반영. */
public record SellerDirectCancelRequest(
        @NotEmpty @Size(max = 200) List<Long> deliveryGroupIds,
        @NotNull SellerCancelReason reasonCode,
        @Schema(description = "소비자 설명 — 사유와 함께 소비자에게 그대로 전달된다(약관 제18조②)")
        @NotBlank @Size(max = 300) String consumerMessage
) {
}
