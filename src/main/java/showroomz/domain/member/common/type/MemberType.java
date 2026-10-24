package showroomz.domain.member.common.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 회원 유형 3종 — 어드민 회원 관리 · 운영팀 채널 · 알림이 같은 축으로 회원을 가리킨다(정합 계획서 R1-1). 식별자는 유형마다 다르다:
 * 소비자 = {@code user_id} · 브랜드 = {@code market_id} · 인플루언서 = {@code creator_id}.
 *
 * <p>회원번호 접두사도 여기 둔다 — 별도 채번 없이 id 를 포맷한다(§25-3 · 52 설계서 0-1 · 53 설계서 0-12).
 */
@Getter
@RequiredArgsConstructor
public enum MemberType {
    CONSUMER("소비자", "CST-"),
    BRAND("브랜드", "BRD-"),
    CREATOR("인플루언서", "INF-");

    private final String label;
    /** 회원번호 접두사 — {@code CST-88231} · {@code BRD-1042} · {@code INF-3018}. */
    private final String memberNoPrefix;
}
