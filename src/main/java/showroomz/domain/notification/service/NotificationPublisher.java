package showroomz.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.service.AfterCommit;

import java.util.Collection;

/**
 * 알림 발행 — 모든 발생 지점이 여기만 부른다(1017 설계서 1-0 ⑧). <b>커밋 뒤에</b> 새 트랜잭션으로 쓴다: 알림 한 건의 실패(중복 키 ·
 * 푸시 오류)가 이미 끝난 조치를 되돌리면 안 되고, 롤백된 조치의 알림이 남아서도 안 된다. 트랜잭션 밖에서 부르면 바로 쓴다.
 */
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private final NotificationWriter writer;

    public void publish(NotificationCommand command) {
        if (command == null || command.recipientId() == null || command.type() == null) {
            return;
        }
        AfterCommit.run(() -> writer.writeSafely(command));
    }

    public void publishAll(Collection<NotificationCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        AfterCommit.run(() -> commands.forEach(writer::writeSafely));
    }
}
