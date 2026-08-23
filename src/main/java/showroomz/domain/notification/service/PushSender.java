package showroomz.domain.notification.service;

import java.util.List;

/**
 * 푸시 채널 어댑터.
 *
 * <p>구현은 두 개다 — 실제로 보내는 {@code FcmPushSender}와, 자격 증명이 없을 때 로그만 남기는
 * {@link LoggingPushSender}. 후자는 <b>성공한 척하지 않는다.</b> 성공 0건을 돌려주므로
 * {@code post_notification_log.delivered}가 false로 남고, 무엇이 나가지 않았는지 이력에서 그대로 읽힌다.
 */
public interface PushSender {

    /**
     * @param tokens 한 번에 보낼 토큰들. 채널의 배치 상한(FCM 500)은 구현이 알아서 나눈다.
     */
    PushResult send(List<String> tokens, PushMessage message);
}
