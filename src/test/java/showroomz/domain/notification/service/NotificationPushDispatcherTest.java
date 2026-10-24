package showroomz.domain.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import showroomz.domain.notification.entity.Notification;
import showroomz.domain.notification.repository.DeviceTokenRepository;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationType;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 푸시 문구(1017 설계서 1-6) — 광고성은 제목 앞 「(광고)」 · 본문 끝 수신거부 방법(정보통신망법 §50). 거래 알림은 그대로.
 * 앱이 알림 행으로 이동하도록 데이터에 알림 · 대상 식별자를 싣는다.
 */
@DisplayName("NotificationPushDispatcher — 광고 표기 · 푸시 데이터")
class NotificationPushDispatcherTest {

    private final NotificationPushDispatcher dispatcher = new NotificationPushDispatcher(mock(DeviceTokenRepository.class),
            null, null, mock(PlatformTransactionManager.class));

    private static Notification notification(NotificationType type, String body) {
        Notification n = Notification.builder().recipientType(NotificationRecipientType.USER).recipientId(1L).type(type)
                .title("뷰티로그의 새 공구가 열렸어요").body(body).targetType(NotificationTargetType.GROUP_BUY).targetId(31L)
                .createdAt(LocalDateTime.now()).build();
        ReflectionTestUtils.setField(n, "id", 501L);
        return n;
    }

    @Test
    @DisplayName("광고성 — 「(광고)」 제목 · 본문 다음 줄에 수신거부 방법")
    void advertisingIsLabelled() {
        Notification ad = notification(NotificationType.FOLLOW_GROUP_BUY_OPENED, "밤 리페어 크림 공구");

        assertThat(dispatcher.title(ad)).isEqualTo("(광고) 뷰티로그의 새 공구가 열렸어요");
        assertThat(dispatcher.body(ad)).isEqualTo("밤 리페어 크림 공구\n수신거부: 마이 > 설정 > 알림");
    }

    @Test
    @DisplayName("광고성 · 본문 없음 — 본문은 수신거부 방법 한 줄")
    void advertisingWithoutBody() {
        assertThat(dispatcher.body(notification(NotificationType.FOLLOW_POST_PUBLISHED, null)))
                .isEqualTo("수신거부: 마이 > 설정 > 알림");
    }

    @Test
    @DisplayName("거래 알림 — 문구를 바꾸지 않는다")
    void transactionalIsUntouched() {
        Notification shipped = notification(NotificationType.ORDER_SHIPPED, "CJ대한통운 1234");

        assertThat(dispatcher.title(shipped)).isEqualTo("뷰티로그의 새 공구가 열렸어요");
        assertThat(dispatcher.body(shipped)).isEqualTo("CJ대한통운 1234");
    }

    @Test
    @DisplayName("푸시 데이터 — type=NOTIFICATION · 알림 id · 유형 · 이동 대상")
    void dataCarriesTarget() {
        Map<String, String> data = NotificationPushDispatcher.data(notification(NotificationType.ORDER_SHIPPED, null));

        assertThat(data).containsEntry("type", "NOTIFICATION")
                .containsEntry("notificationId", "501")
                .containsEntry("notificationType", "ORDER_SHIPPED")
                .containsEntry("targetType", "GROUP_BUY")
                .containsEntry("targetId", "31")
                .doesNotContainKey("targetSub");
        assertThat(dispatcher.dataJson(notification(NotificationType.ORDER_SHIPPED, null)))
                .contains("\"notificationId\":\"501\"");
    }
}
