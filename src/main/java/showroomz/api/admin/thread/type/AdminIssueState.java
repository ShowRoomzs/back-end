package showroomz.api.admin.thread.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.settlement.adjustment.type.AdjustmentStatus;

import java.util.List;

/** 이슈 탭 seg(44 이슈 스레드 설계서 4-1) — 진행 중 · 종결(합의 ∪ 기한 만료). */
@Getter
@RequiredArgsConstructor
public enum AdminIssueState {
    OPEN(List.of(AdjustmentStatus.OPEN)),
    CLOSED(List.of(AdjustmentStatus.AGREED, AdjustmentStatus.EXPIRED));

    private final List<AdjustmentStatus> statuses;
}
