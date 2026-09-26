package showroomz.domain.groupbuy.type;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/**
 * 스튜디오 목록 탭 ↔ 상태 매핑 — §29-2의 6종(31 설계 1-1).
 *
 * <p>파트너 {@link GroupBuyTab}과 <b>공유하지 않는다</b>. 지금은 매핑이 같지만 시안 A1은 탭이 5개(중단이 「종료·정산」에
 * 포함)라 스튜디오 쪽으로 확정되면 두 서피스가 갈린다(31 설계 10-1 #1). 그때 이 enum의 매핑만 바꾼다.
 */
@Getter
public enum CreatorGroupBuyTab {

    ALL(EnumSet.allOf(GroupBuyStatus.class)),
    PREPARING(EnumSet.of(GroupBuyStatus.PREPARING)),
    READY(EnumSet.of(GroupBuyStatus.READY)),
    /** 중단 예정은 탭에 없고 진행중 탭에 배지로만 들어간다. */
    IN_PROGRESS(EnumSet.of(GroupBuyStatus.IN_PROGRESS, GroupBuyStatus.SUSPENSION_SCHEDULED)),
    ENDED(EnumSet.of(GroupBuyStatus.ENDED, GroupBuyStatus.SETTLED)),
    SUSPENDED(EnumSet.of(GroupBuyStatus.SUSPENDED));

    private final Set<GroupBuyStatus> statuses;

    CreatorGroupBuyTab(Set<GroupBuyStatus> statuses) {
        this.statuses = statuses;
    }
}
