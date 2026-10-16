package showroomz.api.admin.brand.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.market.type.MarketStatus;

import java.util.EnumSet;
import java.util.Set;

/** 브랜드 회원 목록 탭(52 설계서 9-1 · 목록 시안 미수령 — 최소 구성). 활성 탭은 휴면을 포함한다(0-2). */
@Getter
@RequiredArgsConstructor
public enum AdminBrandTab {
    ALL(EnumSet.allOf(MarketStatus.class)),
    ACTIVE(EnumSet.of(MarketStatus.ACTIVE, MarketStatus.DORMANT)),
    SUSPENDED(EnumSet.of(MarketStatus.SUSPENDED)),
    WITHDRAWN(EnumSet.of(MarketStatus.WITHDRAWN));

    private final Set<MarketStatus> statuses;
}
