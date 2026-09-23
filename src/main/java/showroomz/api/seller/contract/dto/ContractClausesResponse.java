package showroomz.api.seller.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * 표준 조항 전문(모달 C1·C5) + 작성 화면의 요약 카드.
 *
 * <p>요약과 전문을 같은 행에서 파생시킨다(설계서 4-6) — 두 목록을 따로 관리하면
 * 현재 시안의 요약 11개 ↔ 전문 9조 같은 어긋남이 또 생긴다.
 */
@Schema(description = "표준 조항")
public record ContractClausesResponse(

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
            @Schema(description = "전문 제목 — 문안 미확정 조항은 null(설계서 미결 #5)",
                    example = "제3조 최저가 정책", nullable = true) String fullTitle,
            @Schema(description = "전문 본문 — 문안 미확정 조항은 null", nullable = true) String fullBody
    ) {
    }
}
