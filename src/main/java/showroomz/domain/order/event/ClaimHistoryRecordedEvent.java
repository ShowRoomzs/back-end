package showroomz.domain.order.event;

import showroomz.domain.order.type.ClaimEventType;

import java.time.LocalDateTime;

/**
 * 클레임 처리 이력이 한 줄 남을 때마다 발행된다(앱 클레임 설계서 6절) — 알림 모듈이 필요한 사건만 골라 받는다
 * (자동 취소 · 브랜드 도착 · 검수 통과/반려 · 재발송 출발/도착 · 환불 집행). 이 모듈은 알림을 보내지 않는다.
 *
 * <p>수신자는 {@code @TransactionalEventListener(AFTER_COMMIT)}로 받는다 — 커밋 전에 받으면 되돌려질 수 있는 전이를
 * 알리게 된다. 미결제 고지({@link ClaimEventType#STORAGE_NOTICE_SENT})는 「보낸 뒤의 기록」이라 다시 알리지 않는다.
 *
 * @param detail 이력의 부가 설명 — 송장 · 회차 등. 없으면 null
 */
public record ClaimHistoryRecordedEvent(Long claimId, ClaimEventType eventType, String detail,
                                        LocalDateTime occurredAt) {
}
