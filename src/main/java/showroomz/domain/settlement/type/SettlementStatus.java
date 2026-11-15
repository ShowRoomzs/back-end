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
    /** 수취자 화면에도 그대로 보인다(2026-11-10 — 이전에는 지급 완료로 접었다). */
    PAYOUT_FAILED("분배 실패", SettlementTone.DANGER);

    /** 확정 전 — 명세 다운로드가 닫히고(공통 결정 #15) 분배 · 증빙 블록이 비어 있다. */
    public static final Set<SettlementStatus> BEFORE_CONFIRM = EnumSet.of(REVIEWING, ADJUSTING);

    private final String label;
    private final SettlementTone tone;

    public boolean isConfirmed() {
        return !BEFORE_CONFIRM.contains(this);
    }

    /**
     * 파트너 · 스튜디오에 내리는 상태 — 저장 상태 그대로다. 분배 실패를 지급 완료로 접던 규칙은 없앴다(수정요청 B-14 · 2026-11-10 —
     * 실패한 지급이 「지급 완료」로 보였다).
     */
    public SettlementStatus toPartyStatus() {
        return this;
    }

    /** 파트너 · 스튜디오 목록 필터 — 고른 상태 그대로(분배 실패도 따로 고른다). 비었으면 전부. */
    public static Set<SettlementStatus> expandPartyFilter(Set<SettlementStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return EnumSet.allOf(SettlementStatus.class);
        }
        return EnumSet.copyOf(statuses);
    }
}
