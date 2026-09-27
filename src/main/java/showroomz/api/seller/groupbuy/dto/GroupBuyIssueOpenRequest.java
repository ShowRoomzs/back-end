package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.groupbuy.type.GroupBuyIssueType;

/** 이슈 스레드 열기(C5). 내용이 스레드의 첫 글이 된다. */
@Schema(description = "이슈 스레드 열기 — 종료 후 정산과 무관한 이견. 공구 상태·정산에 영향 없음")
public record GroupBuyIssueOpenRequest(

        @Schema(description = "이슈 유형 — CONTENT_FULFILLMENT(콘텐츠 이행 문제) · TERMS_INTERPRETATION(계약 조건 해석 이견) · "
                + "SETTLEMENT_AMOUNT(정산 금액 이견) · ETC(기타)", example = "CONTENT_FULFILLMENT")
        @NotNull
        GroupBuyIssueType issueType,

        @Schema(description = "이슈 내용 — 필수 · 2,000자. 스레드의 첫 글이 된다", example = "계약상 스토리 3건인데 2건만 게시되었습니다.")
        @NotBlank @Size(max = 2000)
        String content
) {
}
