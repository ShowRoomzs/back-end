package showroomz.domain.member.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import showroomz.domain.notification.repository.DeviceTokenRepository;
import showroomz.domain.notification.repository.DeviceTokenRow;
import showroomz.domain.notification.service.DeviceTokenService;
import showroomz.domain.notification.service.PushMessage;
import showroomz.domain.notification.service.PushResult;
import showroomz.domain.notification.service.PushSender;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 소비자 정지 · 해제 푸시(51-6 설계서 8절). 커밋 뒤 콜백에서 불리므로 <b>어떤 예외도 밖으로 내보내면 안 된다</b> — 이미 커밋된 정지 요청이 500 으로
 * 바뀐다. 그리고 만료 토큰 정리가 끝난 트랜잭션에 합류해 사라지지 않게 새 트랜잭션(REQUIRES_NEW)을 연다.
 */
@ExtendWith(MockitoExtension.class)
class UserSanctionNotifierTest {

    private static final long USER_ID = 42L;

    @Mock
    private DeviceTokenRepository deviceTokenRepository;
    @Mock
    private DeviceTokenService deviceTokenService;
    @Mock
    private PushSender pushSender;
    @Mock
    private PlatformTransactionManager transactionManager;

    private UserSanctionNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new UserSanctionNotifier(deviceTokenRepository, deviceTokenService, pushSender, transactionManager);
    }

    private static List<DeviceTokenRow> rows(long fromId, long toId) {
        return LongStream.rangeClosed(fromId, toId).mapToObj(id -> new DeviceTokenRow(id, "token-" + id)).toList();
    }

    @Test
    @DisplayName("정지 알림 — 사유는 앱 문구로 · 앱이 계정 상태를 다시 읽게 하는 표식을 싣는다 · 새 트랜잭션에서 보낸다")
    void suspendedMessage() {
        given(deviceTokenRepository.findUserTokens(eq(USER_ID), eq(0L), any(Pageable.class))).willReturn(rows(1, 2));
        given(pushSender.send(anyList(), any())).willReturn(new PushResult(2, 0, List.of()));

        notifier.suspended(USER_ID, "욕설·비방");

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender).send(eq(List.of("token-1", "token-2")), message.capture());
        assertThat(message.getValue().title()).isEqualTo("계정 이용이 정지되었어요");
        assertThat(message.getValue().body()).contains("사유: 욕설·비방").contains("진행 중인 주문은 그대로");
        assertThat(message.getValue().data())
                .containsEntry("type", UserSanctionNotifier.DATA_TYPE)
                .containsEntry("event", "SUSPENDED");

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    @DisplayName("해제 알림 문구 · 표식")
    void releasedMessage() {
        given(deviceTokenRepository.findUserTokens(eq(USER_ID), eq(0L), any(Pageable.class))).willReturn(rows(1, 1));
        given(pushSender.send(anyList(), any())).willReturn(new PushResult(1, 0, List.of()));

        notifier.released(USER_ID);

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender).send(anyList(), message.capture());
        assertThat(message.getValue().title()).isEqualTo("계정 이용 정지가 해제되었어요");
        assertThat(message.getValue().data()).containsEntry("event", "RELEASED");
    }

    @Test
    @DisplayName("기기가 없으면 보내지 않는다")
    void noDeviceNoSend() {
        given(deviceTokenRepository.findUserTokens(eq(USER_ID), eq(0L), any(Pageable.class))).willReturn(List.of());

        notifier.released(USER_ID);

        verify(pushSender, never()).send(anyList(), any());
    }

    @Test
    @DisplayName("기기가 500대를 넘으면 마지막 id 다음부터 키셋으로 이어 읽고, 만료 토큰은 묶음마다 지운다")
    void keysetChunksAndPurgesInvalidTokens() {
        given(deviceTokenRepository.findUserTokens(eq(USER_ID), eq(0L), any(Pageable.class))).willReturn(rows(1, 500));
        given(deviceTokenRepository.findUserTokens(eq(USER_ID), eq(500L), any(Pageable.class))).willReturn(rows(501, 502));
        given(pushSender.send(anyList(), any()))
                .willReturn(new PushResult(499, 1, List.of("token-7")))
                .willReturn(new PushResult(2, 0, List.of()));

        notifier.suspended(USER_ID, "반품 남용");

        verify(pushSender, times(2)).send(anyList(), any());
        verify(deviceTokenService).purgeInvalid(List.of("token-7"));
        verify(deviceTokenService).purgeInvalid(List.of());
    }

    @Test
    @DisplayName("발송이 실패해도 예외를 밖으로 내보내지 않는다 — 조치는 이미 커밋됐다")
    void sendFailureIsSwallowed() {
        given(deviceTokenRepository.findUserTokens(eq(USER_ID), anyLong(), any(Pageable.class))).willReturn(rows(1, 1));
        given(pushSender.send(anyList(), any())).willThrow(new IllegalStateException("FCM 응답 없음"));

        assertThatCode(() -> notifier.suspended(USER_ID, "욕설·비방")).doesNotThrowAnyException();
        verify(transactionManager).rollback(any());
    }

    @Test
    @DisplayName("새 트랜잭션을 열지 못해도 예외를 밖으로 내보내지 않는다")
    void transactionFailureIsSwallowed() {
        given(transactionManager.getTransaction(any())).willThrow(new TransactionSystemException("커넥션 고갈"));

        assertThatCode(() -> notifier.released(USER_ID)).doesNotThrowAnyException();
        verify(pushSender, never()).send(anyList(), any());
    }
}
