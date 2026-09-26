package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.groupbuy.type.GroupBuyIssueType;

/** 이슈 스레드 열기(C5). 내용이 스레드의 첫 글이 된다. */
@Schema(description = "이슈 스레드 열기")
public record GroupBuyIssueOpenRequest(

        @Schema(example = "CONTENT_FULFILLMENT") @NotNull
        GroupBuyIssueType issueType,

        @Schema(description = "필수 · 2,000자", example = "계약상 스토리 3건인데 2건만 게시되었습니다.")
        @NotBlank @Size(max = 2000)
        String content
) {
}
