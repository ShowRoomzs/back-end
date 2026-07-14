package showroomz.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ContractStatus {
    DRAFT("작성중"),
    AWAITING_SIGN("서명대기"),
    COMPLETED("체결완료"),
    REJECTED("거절"),
    EXPIRED("만료"),
    CANCELED("취소");

    private final String description;

    /** 종료 상태(체결완료·거절·만료·취소)는 불가역 — 재계약은 신규 계약으로 */
    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == EXPIRED || this == CANCELED;
    }
}
