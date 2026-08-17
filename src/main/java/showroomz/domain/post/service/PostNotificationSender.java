package showroomz.domain.post.service;

import showroomz.domain.post.entity.PostNotificationLog;

/**
 * 통지 발송 어댑터.
 *
 * <p>이 프로젝트에는 아직 발송 인프라가 없다({@code NotificationSetting}은 수신 설정값일 뿐이다).
 * 그래서 <b>이력은 지금 남기고 발송은 스텁</b>으로 둔다 — 이력은 소급해서 만들 수 없지만
 * 발송은 인프라가 생긴 뒤 이 인터페이스의 구현체를 바꿔 끼우면 된다.
 *
 * <p>어드민 회원관리 계획의 {@code IdentityVerificationService} 스텁과 같은 경계다.
 */
public interface PostNotificationSender {

    /**
     * @return 실제로 전달됐으면 true. 스텁은 언제나 false를 돌려주고,
     *         그 값이 {@code post_notification_log.delivered}에 그대로 기록된다.
     */
    boolean send(PostNotificationLog log);
}
