package showroomz.domain.member.creator.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 정지 · 탈퇴 심사 중 서버가 거절하는 행위(53 설계서 4-2) — 409 메시지에 「무엇이 막혔는지」를 싣는다. 「공구 신청」은 존재하지 않는
 * 행위라 값이 없다(§51-1 ⚠️).
 */
@Getter
@RequiredArgsConstructor
public enum CreatorBlockedAction {
    POST_PUBLISH("새 게시물을 등록할"),
    GROUP_BUY_POST_SUBMIT("공구 게시물을 제출할"),
    CONTRACT_SIGNATURE("계약 서명을 진행할"),
    CONNECTION_ACCEPT("새 연결을 수락할");

    /** 「계정 정지 중에는 {phrase} 수 없습니다」의 가운데. */
    private final String phrase;
}
