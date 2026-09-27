package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.groupbuy.type.FulfillmentResult;

/** 계약 이행 확인(C6 · C7) — 확인 대상은 인플루언서의 콘텐츠 의무다. 불가역. */
@Schema(description = "계약 이행 확인 — 인플루언서의 콘텐츠 의무 이행 여부. 1회 · 불가역")
public record GroupBuyFulfillmentCheckRequest(

        @Schema(description = "FULFILLED(이행) · UNFULFILLED(미이행 — reason 필수 · 현재 503)", example = "FULFILLED")
        @NotNull
        FulfillmentResult result,

        @Schema(description = "미이행 사유 — UNFULFILLED면 필수 · 2,000자. 운영자가 참여하는 3자 스레드의 첫 글이 된다. FULFILLED면 무시",
                example = "계약상 스토리 3건인데 2건만 게시되었습니다.", nullable = true)
        @Size(max = 2000)
        String reason
) {
}
