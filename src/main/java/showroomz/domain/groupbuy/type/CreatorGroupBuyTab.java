package showroomz.domain.groupbuy.type;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/**
 * 스튜디오 목록 탭 ↔ 상태 매핑 — 시안 A1의 5탭(31 설계 1-1 · 10-1 #1).
 *
 * <p>파트너 {@link GroupBuyTab}과 <b>공유하지 않는다</b>. 파트너는 §29-2의 6탭(중단 분리)이고, 스튜디오는 시안 A1대로
 * 중단을 「종료·정산」에 넣어 5탭이다 — 별도 중단 탭을 두면 5탭을 그리는 화면에서 중단 건이 전체 탭에만 보인다.
 */
@Getter
public enum CreatorGroupBuyTab {

    ALL(EnumSet.allOf(GroupBuyStatus.class)),
    PREPARING(EnumSet.of(GroupBuyStatus.PREPARING)),
    READY(EnumSet.of(GroupBuyStatus.READY)),
    /** 중단 예정은 탭에 없고 진행중 탭에 배지로만 들어간다. */
    IN_PROGRESS(EnumSet.of(GroupBuyStatus.IN_PROGRESS, GroupBuyStatus.SUSPENSION_SCHEDULED)),
    /** 종료·정산 — 중단도 여기 들어간다(종결 3종 전부). */
    ENDED(EnumSet.of(GroupBuyStatus.ENDED, GroupBuyStatus.SETTLED, GroupBuyStatus.SUSPENDED));

    private final Set<GroupBuyStatus> statuses;

    CreatorGroupBuyTab(Set<GroupBuyStatus> statuses) {
        this.statuses = statuses;
    }
}
