package showroomz.global.payment.portone;

/**
 * 취소 호출 결과. 「이미 취소됨」은 성공으로 수렴시킨다(5-6). {@link Outcome#PENDING}은 PG가 접수만 한 상태라
 * 결과를 모르는 것과 같다 — 호출자는 상태를 바꾸지 않고 수렴 단계에 맡긴다.
 */
public record PortOneCancelResult(Outcome outcome, String pgCancellationId, String rawJson) {

    public enum Outcome {
        SUCCEEDED,
        ALREADY_CANCELLED,
        PENDING
    }

    public boolean isCancelled() {
        return outcome == Outcome.SUCCEEDED || outcome == Outcome.ALREADY_CANCELLED;
    }
}
