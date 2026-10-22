package showroomz.domain.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import showroomz.domain.notification.entity.Notification;
import showroomz.domain.notification.entity.NotificationPushOutbox;
import showroomz.domain.notification.repository.NotificationPushOutboxRepository;
import showroomz.domain.notification.repository.NotificationRepository;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationType;
import showroomz.global.config.properties.NotificationProperties;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 알림 기록 규칙(1017 설계서 1-0 · 1-6) — 광고성 미동의면 행 없음 · 같은 사건 재진입 무시 · 하루 묶음은 대표 행에 합침 ·
 * 광고성은 지연 푸시(야간이면 다음 아침) · 거래 알림은 커밋 뒤 바로 푸시 · 기록 실패는 호출자로 올리지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationWriter — 동의 · 중복 · 묶음 · 지연 푸시")
class NotificationWriterTest {

    private static final Long USER_ID = 7L;

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationPushOutboxRepository outboxRepository;
    @Mock private NotificationConsentReader consentReader;
    @Mock private NotificationPushDispatcher pushDispatcher;
    @Mock private PlatformTransactionManager transactionManager;

    private NotificationWriter writer;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        writer = new NotificationWriter(notificationRepository, outboxRepository, consentReader, pushDispatcher,
                new NotificationSchedule(new NotificationProperties()), transactionManager);
        lenient().when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pushDispatcher.title(any())).thenAnswer(inv -> "(광고) " + ((Notification) inv.getArgument(0)).getTitle());
    }

    private static NotificationCommand.NotificationCommandBuilder followGroupBuy() {
        return NotificationCommand.toUser(USER_ID, NotificationType.FOLLOW_GROUP_BUY_OPENED)
                .title("뷰티로그의 새 공구가 열렸어요").targetType(NotificationTargetType.GROUP_BUY).targetId(3L);
    }

    @Test
    @DisplayName("광고성 미동의 — 행도 지연 푸시도 만들지 않는다")
    void advertisingWithoutConsentWritesNothing() {
        given(consentReader.allows(USER_ID, NotificationType.FOLLOW_GROUP_BUY_OPENED)).willReturn(false);

        assertThat(writer.write(followGroupBuy().build(), LocalDateTime.parse("2026-10-22T14:00:00"))).isNull();

        verify(notificationRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("광고성 · 야간(21:30) — 행은 지금 쓰고 푸시는 다음 날 08:00 로 미룬다 · 제목에 (광고)")
    void advertisingAtNightIsDeferredToMorning() {
        given(consentReader.allows(USER_ID, NotificationType.FOLLOW_GROUP_BUY_OPENED)).willReturn(true);

        Notification written = writer.write(followGroupBuy().build(), LocalDateTime.parse("2026-10-22T21:30:00"));

        assertThat(written).isNotNull();
        assertThat(written.isAdvertising()).isTrue();
        ArgumentCaptor<NotificationPushOutbox> outbox = ArgumentCaptor.forClass(NotificationPushOutbox.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getSendAfter()).isEqualTo(LocalDateTime.parse("2026-10-23T08:00:00"));
        assertThat(outbox.getValue().getTitle()).startsWith("(광고) ");
    }

    @Test
    @DisplayName("하루 묶음 첫 행 — 푸시는 그날 20:00 묶음 시각")
    void digestFirstRowWaitsForDigestSlot() {
        given(consentReader.allows(USER_ID, NotificationType.FOLLOW_POST_PUBLISHED)).willReturn(true);
        given(notificationRepository.findByDedupKey("FOLLOW_POST:7:1:2026-10-22")).willReturn(Optional.empty());

        writer.write(NotificationCommand.toUser(USER_ID, NotificationType.FOLLOW_POST_PUBLISHED)
                .title("뷰티로그 새 게시물").dedupKey("FOLLOW_POST:7:1:2026-10-22").digest(true)
                .digestTitleFormat("뷰티로그 새 게시물 %d개").build(), LocalDateTime.parse("2026-10-22T10:15:00"));

        ArgumentCaptor<NotificationPushOutbox> outbox = ArgumentCaptor.forClass(NotificationPushOutbox.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getSendAfter()).isEqualTo(LocalDateTime.parse("2026-10-22T20:00:00"));
    }

    @Test
    @DisplayName("하루 묶음 재진입 — 새 행 없이 대표 행 건수 +1 · 제목 「새 게시물 3개」 · 안 읽음으로 · 대기 중인 푸시 문구도 갱신")
    void digestAbsorbsIntoRepresentativeRow() {
        Notification existing = Notification.builder().recipientType(NotificationRecipientType.USER).recipientId(USER_ID)
                .type(NotificationType.FOLLOW_POST_PUBLISHED).title("뷰티로그 새 게시물 2개")
                .dedupKey("FOLLOW_POST:7:1:2026-10-22").createdAt(LocalDateTime.parse("2026-10-22T09:00:00")).build();
        existing.absorbDigest("뷰티로그 새 게시물 2개", null, LocalDateTime.parse("2026-10-22T09:30:00"));
        existing.markRead(LocalDateTime.parse("2026-10-22T09:40:00"));
        NotificationPushOutbox pending = NotificationPushOutbox.of(1L, USER_ID, LocalDateTime.parse("2026-10-22T20:00:00"),
                "(광고) 뷰티로그 새 게시물 2개", null, null, "{}");
        given(consentReader.allows(USER_ID, NotificationType.FOLLOW_POST_PUBLISHED)).willReturn(true);
        given(notificationRepository.findByDedupKey("FOLLOW_POST:7:1:2026-10-22")).willReturn(Optional.of(existing));
        given(outboxRepository.findByNotificationId(any())).willReturn(Optional.of(pending));

        Notification written = writer.write(NotificationCommand.toUser(USER_ID, NotificationType.FOLLOW_POST_PUBLISHED)
                .title("뷰티로그 새 게시물").dedupKey("FOLLOW_POST:7:1:2026-10-22").digest(true)
                .digestTitleFormat("뷰티로그 새 게시물 %d개").build(), LocalDateTime.parse("2026-10-22T11:00:00"));

        assertThat(written).isNull();
        assertThat(existing.getDigestCount()).isEqualTo(3);
        assertThat(existing.getTitle()).isEqualTo("뷰티로그 새 게시물 3개");
        assertThat(existing.isRead()).isFalse();
        assertThat(pending.getTitle()).isEqualTo("(광고) 뷰티로그 새 게시물 3개");
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 사건 재진입(묶음 아님) — 무시한다")
    void duplicateDedupKeyIsIgnored() {
        Notification existing = Notification.builder().recipientType(NotificationRecipientType.SELLER).recipientId(1L)
                .type(NotificationType.CONTRACT_REVIEW_REJECTED).title("반려").dedupKey("K")
                .createdAt(LocalDateTime.now()).build();
        given(notificationRepository.findByDedupKey("K")).willReturn(Optional.of(existing));

        assertThat(writer.write(NotificationCommand.toSeller(1L, NotificationType.CONTRACT_REVIEW_REJECTED)
                .title("반려").dedupKey("K").build(), LocalDateTime.now())).isNull();

        assertThat(existing.getDigestCount()).isEqualTo(1);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("파트너 알림 — 동의 판정 · 지연 푸시가 없다(소비자만 푸시)")
    void sellerRowSkipsConsentAndPush() {
        Notification written = writer.write(NotificationCommand.toSeller(1L, NotificationType.CONTRACT_REVIEW_REJECTED)
                .title("검토가 반려되었습니다").build(), LocalDateTime.parse("2026-10-22T22:00:00"));

        assertThat(written).isNotNull();
        verify(consentReader, never()).allows(anyLong(), any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("소비자 거래 알림 — 커밋 뒤 바로 푸시 · skipPush 면 행만 · 광고성은 즉시 푸시하지 않는다")
    void transactionalUserPushIsImmediateUnlessSkipped() {
        given(consentReader.allows(any(), any())).willReturn(true);

        writer.writeSafely(NotificationCommand.toUser(USER_ID, NotificationType.ORDER_SHIPPED).title("발송했어요").build());
        verify(pushDispatcher).sendNow(any());

        writer.writeSafely(NotificationCommand.toUser(USER_ID, NotificationType.ORDER_SHIPPED).title("발송했어요")
                .skipPush(true).build());
        writer.writeSafely(followGroupBuy().build());
        verify(pushDispatcher).sendNow(any()); // 여전히 1번
    }

    @Test
    @DisplayName("기록 실패 — 예외를 호출자(이미 커밋된 요청)로 올리지 않는다")
    void failureIsSwallowed() {
        given(notificationRepository.save(any(Notification.class))).willThrow(new IllegalStateException("db down"));

        assertThatCode(() -> writer.writeSafely(NotificationCommand.toSeller(1L, NotificationType.CONTRACT_REVIEW_REJECTED)
                .title("반려").build())).doesNotThrowAnyException();
    }
}
