package showroomz.api.app.showroom.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * §22-4 쇼룸 방문 기록 요청 — 쇼룸 화면이 열릴 때 한 번 호출한다.
 *
 * <p>{@code source}는 쇼룸 링크에 붙은 소스 값(`?from=ig`)을 그대로 넘긴 것이다. 앱 딥링크가 이 값을
 * 보존하지 못하면 대부분의 방문이 "직접 유입"으로 뭉치므로, 링크 발급 규칙과 함께 봐야 한다(§22-5).
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "쇼룸 방문 기록 요청")
public class ShowroomVisitRequest {

    @Schema(description = "쇼룸 링크의 소스 값 — ig · search · post · direct. 없으면 직접 유입으로 집계",
            example = "ig", nullable = true)
    private String source;

    @Schema(description = "비로그인 방문자의 디바이스 식별자 — **비로그인일 때 필수.** " +
            "로그인 방문은 사용자 기준으로 세므로 보내도 무시된다.",
            example = "3f7c1c5a-2f2e-4a9a-9a3f-0f2d9f0a1b2c", nullable = true)
    private String visitorId;
}
