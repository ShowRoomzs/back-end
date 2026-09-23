package showroomz.global.error.exception;

import lombok.Getter;
import showroomz.domain.contract.type.ContractViolation;

import java.util.List;

/**
 * 하드 검증 H1~H8 실패(설계서 2-2).
 *
 * <p>일반 {@link BusinessException}과 달리 <b>위반 항목 목록</b>을 함께 내려야 한다 —
 * 어느 행의 어느 필드가 걸렸는지("items[0].groupBuyPrice")를 서버가 알려주지 않으면
 * FE가 같은 판정을 다시 한다.
 */
@Getter
public class ContractValidationException extends BusinessException {

    private final List<ContractViolation> violations;

    public ContractValidationException(List<ContractViolation> violations) {
        super(ErrorCode.CONTRACT_VALIDATION_FAILED);
        this.violations = List.copyOf(violations);
    }
}
