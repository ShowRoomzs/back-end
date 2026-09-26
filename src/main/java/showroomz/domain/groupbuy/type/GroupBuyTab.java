package showroomz.domain.groupbuy.type;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/**
 * 파트너 목록 탭 ↔ 상태 매핑 — 서버가 소유한다(설계서 1-3).
 *
 * <p>중단 예정은 탭에 없고 진행중 탭에 배지로만 들어간다(§29-2). 목록 배지는 묶음이 아니라 개별 값 그대로다.
 */
@Getter
public enum GroupBuyTab {

    ALL(EnumSet.allOf(GroupBuyStatus.class)),
    PREPARING(EnumSet.of(GroupBuyStatus.PREPARING)),
    READY(EnumSet.of(GroupBuyStatus.READY)),
    IN_PROGRESS(EnumSet.of(GroupBuyStatus.IN_PROGRESS, GroupBuyStatus.SUSPENSION_SCHEDULED)),
    /** 시안 탭 라벨 「종료·정산」. */
    ENDED(EnumSet.of(GroupBuyStatus.ENDED, GroupBuyStatus.SETTLED)),
    /** 정상 종료와 성격이 달라 탭을 분리한다. */
    SUSPENDED(EnumSet.of(GroupBuyStatus.SUSPENDED));

    private final Set<GroupBuyStatus> statuses;

    GroupBuyTab(Set<GroupBuyStatus> statuses) {
        this.statuses = statuses;
    }
}
