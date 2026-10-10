package showroomz.domain.settlement.adjustment.port;

/**
 * 조정 협의 → 연결·소통 포트(44 이슈 스레드 설계서 0-4) — 그 쌍의 PAIR 연결에 {@code ThreadKind.SETTLEMENT_ADJUSTMENT} 스레드를
 * 연다. 구현은 메시지 도메인({@code MessageSettlementAdjustmentThreadGateway}). 끊긴 연결이면 503 GROUP_BUY_THREAD_UNAVAILABLE.
 * 호출자 트랜잭션에 합류한다 — 협의 행과 스레드가 함께 커밋되거나 함께 사라진다.
 */
public interface SettlementAdjustmentThreadPort {

    Long openAdjustmentThread(Long groupBuyId, Long marketId, Long creatorId);
}
