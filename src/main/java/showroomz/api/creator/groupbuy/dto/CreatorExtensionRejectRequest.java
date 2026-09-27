package showroomz.api.creator.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import showroomz.domain.groupbuy.type.ExtensionRejectReason;

/** 기간 연장 거절(C2) — 사유는 선택이다. 기타(ETC)면 메모 필수. 메모는 <b>브랜드에게</b> 보인다. */
@Schema(description = "기간 연장 거절")
public record CreatorExtensionRejectRequest(

        @Schema(description = "거절 사유 — 선택. NEXT_SCHEDULE_BOOKED(다음 일정이 잡혀 있음) · CONTENT_PLAN_MISMATCH(콘텐츠 계획과 맞지 않음) · "
                + "TERMS_RENEGOTIATION(조건 재협의 필요) · ETC(기타 — memo 필수)",
                example = "NEXT_SCHEDULE_BOOKED", nullable = true)
        ExtensionRejectReason reasonCode,

        @Schema(description = "브랜드에게 남길 메모 — ETC면 필수 · 1,000자 · 공백만이면 빈 값. 브랜드 상세에 그대로 보인다",
                example = "8월 넷째 주에 다른 공구가 잡혀 있어요.",
                nullable = true)
        @Size(max = 1000)
        String memo
) {
}
