package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "이슈 스레드 개설 결과 — FE가 「스레드로 이동 ↗」에 쓴다")
public record GroupBuyIssueOpenResponse(
        @Schema(description = "개설된 이슈 id", example = "5") Long issueId,
        @Schema(description = "이슈 3자 스레드 id — 연결·소통 화면 이동에 쓴다", example = "120") Long threadId
) {
}
