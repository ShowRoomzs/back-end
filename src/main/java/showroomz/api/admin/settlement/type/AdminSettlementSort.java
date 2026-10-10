package showroomz.api.admin.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.settlement.type.SettlementAdminSort;

/** 07a 전체 탭 정렬(44 어드민 설계서 7-1) — 상태 탭은 탭별 고정 정렬이라 이 값을 무시한다. */
@Getter
@RequiredArgsConstructor
public enum AdminSettlementSort {
    /** 일정 최신순(기본). */
    SCHEDULE_DESC(SettlementAdminSort.SCHEDULE_DESC),
    /** 확정 거래액순. */
    SALES_DESC(SettlementAdminSort.SALES_DESC);

    private final SettlementAdminSort domainSort;
}
