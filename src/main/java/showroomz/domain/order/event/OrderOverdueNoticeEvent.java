package showroomz.domain.order.event;

import java.time.LocalDateTime;

/**
 * 처리 지연 자동 알림 1회(1009 기획 수정본 8-4 · 결정 「독촉 → 자동 알림」) — 알림 모듈이 커밋 뒤에 받아 브랜드에 보낸다.
 * 이 모듈은 알림을 보내지 않고 횟수와 시각만 센다.
 *
 * @param kind     SHIP_OVERDUE(발송 기한 경과 · targetId = 하위주문) · INSPECT_OVERDUE(검수 기한 경과 · targetId = 클레임)
 * @param sequence 몇 번째 알림인가 — 3회 무응답이면 어드민 대행이 열린다
 */
public record OrderOverdueNoticeEvent(Kind kind, Long targetId, Long marketId, int sequence, LocalDateTime notifiedAt) {

    public enum Kind { SHIP_OVERDUE, INSPECT_OVERDUE }
}
