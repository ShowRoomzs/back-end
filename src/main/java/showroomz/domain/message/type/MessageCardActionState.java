package showroomz.domain.message.type;

/**
 * 재발송 요청 카드의 액션 상태(36 설계 4-2).
 * {@code CLOSED}는 알림 전에 계약이 서명 단계를 벗어난 경우다 — 다시 보낼 안내가 없으므로 버튼을 내리지 않는다.
 */
public enum MessageCardActionState {
    PENDING,
    DONE,
    CLOSED
}
