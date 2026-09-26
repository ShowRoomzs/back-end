package showroomz.api.creator.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import showroomz.domain.groupbuy.type.ExtensionRejectReason;

/** 기간 연장 거절(C2) — 사유는 선택이다. 기타(ETC)면 메모 필수. 메모는 <b>브랜드에게</b> 보인다. */
@Schema(description = "기간 연장 거절")
public record CreatorExtensionRejectRequest(

        @Schema(description = "NEXT_SCHEDULE_BOOKED · CONTENT_PLAN_MISMATCH · TERMS_RENEGOTIATION · ETC — 선택",
                example = "NEXT_SCHEDULE_BOOKED", nullable = true)
        ExtensionRejectReason reasonCode,

        @Schema(description = "브랜드에게 남길 메모 — ETC면 필수 · 1,000자", example = "9월 첫 주에 다른 공구가 잡혀 있어요.",
                nullable = true)
        @Size(max = 1000)
        String memo
) {
}
