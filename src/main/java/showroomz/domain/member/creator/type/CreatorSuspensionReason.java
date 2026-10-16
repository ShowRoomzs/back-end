package showroomz.domain.member.creator.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 인플루언서 정지 사유 6종(§51-2 · M1) — 브랜드 목록을 그대로 쓰지 않는다. <b>리워드 지급 정지는 사유가 정한다</b> — 정산 정보 불일치만
 * 멈춘다. 고정 지급비는 플랫폼을 거치지 않아 무관하다. 「허위 기재」는 M1 목록에 없어 값을 만들지 않는다(§55 D-32).
 */
@Getter
@RequiredArgsConstructor
public enum CreatorSuspensionReason {
    AD_DISCLOSURE_OMITTED_REPEATED("협찬 표기 누락 반복", false),
    AD_LAW_VIOLATION_REPEATED("표시광고법 위반 문구 반복", false),
    GROUP_BUY_NONPERFORMANCE("공구 진행 불이행", false),
    CONSUMER_RESPONSE_NEGLECT("소비자 응대 불이행", false),
    SETTLEMENT_INFO_MISMATCH("정산 정보 불일치", true),
    OTHER("기타(직접 입력)", false);

    private final String label;
    /** 인플루언서 몫(리워드) 지급 정지. */
    private final boolean holdsPayout;

    public boolean isDetailRequired() {
        return this == OTHER;
    }
}
