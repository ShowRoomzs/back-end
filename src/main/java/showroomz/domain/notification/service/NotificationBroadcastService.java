package showroomz.domain.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.notification.type.NotificationActorType;
import showroomz.domain.notification.type.NotificationImageKind;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 전 소비자 알림 — 약관 · 개인정보 처리방침 개정(C11 「개인정보 처리방침이 11.01부터 바뀌어요」 · 1017 설계서 1-3).
 * 수십만 행을 엔티티로 쓰지 않고 한 문장(INSERT … SELECT)으로 넣는다. 푸시는 보내지 않는다(서비스 공지 · 목록에서 본다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationBroadcastService {

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM.dd");

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public int termsChanged(Long documentId, String documentName, LocalDate effectiveDate) {
        String title = documentName + "이 " + effectiveDate.format(MD) + "부터 바뀌어요";
        int inserted = jdbcTemplate.update("""
                INSERT INTO notification (recipient_type, recipient_id, type, category, actor_type, title, emphasis,
                                          target_type, target_id, image_kind, advertising, digest_count, created_at)
                SELECT ?, u.USER_ID, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, 1, ?
                FROM users u
                WHERE u.STATUS <> 'WITHDRAWN' AND u.ROLE_TYPE IN ('USER', 'CREATOR')""",
                NotificationRecipientType.USER.name(), NotificationType.TERMS_CHANGED.name(),
                NotificationType.TERMS_CHANGED.getCategory(), NotificationActorType.SYSTEM.name(), title, documentName,
                NotificationTargetType.TERMS.name(), documentId, NotificationImageKind.ICON.name(), LocalDateTime.now());
        log.info("약관 개정 알림 - documentId: {}, 수신자: {}", documentId, inserted);
        return inserted;
    }
}
