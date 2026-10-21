package showroomz.domain.member.user.service;

import org.springframework.beans.factory.annotation.Autowired;
import showroomz.domain.notification.type.NotificationType;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationImageKind;
import showroomz.domain.notification.type.NotificationActorType;
import showroomz.domain.notification.service.NotificationPublisher;
import showroomz.domain.notification.service.NotificationCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import showroomz.domain.notification.repository.DeviceTokenRepository;
import showroomz.domain.notification.repository.DeviceTokenRow;
import showroomz.domain.notification.service.DeviceTokenService;
import showroomz.domain.notification.service.PushMessage;
import showroomz.domain.notification.service.PushResult;
import showroomz.domain.notification.service.PushSender;

import java.util.List;

/**
 * 소비자 정지 · 해제 알림(51-6 설계서 8절) — 브랜드 · 인플루언서와 달리 <b>실제로 보낸다</b>(앱 푸시 인프라가 있다). 호출 지점은 전부 커밋
 * 뒤다. 서비스 통지라 광고성 수신 동의 · 팔로우 게시물 수신 토글과 무관하다({@code findUserTokens}).
 *
 * <p>발송 실패는 조치를 되돌리지 않는다 — 앱은 다음 진입에 {@code GET /v1/user/account-status}로 같은 사실을 받는다. 커밋 뒤 콜백에서
 * 불리므로 만료 토큰 정리가 끝난 트랜잭션에 합류하지 않게 새 트랜잭션을 열고, 어떤 예외도 호출자(이미 커밋된 요청)로 올리지 않는다.
 */
@Slf4j
@Component
public class UserSanctionNotifier {

    /** 앱이 알림을 받았을 때 계정 상태를 다시 읽게 하는 표식. */
    public static final String DATA_TYPE = "ACCOUNT_STATUS";

    private static final int CHUNK_SIZE = 500;

    private final DeviceTokenRepository deviceTokenRepository;
    private final DeviceTokenService deviceTokenService;
    private final PushSender pushSender;
    private final TransactionTemplate newTransaction;

    private final NotificationPublisher notificationPublisher;

    /** 푸시만 검증하는 단위 테스트용 — 알림함 행은 쓰지 않는다. */
    public UserSanctionNotifier(DeviceTokenRepository deviceTokenRepository, DeviceTokenService deviceTokenService,
                                PushSender pushSender, PlatformTransactionManager transactionManager) {
        this(deviceTokenRepository, deviceTokenService, pushSender, transactionManager, null);
    }

    @Autowired
    public UserSanctionNotifier(DeviceTokenRepository deviceTokenRepository, DeviceTokenService deviceTokenService,
                                PushSender pushSender, PlatformTransactionManager transactionManager,
                                NotificationPublisher notificationPublisher) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.deviceTokenService = deviceTokenService;
        this.pushSender = pushSender;
        this.notificationPublisher = notificationPublisher;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void suspended(Long userId, String appReasonLabel) {
        inbox(userId, NotificationType.USER_ACCOUNT_SUSPENDED, "계정 이용이 정지되었어요 · 사유: " + appReasonLabel);
        send(userId, "SUSPENDED", PushMessage.of("계정 이용이 정지되었어요",
                "사유: " + appReasonLabel + " · 진행 중인 주문은 그대로 이용할 수 있어요", null,
                "type", DATA_TYPE, "event", "SUSPENDED"));
    }

    public void released(Long userId) {
        inbox(userId, NotificationType.USER_ACCOUNT_RELEASED, "계정 이용 정지가 해제되었어요");
        send(userId, "RELEASED", PushMessage.of("계정 이용 정지가 해제되었어요", "지금부터 모든 기능을 다시 이용할 수 있어요", null,
                "type", DATA_TYPE, "event", "RELEASED"));
    }

    /** 알림함(C11) 행 — 푸시는 위에서 계정 상태 표식으로 따로 보낸다(앱이 상태를 다시 읽는다). */
    private void inbox(Long userId, NotificationType type, String title) {
        if (notificationPublisher == null) {
            return;
        }
        notificationPublisher.publish(NotificationCommand.toUser(userId, type).actorType(NotificationActorType.OPERATOR)
                .title(title).imageKind(NotificationImageKind.ICON)
                .targetType(NotificationTargetType.ACCOUNT_STATUS).skipPush(true).build());
    }

    private void send(Long userId, String event, PushMessage message) {
        try {
            PushResult total = newTransaction.execute(status -> sendAll(userId, message));
            log.info("소비자 계정 상태 알림 - userId: {}, event: {}, success: {}, failure: {}", userId, event,
                    total == null ? 0 : total.successCount(), total == null ? 0 : total.failureCount());
        } catch (RuntimeException e) {
            log.warn("소비자 계정 상태 알림 실패 - userId: {}, event: {}", userId, event, e);
        }
    }

    /** 기기가 여러 대일 수 있어 키셋으로 끊어 읽는다 — {@code PushPostNotificationSender}의 본인 통지 경로와 같은 방식. */
    private PushResult sendAll(Long userId, PushMessage message) {
        Pageable chunk = PageRequest.of(0, CHUNK_SIZE);
        PushResult total = PushResult.none();
        long afterId = 0L;
        while (true) {
            List<DeviceTokenRow> rows = deviceTokenRepository.findUserTokens(userId, afterId, chunk);
            if (rows.isEmpty()) {
                return total;
            }
            afterId = rows.get(rows.size() - 1).id();
            PushResult result = pushSender.send(rows.stream().map(DeviceTokenRow::token).toList(), message);
            deviceTokenService.purgeInvalid(result.invalidTokens());
            total = total.plus(new PushResult(result.successCount(), result.failureCount(), List.of()));
            if (rows.size() < CHUNK_SIZE) {
                return total;
            }
        }
    }
}
