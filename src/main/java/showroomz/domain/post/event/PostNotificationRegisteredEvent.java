package showroomz.domain.post.event;

/**
 * 통지 이력이 적재됐다는 사실. 실제 발송은 이 이벤트를 받아 <b>커밋 이후에</b> 일어난다.
 *
 * <p>엔티티가 아니라 <b>id만</b> 싣는 이유 — 수신 측은 다른 트랜잭션·다른 스레드에서 돌기 때문에
 * 여기서 넘긴 엔티티는 준영속 상태다. 지연 로딩이 터지거나, 더 나쁘게는 발송 중 바뀐 값이
 * 조용히 무시된다. id로 다시 읽으면 그 시점의 실제 상태를 본다.
 */
public record PostNotificationRegisteredEvent(Long notificationLogId) {
}
