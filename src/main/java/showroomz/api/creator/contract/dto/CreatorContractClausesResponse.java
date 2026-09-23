package showroomz.api.creator.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * 표준 조항 — <b>계약에 고정된 버전</b>을 읽는다(설계서 6-3).
 *
 * <p>파트너는 작성 중(조항 버전 미고정)에도 전문을 봐야 해서 계약과 무관한 경로가 필요했다.
 * 스튜디오가 보는 계약은 전부 {@code clause_version_id}가 고정된 뒤이므로, 계약에 매달린 경로가
 * 더 정확하다 — <b>문안이 개정된 뒤에 내가 서명한 계약의 조항과 화면에 뜨는 조항이 달라지면 안 된다.</b>
 *
 * <p>입력 컨트롤이 하나도 없는 읽기 전용 응답이다(§25-7).
 */
@Schema(description = "계약에 고정된 표준 조항")
public record CreatorContractClausesResponse(

        @Schema(description = "조항 버전 ID", example = "1")
        Long clauseVersionId,

        @Schema(description = "버전 — 화면은 v를 붙여 표기한다", example = "1.0")
        String versionNumber,

        @Schema(description = "시행일")
        LocalDate effectiveDate,

        List<Clause> clauses
) {

    @Schema(description = "조항 1건")
    public record Clause(
            @Schema(example = "PRICE_POLICY") String code,
            @Schema(description = "요약 카드 좌측 라벨", example = "가격 정책") String summaryTitle,
            @Schema(description = "요약 카드 우측 문구") String summaryDescription,
            @Schema(description = "전문 제목 — 문안 미확정 조항은 null", nullable = true) String fullTitle,
            @Schema(description = "전문 본문 — 문안 미확정 조항은 null", nullable = true) String fullBody
    ) {
    }
}
