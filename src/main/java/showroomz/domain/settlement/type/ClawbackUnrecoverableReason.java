package showroomz.domain.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 미회수 사유(44 어드민 설계서 3-6) — 대상 측이 다음 정산을 가질 수 없다. 회수 방법은 [자문대기-법률 J]라 표시만 한다. */
@Getter
@RequiredArgsConstructor
public enum ClawbackUnrecoverableReason {
    CREATOR_WITHDRAWN("인플루언서 탈퇴"),
    MARKET_WITHDRAWN("브랜드 탈퇴");

    private final String label;
}
