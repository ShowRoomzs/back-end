package showroomz.domain.member.user.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.notification.service.NotificationCommand;
import showroomz.domain.notification.service.NotificationPublisher;
import showroomz.domain.notification.type.NotificationActorType;
import showroomz.domain.notification.type.NotificationImageKind;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationType;
import showroomz.global.utils.KstDates;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 마케팅 수신 동의 2년 재확인(정보통신망법 시행령 제62조의3 · 수정요청 C) — 광고성 정보 수신에 동의한 날(또는 마지막 확인 통지일)부터 2년이
 * 지나면 수신 동의 사실(동의 일자)과 철회 방법을 알린다. 통지하면 그날부터 다시 2년을 센다. 동의를 바꾸지 않는다 — 철회는 소비자가 설정에서 한다.
 */
@Service
@RequiredArgsConstructor
public class MarketingConsentReconfirmService {

    public static final int RECONFIRM_YEARS = 2;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final EntityManager entityManager;
    private final NotificationPublisher notificationPublisher;

    /** 대상 — 동의 중 ∧ 탈퇴 아님 ∧ 동의(변경) · 가입 · 마지막 통지 중 늦은 시각 + 2년 경과. */
    @Transactional(readOnly = true)
    public List<Long> findIdsToNotice(LocalDateTime now, int limit) {
        LocalDateTime before = now.minusYears(RECONFIRM_YEARS);
        return entityManager.createQuery("SELECT u.id FROM Users u WHERE u.marketingAgree = true "
                        + "AND u.status <> :withdrawn "
                        + "AND COALESCE(u.marketingAgreeChangedAt, u.createdAt) <= :before "
                        + "AND (u.marketingConsentNoticedAt IS NULL OR u.marketingConsentNoticedAt <= :before) "
                        + "ORDER BY u.id ASC", Long.class)
                .setParameter("withdrawn", UserStatus.WITHDRAWN)
                .setParameter("before", before)
                .setMaxResults(limit)
                .getResultList();
    }

    @Transactional
    public boolean notice(Long userId, LocalDateTime now) {
        Users user = entityManager.find(Users.class, userId);
        if (user == null || !user.isMarketingAgree() || user.getStatus() == UserStatus.WITHDRAWN
                || user.marketingConsentBaseAt().isAfter(now.minusYears(RECONFIRM_YEARS))) {
            return false;
        }
        LocalDateTime consentedAt = user.getMarketingAgreeChangedAt() != null ? user.getMarketingAgreeChangedAt()
                : user.getCreatedAt();
        notificationPublisher.publish(NotificationCommand.toUser(userId, NotificationType.MARKETING_CONSENT_RECONFIRM)
                .actorType(NotificationActorType.SYSTEM)
                .title(KstDates.toKst(consentedAt).format(DAY) + "에 광고성 정보 수신에 동의하셨어요 · 계속 받으시려면 그대로 두시고, "
                        + "원치 않으시면 설정에서 언제든 철회할 수 있어요")
                .emphasis("광고성 정보 수신 동의")
                .imageKind(NotificationImageKind.ICON)
                .targetType(NotificationTargetType.SETTINGS).build());
        user.markMarketingConsentNoticed(now);
        return true;
    }
}
