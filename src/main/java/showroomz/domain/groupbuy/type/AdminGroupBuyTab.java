package showroomz.domain.groupbuy.type;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/**
 * 어드민 목록 탭 8종(32 설계 2-2). 파트너·스튜디오와 구성이 달라 따로 둔다.
 *
 * <p>{@link #ACTION_REQUIRED}는 상태 탭이 아니라 <b>술어 탭</b>이다 — 상태 집합은 전체이고 조치 큐 판정식이 거른다.
 * {@link #ENDED}와 {@link #SETTLED}를 가른다 — 정산 지연 감시는 종료 탭에서 본다(§32-6).
 */
@Getter
public enum AdminGroupBuyTab {

    ALL(EnumSet.allOf(GroupBuyStatus.class)),
    ACTION_REQUIRED(EnumSet.allOf(GroupBuyStatus.class)),
    PREPARING(EnumSet.of(GroupBuyStatus.PREPARING)),
    READY(EnumSet.of(GroupBuyStatus.READY)),
    /** 중단 예정은 진행중 탭에 배지로만 들어간다(§29-2). */
    IN_PROGRESS(EnumSet.of(GroupBuyStatus.IN_PROGRESS, GroupBuyStatus.SUSPENSION_SCHEDULED)),
    ENDED(EnumSet.of(GroupBuyStatus.ENDED)),
    SETTLED(EnumSet.of(GroupBuyStatus.SETTLED)),
    SUSPENDED(EnumSet.of(GroupBuyStatus.SUSPENDED));

    private final Set<GroupBuyStatus> statuses;

    AdminGroupBuyTab(Set<GroupBuyStatus> statuses) {
        this.statuses = statuses;
    }

    public boolean isActionRequiredOnly() {
        return this == ACTION_REQUIRED;
    }
}
