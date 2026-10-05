package showroomz.domain.order.type;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/**
 * 파트너센터 반품·교환 목록 탭 7종(35 설계서 1-3) — 탭이 조건과 기본 정렬을 소유한다.
 * 전체 = 여섯 탭의 합이다 — 빠지는 상태가 없다. 결제 대기(PAYMENT_PENDING)는 아직 접수 전이라 어느 탭에도 없다.
 */
@Getter
public enum ClaimTab {

    ALL(EnumSet.complementOf(EnumSet.of(ClaimStatus.PAYMENT_PENDING))),
    COLLECT_WAIT(EnumSet.of(ClaimStatus.REQUESTED)),
    COLLECTING(EnumSet.of(ClaimStatus.COLLECTING)),
    INSPECTION(EnumSet.of(ClaimStatus.ARRIVED, ClaimStatus.RECEIVED)),
    RESHIP(EnumSet.of(ClaimStatus.RESHIP_READY)),
    REJECT_HOLD(EnumSet.of(ClaimStatus.REJECT_HOLD)),
    /** 브랜드가 할 일이 끝난 것 — 환불 대기 · 재발송 중 · 종결. */
    DONE(EnumSet.of(ClaimStatus.REFUND_PENDING, ClaimStatus.RESHIPPING, ClaimStatus.COMPLETED));

    private final Set<ClaimStatus> statuses;

    ClaimTab(Set<ClaimStatus> statuses) {
        this.statuses = statuses;
    }

    /** 그 상태가 속한 작업 탭 — 목록 행의 {@code stage}. 결제 대기는 탭이 없다. */
    public static ClaimTab stageOf(ClaimStatus status) {
        for (ClaimTab tab : values()) {
            if (tab != ALL && tab.statuses.contains(status)) {
                return tab;
            }
        }
        return null;
    }
}
