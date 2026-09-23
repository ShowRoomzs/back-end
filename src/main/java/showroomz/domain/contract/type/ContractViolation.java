package showroomz.domain.contract.type;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 하드 검증 위반 1건(설계서 2-4).
 *
 * @param field 화면 필드 경로. 항목 위반은 index를 포함한다(예: {@code items[0].groupBuyPrice}) —
 *              어느 행이 걸렸는지 서버가 알려주지 않으면 FE가 다시 판정해야 한다.
 */
@Schema(description = "하드 검증 위반")
public record ContractViolation(

        @Schema(example = "H1") String code,

        @Schema(description = "REQUIRED(미입력, 문구 없이 버튼 비활성) / RULE(규칙 위반, 에러 문구)")
        ContractViolationKind kind,

        @Schema(example = "items[0].groupBuyPrice", nullable = true) String field,

        @Schema(example = "공구가는 정가 이하여야 합니다.") String message
) {

    public static ContractViolation of(ContractViolationCode code, String field) {
        return new ContractViolation(code.getCode(), code.getKind(), field, code.getMessage());
    }

    public static ContractViolation of(ContractViolationCode code, String field, String message) {
        return new ContractViolation(code.getCode(), code.getKind(), field, message);
    }
}
