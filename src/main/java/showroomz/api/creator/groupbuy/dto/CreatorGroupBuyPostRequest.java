package showroomz.api.creator.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 공구 게시물 제목·본문 — 임시저장 · 등록하고 검토 요청 · 승인 후 수정이 같은 모양을 쓴다(31 설계 2-3 ~ 2-5).
 *
 * <p>길이·필수 검증은 Bean Validation이 아니라 서비스가 한다 — 임시저장과 제출의 필수 규칙이 달라
 * (임시저장은 둘 중 하나, 제출은 둘 다) 에러 코드를 경로별로 골라야 한다. 사진 필드는 없다(§31-2).
 */
@Schema(description = "공구 게시물 제목·본문")
public record CreatorGroupBuyPostRequest(

        @Schema(description = "제목 — 40자. 임시저장은 비워도 된다", example = "여름 수분 세럼, 제가 쓰던 그 조합", nullable = true)
        String title,

        @Schema(description = "본문 — 2,000자. 대가관계 문구를 넣지 않는다 — 서버가 렌더링 시점에 붙인다",
                example = "건조한 여름에도…", nullable = true)
        String content
) {
}
