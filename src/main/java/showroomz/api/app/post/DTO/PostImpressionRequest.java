package showroomz.api.app.post.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 게시물 노출 적재 요청 (§24-7).
 *
 * <p><b>배열로 묶어 보낸다.</b> 카드 한 장이 뷰포트에 들어올 때마다 요청을 날리면 피드 스크롤에서
 * 요청이 폭발한다. 클라이언트는 일정 간격으로 모아 한 번에 보낸다.
 *
 * <p>같은 사람이 같은 게시물을 다시 봐도 30분 안이면 서버가 적재하지 않는다 — 쇼룸 방문(§22-4)과
 * 같은 세션 규칙이라 두 화면의 지표 정의가 어긋나지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "게시물 노출 적재 요청 — 뷰포트에 들어온 게시물들을 모아 보낸다")
public class PostImpressionRequest {

    @Schema(description = "노출된 게시물 ID 목록", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "[301, 302, 305]")
    @NotEmpty(message = "노출된 게시물 ID가 필요합니다.")
    private List<Long> postIds;

    @Schema(description = "비로그인 조회자의 디바이스 식별자 — **비로그인일 때 필수.** " +
            "로그인 조회는 사용자 기준으로 세므로 보내도 무시된다. 쇼룸 방문 기록과 같은 값을 써야 " +
            "행동 귀속이 성립한다(§24-7)",
            example = "3f7c1c5a-2f2e-4a9a-9a3f-0f2d9f0a1b2c", nullable = true)
    private String visitorId;
}
