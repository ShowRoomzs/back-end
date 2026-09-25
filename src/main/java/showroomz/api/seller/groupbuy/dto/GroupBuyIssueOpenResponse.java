package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "이슈 스레드 개설 결과 — FE가 「스레드로 이동 ↗」에 쓴다")
public record GroupBuyIssueOpenResponse(Long issueId, Long threadId) {
}
