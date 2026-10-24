package showroomz.domain.member.creator.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 인플루언서 회원 상태 3종(53 설계서 0-1) — 소비자 계정의 {@code Users.status}와 <b>다른 축</b>이다. 탈퇴해도 소비자 계정은 남고(§51-5),
 * 인플루언서 정지는 소비자 기능을 막지 않는다. 목록 상태 열 · 상세 배지 · 운영팀 채널의 회원 상태가 이 값 하나를 읽는다.
 *
 * <p>탈퇴 <b>신청</b>은 상태가 아니다(§51-0) — 승인 전까지 인플루언서는 실제로 활성이다.
 */
@Getter
@RequiredArgsConstructor
public enum CreatorStatus {
    ACTIVE("활성", "SUCCESS"),
    SUSPENDED("정지", "DANGER"),
    WITHDRAWN("탈퇴", "NEUTRAL");

    private final String label;
    private final String tone;
}
