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
        @Schema(description = "하위주문 id 목록 — 1~200건", example = "[1024]")
        @NotEmpty @Size(max = 200) List<Long> deliveryGroupIds,
        @Schema(description = "취소 사유 — `SOLD_OUT` 품절 · `DEFECT` 상품 하자 · `UNDELIVERABLE_AREA` 배송 불가 지역 · `ETC` 기타",
                example = "SOLD_OUT")
        @NotNull SellerCancelReason reasonCode,
        @Schema(description = "소비자 설명 — 필수 · 300자. 사유와 함께 소비자에게 그대로 전달된다(약관 제18조②)",
                example = "준비 중 재고 오차로 2개 세트 옵션이 품절되었습니다. 결제하신 금액은 전액 환불됩니다.")
        @NotBlank @Size(max = 300) String consumerMessage
) {
}
