package showroomz.api.creator.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.groupbuy.type.FulfillmentResult;

/** 계약 이행 확인(C5 · C6) — 확인 대상은 <b>브랜드의 의무</b>(주문 배송 · 고정 지급비 지급)다. 불가역. */
@Schema(description = "계약 이행 확인")
public record CreatorFulfillmentCheckRequest(

        @Schema(example = "UNFULFILLED") @NotNull
        FulfillmentResult result,

        @Schema(description = "미이행 사유 — UNFULFILLED면 필수 · 2,000자 · 3자 스레드의 첫 글", nullable = true)
        @Size(max = 2000)
        String reason
) {
}
