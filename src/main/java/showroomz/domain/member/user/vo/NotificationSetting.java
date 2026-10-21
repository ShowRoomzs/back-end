package showroomz.domain.member.user.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.user.type.NotificationToggle;

import java.time.LocalDateTime;

/**
 * C15 설정 · 알림 설정(1017 시안 · 설계서 2절) — 광고성 토글 4개 중 3개를 여기에 둔다. 네 번째(혜택 · 이벤트)는 가입 [선택] 광고성 동의와
 * 같은 값이라 {@code users.marketing_agree}에 그대로 있다.
 *
 * <p>네 토글은 모두 <b>광고성</b>이다(「켜면 수신에 동의한 것으로 처리」) — 켤 때마다 동의 일시를 남겨 화면 「YYYY.MM.DD 수신 동의」를
 * 그리고, 바뀔 때마다 동의 이력을 쌓는다. 주문 · 배송 · 문의 답변 등 거래 알림은 끌 수 없어 설정 항목이 없다.
 */
@Embeddable
@Getter
@NoArgsConstructor
public class NotificationSetting {

    /** 팔로우 쇼룸 새 게시물(하루 묶음). 엔티티 기본값은 켜짐이지만 가입 · 백필(V206)이 광고성 동의 값으로 맞춘다. */
    @Column(nullable = false)
    private boolean followPostPushAgree = true;

    @Column(name = "follow_post_push_agreed_at")
    private LocalDateTime followPostPushAgreedAt;

    /** 팔로우 쇼룸 새 공구. */
    @Column(name = "follow_group_buy_push_agree", nullable = false)
    private boolean followGroupBuyPushAgree = false;

    @Column(name = "follow_group_buy_push_agreed_at")
    private LocalDateTime followGroupBuyPushAgreedAt;

    /** 공구 마감 임박(장바구니 · 좋아요 상품 · 마감 하루 전). */
    @Column(name = "group_buy_ending_push_agree", nullable = false)
    private boolean groupBuyEndingPushAgree = false;

    @Column(name = "group_buy_ending_push_agreed_at")
    private LocalDateTime groupBuyEndingPushAgreedAt;

    public NotificationSetting(boolean followPostPushAgree) {
        this.followPostPushAgree = followPostPushAgree;
    }

    /** null인 값은 변경하지 않는다(부분 업데이트). — 옛 호출 경로 호환. */
    public void update(Boolean followPostPushAgree) {
        if (followPostPushAgree != null) this.followPostPushAgree = followPostPushAgree;
    }

    public boolean isOn(NotificationToggle toggle) {
        return switch (toggle) {
            case FOLLOW_POST -> followPostPushAgree;
            case FOLLOW_GROUP_BUY -> followGroupBuyPushAgree;
            case GROUP_BUY_ENDING -> groupBuyEndingPushAgree;
            case BENEFIT -> throw new IllegalArgumentException("혜택 · 이벤트는 users.marketing_agree 다");
        };
    }

    public LocalDateTime agreedAt(NotificationToggle toggle) {
        return switch (toggle) {
            case FOLLOW_POST -> followPostPushAgreedAt;
            case FOLLOW_GROUP_BUY -> followGroupBuyPushAgreedAt;
            case GROUP_BUY_ENDING -> groupBuyEndingPushAgreedAt;
            case BENEFIT -> throw new IllegalArgumentException("혜택 · 이벤트는 users.marketing_agree_changed_at 이다");
        };
    }

    /** @return 값이 실제로 바뀌었는가 — 켤 때만 동의 일시를 새로 찍는다(끄면 화면 「수신 동의」 줄이 사라진다). */
    public boolean set(NotificationToggle toggle, boolean on, LocalDateTime now) {
        if (isOn(toggle) == on) {
            return false;
        }
        switch (toggle) {
            case FOLLOW_POST -> {
                followPostPushAgree = on;
                followPostPushAgreedAt = on ? now : null;
            }
            case FOLLOW_GROUP_BUY -> {
                followGroupBuyPushAgree = on;
                followGroupBuyPushAgreedAt = on ? now : null;
            }
            case GROUP_BUY_ENDING -> {
                groupBuyEndingPushAgree = on;
                groupBuyEndingPushAgreedAt = on ? now : null;
            }
            case BENEFIT -> throw new IllegalArgumentException("혜택 · 이벤트는 users.marketing_agree 다");
        }
        return true;
    }
}
