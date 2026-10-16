package showroomz.global.error.exception;

import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 조건 미충족 — 어느 조건이 몇 건 남았는지를 함께 내린다(52 설계서 0-5 · 탈퇴 승인 재검증). 일반 {@link BusinessException}은 코드와 문장뿐이라
 * 운영자가 「무엇 때문에 막혔는지」를 다시 찾아야 한다. 키는 조건 enum 이름, 값은 남은 건수다.
 */
@Getter
public class ConditionUnmetException extends BusinessException {

    private final Map<String, Long> unmet;

    public ConditionUnmetException(ErrorCode errorCode, Map<String, Long> unmet) {
        super(errorCode);
        this.unmet = Collections.unmodifiableMap(new LinkedHashMap<>(unmet));
    }
}
