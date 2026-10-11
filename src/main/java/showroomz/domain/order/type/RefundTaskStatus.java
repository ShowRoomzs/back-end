package showroomz.domain.order.type;

/**
 * 환불 큐 상태(1009 기획 수정본 2-4) — PG 자동 환불이 기본이 되면서 집행 중 · 실패가 생겼다.
 *
 * <pre>
 * PENDING ──▶ EXECUTING ──▶ DONE
 *    ▲            │
 *    └── FAILED ◀─┘     (PG 거절 · 결과 미확인 재시도 대기 — 자동 재시도 뒤 운영자 [재시도])
 * PENDING · FAILED ──▶ VOID  (집행할 필요가 없어짐)
 * </pre>
 */
public enum RefundTaskStatus {
    PENDING,
    /** PG 취소 호출 중 — 같은 결제에 둘 이상 동시에 있지 않다(결제 행 잠금으로 직렬화). */
    EXECUTING,
    DONE,
    /** PG 가 거절했거나 결과를 끝내 확인하지 못했다 — 어드민 환불 관리 「실패」 탭. */
    FAILED,
    VOID;

    /** 아직 돈이 나가지 않은 상태 — 소비자 앱 「환불 처리 중」 판정. */
    public boolean isOutstanding() {
        return this == PENDING || this == EXECUTING || this == FAILED;
    }
}
