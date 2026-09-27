package showroomz.domain.payment.type;

/**
 * 웹훅 이벤트 처리 결과(5-7). 유니크는 「같은 webhook-id를 두 번 <b>처리</b>하지 않는다」이지 「두 번 받지 않는다」가 아니다 —
 * RECEIVED·FAILED로 남은 재전송은 다시 처리하고 attempts를 올린다.
 */
public enum WebhookEventResult {
    RECEIVED,
    PROCESSED,
    IGNORED,
    FAILED
}
