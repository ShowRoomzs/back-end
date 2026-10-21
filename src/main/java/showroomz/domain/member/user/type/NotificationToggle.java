package showroomz.domain.member.user.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.history.type.ConsentType;

/**
 * C15 알림 설정의 광고성 토글 4개(1017 시안) — 화면 순서대로. 켜면 광고성 정보 수신에 동의한 것으로 처리한다.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationToggle {
    FOLLOW_GROUP_BUY("팔로우 쇼룸 새 공구", "팔로우한 쇼룸에 새 공구가 열리면 알려드려요", ConsentType.MARKETING_FOLLOW_GROUP_BUY),
    FOLLOW_POST("팔로우 쇼룸 새 게시물", "같은 쇼룸 소식은 하루에 한 번 묶어서 보내드려요", ConsentType.MARKETING_FOLLOW_POST),
    GROUP_BUY_ENDING("공구 마감 임박", "장바구니에 담거나 좋아요한 상품의 공구가 마감되기 하루 전에 알려드려요",
            ConsentType.MARKETING_GROUP_BUY_ENDING),
    /** 혜택 · 이벤트 — 가입 [선택] 광고성 정보 수신 동의와 같은 값(users.marketing_agree). */
    BENEFIT("혜택 · 이벤트", "쇼룸즈의 혜택과 이벤트 소식을 받아요", ConsentType.MARKETING);

    private final String label;
    private final String description;
    private final ConsentType consentType;
}
