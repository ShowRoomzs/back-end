package showroomz.domain.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.EnumSet;
import java.util.Set;

/**
 * 정산 상태 5종(44 어드민 설계서 0-4). 「분배 실패」는 수취자 행({@code settlement_payout})에서 파생해 저장한다 —
 * 하나라도 FAILED 면 PAYOUT_FAILED, 전부 PAID(또는 NOT_APPLICABLE)면 PAID. 폐기된 상태(정산 대기 · 확정 대기 · 정산 확정 ·
 * 정산 보류 · 이체 완료)는 여기 없다(§41-2).
 */
@Getter
@RequiredArgsConstructor
public enum SettlementStatus {
    REVIEWING("정산 확인 중", SettlementTone.INFO),
    ADJUSTING("조정 협의", SettlementTone.WARNING),
    PAYOUT_SCHEDULED("지급 예정", SettlementTone.NEUTRAL),
    PAID("지급 완료", SettlementTone.SUCCESS),
    /** 어드민 전용 — 수취자 화면은 {@link #toPartyStatus()}로 PAID 에 접는다(§41-5 · §46 A-4). */
    PAYOUT_FAILED("분배 실패", SettlementTone.DANGER);

    /** 확정 전 — 명세 다운로드가 닫히고(공통 결정 #15) 분배 · 증빙 블록이 비어 있다. */
    public static final Set<SettlementStatus> BEFORE_CONFIRM = EnumSet.of(REVIEWING, ADJUSTING);

    private final String label;
    private final SettlementTone tone;

    public boolean isConfirmed() {
        return !BEFORE_CONFIRM.contains(this);
    }

    /** 파트너 · 스튜디오에 내리는 상태 — 분배 실패는 지급 완료로 접는다. 수취자별 사실은 {@code payouts[].status}가 따로 말한다. */
    public SettlementStatus toPartyStatus() {
        return this == PAYOUT_FAILED ? PAID : this;
    }

    /** 파트너 · 스튜디오 목록 필터 — 접힌 상태로 받는다. PAID 를 고르면 PAYOUT_FAILED 도 함께 본다. */
    public static Set<SettlementStatus> expandPartyFilter(Set<SettlementStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return EnumSet.allOf(SettlementStatus.class);
        }
        Set<SettlementStatus> expanded = EnumSet.noneOf(SettlementStatus.class);
        for (SettlementStatus status : statuses) {
            expanded.add(status);
            if (status == PAID) {
                expanded.add(PAYOUT_FAILED);
            }
        }
        return expanded;
    }
}
