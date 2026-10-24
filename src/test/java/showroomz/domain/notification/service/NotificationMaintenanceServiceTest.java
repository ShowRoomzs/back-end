package showroomz.domain.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import showroomz.domain.notification.entity.NotificationPushOutbox;
import showroomz.domain.notification.repository.NotificationPushOutboxRepository;
import showroomz.domain.notification.repository.NotificationRepository;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.global.config.properties.NotificationProperties;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 지연 푸시 발송 · 보관 정리(1017 설계서 1-6) — 야간에는 돌아도 보내지 않는다 · 실패는 3회까지 다시 시도하고 그 뒤 닫는다 ·
 * 알림함 보관은 소비자 90일 · 파트너 · 스튜디오 30일 · 보낸 지연 푸시는 7일 뒤 지운다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationMaintenanceService — 지연 푸시 발송 · 재시도 · 보관")
class NotificationMaintenanceServiceTest {

    private static final LocalDateTime NOON = LocalDateTime.parse("2026-10-22T12:00:00");

    @Mock private NotificationPushOutboxRepository outboxRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationPushDispatcher pushDispatcher;

    private NotificationMaintenanceService service;

    @BeforeEach
    void setUp() {
        NotificationProperties properties = new NotificationProperties();
        service = new NotificationMaintenanceService(outboxRepository, notificationRepository, pushDispatcher,
                new NotificationSchedule(properties), properties);
    }

    private static NotificationPushOutbox outbox(Long userId) {
        return NotificationPushOutbox.of(1L, userId, NOON.minusHours(1), "(광고) 새 공구", "본문", null, "{}");
    }

    @Test
    @DisplayName("야간(22시) — 대기열을 읽지도 않는다")
    void quietHoursSendNothing() {
        assertThat(service.sendDue(LocalDateTime.parse("2026-10-22T22:00:00"))).isZero();

        verifyNoInteractions(outboxRepository, pushDispatcher);
    }

    @Test
    @DisplayName("보낼 때가 된 행 — 성공은 보냄 처리 · 실패는 다시 시도하도록 열어 둔다")
    void sendsDueRowsAndKeepsFailuresOpen() {
        NotificationPushOutbox ok = outbox(1L);
        NotificationPushOutbox failing = outbox(2L);
        given(outboxRepository.findDue(eq(NOON), any())).willReturn(List.of(ok, failing));
        given(pushDispatcher.send(eq(1L), any(), any(), any(), any())).willReturn(true);
        given(pushDispatcher.send(eq(2L), any(), any(), any(), any())).willReturn(false);

        assertThat(service.sendDue(NOON)).isEqualTo(1);

        assertThat(ok.isSent()).isTrue();
        assertThat(ok.getSentAt()).isEqualTo(NOON);
        assertThat(failing.isSent()).isFalse();
        assertThat(failing.getAttempt()).isEqualTo(1);
        assertThat(failing.getLastError()).isEqualTo("push failed");
    }

    @Test
    @DisplayName("세 번째 실패 — 보낸 것으로 닫는다(알림함 행은 남는다)")
    void thirdFailureCloses() {
        NotificationPushOutbox failing = outbox(2L);
        given(outboxRepository.findDue(any(), any())).willReturn(List.of(failing));
        given(pushDispatcher.send(any(), any(), any(), any(), any())).willReturn(false);

        service.sendDue(NOON);
        service.sendDue(NOON.plusMinutes(1));
        assertThat(failing.isSent()).isFalse();
        service.sendDue(NOON.plusMinutes(2));

        assertThat(failing.isSent()).isTrue();
        assertThat(failing.getAttempt()).isEqualTo(3);
        verify(notificationRepository, never()).deleteExpired(any(), any());
    }

    @Test
    @DisplayName("보관 정리 — 소비자 90일 · 파트너 · 스튜디오 30일 전 행 · 보낸 지 7일 지난 지연 푸시")
    void purgeUsesRetentionPerRecipient() {
        given(notificationRepository.deleteExpired(any(), any())).willReturn(2);

        assertThat(service.purge(NOON)).isEqualTo(2 * NotificationRecipientType.values().length);

        verify(notificationRepository).deleteExpired(NotificationRecipientType.USER, NOON.minusDays(90));
        verify(notificationRepository).deleteExpired(NotificationRecipientType.SELLER, NOON.minusDays(30));
        verify(notificationRepository).deleteExpired(NotificationRecipientType.CREATOR, NOON.minusDays(30));
        verify(outboxRepository).deleteSentBefore(NOON.minusDays(7));
    }
}
