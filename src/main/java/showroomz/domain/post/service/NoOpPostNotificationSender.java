package showroomz.domain.post.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import showroomz.domain.post.entity.PostNotificationLog;

/**
 * 발송 인프라가 들어오기 전까지의 스텁. 로그만 남기고 아무것도 보내지 않는다.
 *
 * <p>조용히 성공한 척하지 않는 것이 핵심이다 — {@code false}를 돌려주므로 이력에는
 * "통지 대상이었으나 전달되지 않음"이 남고, 나중에 발송 인프라가 붙었을 때 무엇이 나가지
 * 않았는지 이 테이블에서 그대로 읽힌다.
 */
@Slf4j
@Component
public class NoOpPostNotificationSender implements PostNotificationSender {

    @Override
    public boolean send(PostNotificationLog notificationLog) {
        log.info("게시물 통지 이력만 적재(발송 인프라 미도입) - postId={}, creatorId={}, event={}",
                notificationLog.getPostId(), notificationLog.getCreatorId(), notificationLog.getEventType());
        return false;
    }
}
