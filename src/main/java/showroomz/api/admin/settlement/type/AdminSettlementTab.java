package showroomz.api.admin.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.settlement.type.SettlementAdminSort;
import showroomz.domain.settlement.type.SettlementStatus;

import java.util.EnumSet;
import java.util.Set;

/**
 * 07a 탭(44 어드민 설계서 7-1) — 정산 탭 4개는 정산 1건이 행이고, {@link #EVIDENCE} · {@link #CLAWBACK}은 행의 단위가 다르다
 * (증빙 문서 1건 · 차감 1건). 상태 탭은 정렬이 고정이다.
 */
@Getter
@RequiredArgsConstructor
public enum AdminSettlementTab {
    ALL(EnumSet.allOf(SettlementStatus.class), null),
    REVIEWING(EnumSet.of(SettlementStatus.REVIEWING), SettlementAdminSort.REVIEW_DUE_ASC),
    ADJUSTING(EnumSet.of(SettlementStatus.ADJUSTING), SettlementAdminSort.AGREEMENT_DUE_ASC),
    PAYOUT_FAILED(EnumSet.of(SettlementStatus.PAYOUT_FAILED), SettlementAdminSort.FAILED_AT_ASC),
    EVIDENCE(Set.of(), null),
    CLAWBACK(Set.of(), null);

    /** 정산 탭의 상태 — 증빙 · 차감 탭은 비어 있다. */
    private final Set<SettlementStatus> statuses;
    /** 탭별 고정 정렬 — null 이면 사용자가 고른다(전체 탭). */
    private final SettlementAdminSort fixedSort;

    public boolean isSettlementRow() {
        return this != EVIDENCE && this != CLAWBACK;
    }
}
