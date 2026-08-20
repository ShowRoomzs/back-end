package showroomz.api.app.post.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.domain.post.entity.PostReport;
import showroomz.domain.post.type.PostReportReason;

/**
 * 게시물 신고 요청 (C4 게시물 헤더 ⋯ 시트).
 *
 * <p>사유 코드는 {@code GET /v1/user/posts/report-reasons}가 내려준 목록에서 고른 값이다. 화면이
 * 문구를 직접 들고 있으면 서버가 사유를 늘릴 때마다 앱을 다시 배포해야 한다.
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "게시물 신고 요청")
public class PostReportRequest {

    @Schema(description = "신고 사유 코드", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "AD_DISCLOSURE")
    @NotNull(message = "신고 사유는 필수입니다.")
    private PostReportReason reasonCode;

    @Schema(description = "상세 사유 — 기타(OTHER)를 고르면 필수", maxLength = PostReport.MAX_DETAIL_LENGTH,
            example = "광고 표시 없이 협찬 상품을 소개하고 있어요", nullable = true)
    @Size(max = PostReport.MAX_DETAIL_LENGTH, message = "상세 사유는 최대 500자까지 입력할 수 있습니다.")
    private String reasonDetail;
}
