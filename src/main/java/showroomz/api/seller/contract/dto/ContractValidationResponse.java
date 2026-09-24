package showroomz.api.seller.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.contract.type.ContractViolation;
import showroomz.domain.contract.type.ContractWarning;

import java.util.List;

/**
 * 검증 결과(설계서 2-4) — 상태를 바꾸지 않고 하드·경고 판정만 돌려준다.
 *
 * <p>쓰는 곳 둘: ① 검토 요청 확인 모달(C3)이 열거할 경고를 받는다
 * ② 재작성 직후 위반 행을 표시한다.
 */
@Schema(description = "계약 검증 결과")
public record ContractValidationResponse(

        @Schema(description = "검토 요청이 가능한지 — 하드 위반이 하나도 없을 때만 true", example = "false")
        boolean canSubmit,

        @Schema(description = "검토 요청을 막는 위반. kind가 REQUIRED면 문구 없이 버튼만 비활성한다(§25-6)")
        List<ContractViolation> hardViolations,

        @Schema(description = "막지 않는 경고. 검토 요청 시 이 코드들을 acknowledgedWarnings로 되돌려 보낸다")
        List<ContractWarning> warnings
) {

    public static ContractValidationResponse of(List<ContractViolation> violations, List<ContractWarning> warnings) {
        return new ContractValidationResponse(violations.isEmpty(), violations, warnings);
    }
}
