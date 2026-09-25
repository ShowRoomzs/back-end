package showroomz.api.creator.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 탭 카운트 + 「내 조치 필요」 수(31 설계 3-3). GNB 배지가 공구 화면 밖에서도 폴링되므로 목록과 분리한다 —
 * 연장 응답 기한이 현재 종료 시각이라 공구 화면에 들어오지 않는 인플루언서는 배지만 보고 움직인다.
 */
@Schema(description = "스튜디오 공구 탭 카운트 · 내 조치 필요 수")
public record CreatorGroupBuySummaryResponse(

        @Schema(description = "탭 코드별 건수 — ALL · PREPARING · READY · IN_PROGRESS · ENDED · SUSPENDED",
                example = "{\"ALL\":11,\"PREPARING\":3,\"READY\":1,\"IN_PROGRESS\":4,\"ENDED\":2,\"SUSPENDED\":1}")
        Map<String, Long> tabCounts,

        @Schema(description = "내 조치가 필요한 공구 수 — 게시물 작성·재등록 · 숨김 게시물 수정 · 연장 응답 · 이행 확인",
                example = "3")
        long actionRequiredCount
) {
}
