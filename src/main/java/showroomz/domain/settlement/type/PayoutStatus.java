package showroomz.domain.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.EnumSet;
import java.util.Set;

/**
 * 수취자별 지급 상태(44 어드민 설계서 1-4).
 *
 * <pre>
 * 생성 WAITING ─(조정 요청)─▶ HELD
 *   └─(확정)─▶ SCHEDULED / BLOCKED(인플루언서 증빙 대기) / NOT_APPLICABLE(0원)
 * SCHEDULED ─(지시)─▶ REQUESTED ─▶ PAID / FAILED       FAILED ─(재분배)─▶ SCHEDULED
 * </pre>
 */
@Getter
@RequiredArgsConstructor
public enum PayoutStatus {
    WAITING("확인 기간 후 지급", SettlementTone.NEUTRAL),
    HELD("보류 중", SettlementTone.WARNING),
    BLOCKED("지급 보류", SettlementTone.WARNING),
    SCHEDULED("지급 예정", SettlementTone.NEUTRAL),
    REQUESTED("지급 처리 중", SettlementTone.INFO),
    PAID("지급 완료", SettlementTone.SUCCESS),
    FAILED("분배 실패", SettlementTone.DANGER),
    NOT_APPLICABLE("—", SettlementTone.NEUTRAL);

    /** 정산 종결 판정 — 전부 이 둘이면 정산은 PAID. */
    public static final Set<PayoutStatus> SETTLED = EnumSet.of(PAID, NOT_APPLICABLE);
    /** 확정 시 결정되는 상태 — 확정 전(WAITING · HELD)에서만 옮겨 간다. */
    public static final Set<PayoutStatus> BEFORE_CONFIRM = EnumSet.of(WAITING, HELD);

    private final String label;
    private final SettlementTone tone;

    public boolean isSettled() {
        return SETTLED.contains(this);
    }
}
