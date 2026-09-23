package showroomz.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 경고 W1~W6 — 막지 않고 <b>기록</b>한다(설계서 2-3).
 *
 * <p>검토 요청 API가 자체적으로 다시 판정하고 결과를 {@code warning_flags}에 저장한다.
 * FE가 보낸 목록을 그대로 믿지 않는다 — 사용자가 모달을 본 뒤 다른 탭에서 값을 고쳤을 수 있다.
 *
 * <p>수신처(어드민 노출)는 미결(§28-8 A)이라 <b>저장만 하고 노출은 붙이지 않는다</b>.
 * 저장을 미루면 확정된 뒤 과거 계약의 경고를 복원할 방법이 없다.
 */
@Getter
@RequiredArgsConstructor
public enum ContractWarningCode {

    W1("W1", "할인율이 70%를 넘습니다."),
    W2("W2", "리워드율이 40%를 넘습니다."),
    W3("W3", "공구 기간이 14일을 넘습니다."),
    W4("W4", "공구 시작일이 검토 요청일로부터 60일 이후입니다."),
    W5("W5", "상품이 10건을 넘습니다."),
    W6("W6", "고정 지급비가 1,000,000원을 넘습니다.");

    private final String code;
    private final String message;
}
