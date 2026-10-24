package showroomz.domain.notification.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.NotificationToggle;
import showroomz.domain.notification.type.NotificationType;

/**
 * 광고성 알림 수신 동의(C15 · 1017 설계서 2절) — 미동의면 알림함 행 자체를 만들지 않는다(C11 「광고 수신 미동의면 광고성 행이 빠진다」).
 * 토글은 유형별이다 — 팔로우 쇼룸 새 공구 · 새 게시물 · 공구 마감 임박 · 혜택 · 이벤트.
 */
@Component
public class NotificationConsentReader {

    @PersistenceContext
    private EntityManager entityManager;

    public boolean allows(Long userId, NotificationType type) {
        if (!type.isAdvertising()) {
            return true;
        }
        Users user = entityManager.find(Users.class, userId);
        if (user == null || user.getStatus() == null || user.getStatus().name().equals("WITHDRAWN")) {
            return false;
        }
        return switch (type) {
            case FOLLOW_GROUP_BUY_OPENED -> on(user, NotificationToggle.FOLLOW_GROUP_BUY);
            case FOLLOW_POST_PUBLISHED -> on(user, NotificationToggle.FOLLOW_POST);
            case GROUP_BUY_ENDING_D1 -> on(user, NotificationToggle.GROUP_BUY_ENDING);
            case BENEFIT -> user.isMarketingAgree();
            default -> true;
        };
    }

    private static boolean on(Users user, NotificationToggle toggle) {
        return user.getNotificationSetting() != null && user.getNotificationSetting().isOn(toggle);
    }
}
