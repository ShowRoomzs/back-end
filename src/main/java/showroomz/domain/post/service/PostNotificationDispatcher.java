package showroomz.domain.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import showroomz.domain.post.entity.PostNotificationLog;
import showroomz.domain.post.event.PostNotificationRegisteredEvent;
import showroomz.domain.post.repository.PostNotificationLogRepository;
import showroomz.global.config.NotificationAsyncConfig;

/**
 * 통지 이력이 적재되면 <b>커밋 이후에</b> 실제로 보낸다.
 *
 * <p>세 가지 성질을 이 클래스 하나가 책임진다.
 *
 * <p><b>커밋 이후</b>({@code AFTER_COMMIT}) — 게시 트랜잭션이 롤백되면 알림도 나가지 않아야 한다.
 * 트랜잭션 안에서 보내면 이미지 저장 실패로 롤백된 게시물의 알림이 팔로워 전원에게 남는다.
 *
 * <p><b>비동기</b> — 팔로워 수만 명의 발송은 FCM 왕복이 수십 초다. 게시 API가 그동안 응답을
 * 붙들고 있으면 크리에이터 화면이 멈춘 것처럼 보인다.
 *
 * <p><b>새 트랜잭션</b>({@code REQUIRES_NEW}) — 호출한 트랜잭션은 이미 끝났다. 이력의
 * {@code delivered}를 갱신하려면 여기서 트랜잭션을 새로 열어야 한다.
 *
 * <p>예외는 삼킨다. 알림 실패가 이미 커밋된 게시를 되돌릴 수는 없고, 무엇이 안 나갔는지는
 * {@code delivered=false}로 이력에 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostNotificationDispatcher {

    private final PostNotificationLogRepository postNotificationLogRepository;
    private final PostNotificationSender postNotificationSender;

    @Async(NotificationAsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(PostNotificationRegisteredEvent event) {
        try {
            PostNotificationLog notificationLog = postNotificationLogRepository
                    .findById(event.notificationLogId())
                    .orElse(null);
            if (notificationLog == null) {
                log.warn("발송할 통지 이력을 찾을 수 없다 - logId={}", event.notificationLogId());
                return;
            }

            if (postNotificationSender.send(notificationLog)) {
                notificationLog.markDelivered();
            }
        } catch (Exception e) {
            log.error("게시물 통지 발송 실패 - logId={}", event.notificationLogId(), e);
        }
    }
}
