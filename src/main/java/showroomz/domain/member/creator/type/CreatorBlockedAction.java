package showroomz.domain.member.creator.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 정지 · 탈퇴 심사 중 서버가 거절하는 행위(53 설계서 4-2 · ui-studio-13 rev.1 확정) — 409 메시지에 「무엇이 막혔는지」를 싣는다.
 * 「공구 신청」은 존재하지 않는 행위라 값이 없다(§51-1 ⚠️). 계약 <b>서명</b>은 모두싸인이 보내 막을 수 없다 — 막는 지점은 운영자의
 * 체결이다(1017 설계서 8-3).
 */
@Getter
@RequiredArgsConstructor
public enum CreatorBlockedAction {
    POST_PUBLISH("새 게시물을 등록할"),
    GROUP_BUY_POST_SUBMIT("공구 게시물을 등록할"),
    CONNECTION_ACCEPT("새 연결을 수락할"),
    /** 연장 수락(S4a) — 판매 기간을 늘리는 일. 연장 거절 · 중단 요청은 허용. */
    GROUP_BUY_EXTENSION_ACCEPT("기간 연장을 수락할"),
    /** 쇼룸명 변경(S7) — 소비자 · 브랜드가 정지된 계정을 다른 계정으로 오인한다. 소개 · 프로필 이미지는 허용. */
    SHOWROOM_NAME_CHANGE("쇼룸명을 바꿀"),
    /** 서명 안내 재발송 요청(S3). 계약 거절은 허용. */
    CONTRACT_RESEND_REQUEST("서명 안내 재발송을 요청할");

    /** 「계정 이용 정지 중에는 {phrase} 수 없습니다」의 가운데. */
    private final String phrase;
}
