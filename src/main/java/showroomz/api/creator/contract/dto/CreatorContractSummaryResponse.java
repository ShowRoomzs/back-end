package showroomz.api.creator.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 탭 카운트 + GNB 배지(§27 설계서 3).
 *
 * <p>목록과 분리한다. 파트너와 같은 이유(배지는 계약 화면 밖에서도 폴링된다)에 더해,
 * 스튜디오 쪽이 더 중요하다 — <b>배지가 가리키는 것이 「기한이 있는 내 조치」</b>라서
 * 계약 화면에 들어오지 않는 인플루언서가 이 숫자만 보고 움직인다. 놓치면 만료다.
 */
@Schema(description = "스튜디오 계약 탭 카운트 · GNB 배지")
public record CreatorContractSummaryResponse(

        @Schema(description = "탭별 건수 — 키는 ALL/SIGNING/CONCLUSION_PENDING/CONCLUDED/CLOSED. "
                + "**작성중 탭은 키 자체가 없다** — 0건으로 표시하는 것이 아니라 값이 존재하지 않는다(§27-1 #1)",
                example = "{\"ALL\":8,\"SIGNING\":2,\"CONCLUSION_PENDING\":1,\"CONCLUDED\":2,\"CLOSED\":3}")
        Map<String, Long> tabCounts,

        @Schema(description = "**내 서명이 필요한 계약 건수**(GNB 배지 · 목록 헤더 「내 서명이 필요한 계약 N건」). "
                + "`SIGNING`이면서 내 서명이 아직 없는 계약만 센다. "
                + "**브랜드 서명 여부는 보지 않는다** — S3a는 「내 차례」가 아니라 「내 몫이 남은 것」이고, "
                + "양측 미서명(S3)에서도 내 몫은 똑같이 남아 있다. 순서가 아니라 각자 한다(§27-2)",
                example = "1")
        long actionRequiredCount
) {
}
