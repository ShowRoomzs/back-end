package showroomz.api.app.post.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.post.type.PostReportReason;

/**
 * 신고 사유 한 줄 — 신고 시트의 선택지다.
 *
 * <p>목록을 서버가 내려주는 이유 — 사유는 운영정책과 함께 움직이는 값이라 앱 배포 주기에 묶이면
 * 규정이 바뀌어도 화면이 따라가지 못한다. 어드민 조치 화면이 사유 목록을 서버에서 받는 것
 * ({@code GET /v1/admin/posts/suspension-reasons})과 같은 이유이고, 두 목록은 같은 코드 축을 쓴다.
 *
 * @param code           서버로 그대로 돌려보낼 코드
 * @param label          시트에 그리는 문구
 * @param detailRequired true면 상세 사유 입력란을 필수로 연다(기타)
 */
@Schema(description = "신고 사유 항목")
public record PostReportReasonItem(

        @Schema(description = "사유 코드", example = "AD_DISCLOSURE")
        PostReportReason code,

        @Schema(description = "표시 문구", example = "광고인데 표시가 없어요")
        String label,

        @Schema(description = "상세 사유 필수 여부 — true면 입력란을 연다", example = "false")
        boolean detailRequired) {
}
