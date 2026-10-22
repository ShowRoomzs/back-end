package showroomz.domain.order.type;

/**
 * 운임 정산 방향 — 브랜드 정산 「± 운임 정산」의 부호(1021 설계서 6-1).
 * CHARGE_CONSUMER(소비자 부담 · 브랜드 +) · REFUND_CONSUMER(소비자 환급 · 브랜드 −).
 */
public enum FreightAdjustmentDirection {
    CHARGE_CONSUMER,
    REFUND_CONSUMER;

    public long signed(long amount) {
        return this == CHARGE_CONSUMER ? amount : -amount;
    }
}
