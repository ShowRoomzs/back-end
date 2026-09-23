package showroomz.domain.contract.type;

import io.swagger.v3.oas.annotations.media.Schema;

/** 경고 1건 — 검토 요청을 막지 않고 확인 모달(C3)에 열거된다(설계서 2-3). */
@Schema(description = "경고")
public record ContractWarning(

        @Schema(example = "W2") String code,

        @Schema(example = "리워드율이 40%를 넘습니다.") String message
) {

    public static ContractWarning of(ContractWarningCode code) {
        return new ContractWarning(code.getCode(), code.getMessage());
    }

    public static ContractWarning of(ContractWarningCode code, String message) {
        return new ContractWarning(code.getCode(), message);
    }
}
