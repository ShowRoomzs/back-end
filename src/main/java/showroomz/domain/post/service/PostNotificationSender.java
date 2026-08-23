package showroomz.domain.post.service;

import showroomz.domain.post.entity.PostNotificationLog;

/**
 * 통지 발송 어댑터 — 이력 한 건을 실제 알림으로 바꾼다.
 *
 * <p>구현은 {@code PushPostNotificationSender}(FCM) 하나다. 인터페이스를 남겨 두는 이유는
 * <b>수신자 해석과 이력 적재를 갈라 두기 위해서다</b>. {@code PostNotificationService}는
 * "무슨 일이 있었는지"만 기록하고, 그것이 누구에게 어떤 채널로 가는지는 이쪽이 정한다.
 * 문자·이메일 채널이 붙거나 테스트에서 발송을 끊을 때 갈아끼우는 자리다.
 *
 * <p>호출은 {@code PostNotificationDispatcher}가 <b>커밋 이후·별도 스레드</b>에서 한다.
 * 구현체가 오래 걸려도 게시 API를 붙들지 않는다.
 */
public interface PostNotificationSender {

    /**
     * @return 한 명이라도 실제로 받았으면 true. 그 값이
     *         {@code post_notification_log.delivered}에 그대로 기록된다.
     *         FCM 미설정·발송 실패·푸시하지 않는 종류는 전부 false다.
     */
    boolean send(PostNotificationLog log);
}
