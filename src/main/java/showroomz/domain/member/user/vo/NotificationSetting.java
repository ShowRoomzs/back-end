package showroomz.domain.member.user.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * C15 설정 · 알림 설정.
 *
 * <p>화면에 남는 토글은 두 개뿐이다 — 팔로우 쇼룸 새 게시물 알림, 광고성 정보 수신 동의.
 * 광고성 정보 수신은 가입 시 [선택] 동의와 같은 값이라 {@code users.marketing_agree}에 그대로 두고,
 * 여기에는 끌 수 있는 서비스 알림만 담는다.
 *
 * <p>주문·배송·문의 답변 등 거래 알림은 끌 수 없으므로 설정 항목 자체를 두지 않는다.
 */
@Embeddable
@Getter
@NoArgsConstructor
public class NotificationSetting {

    /** 팔로우한 쇼룸이 새 공구나 게시물을 올렸을 때 알림 */
    @Column(nullable = false)
    private boolean followPostPushAgree = true;

    public NotificationSetting(boolean followPostPushAgree) {
        this.followPostPushAgree = followPostPushAgree;
    }

    /** null인 값은 변경하지 않는다(부분 업데이트). */
    public void update(Boolean followPostPushAgree) {
        if (followPostPushAgree != null) this.followPostPushAgree = followPostPushAgree;
    }
}
