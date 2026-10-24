package showroomz.api.admin.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 회원 목록 행의 표시 상태(ui-admin-14 · 16 「상태 색 매핑」 · 55 계획서 M1 · M2) — 회원 상태에 탈퇴 승인 대기를 얹은 값이다. 대기는 회원 상태가
 * 아니라 열린 신청 행이라(§51-0), 정지 중에 신청한 행도 대기로 보인다(D13-1 · 원래 상태는 행의 회원 상태 필드에 그대로 있다).
 */
@Getter
@RequiredArgsConstructor
public enum AdminMemberListStatus {
    ACTIVE("활성", "SUCCESS"),
    SUSPENDED("정지", "DANGER"),
    WITHDRAWAL_PENDING("탈퇴 승인 대기", "WARNING"),
    WITHDRAWN("탈퇴", "NEUTRAL");

    private final String label;
    private final String tone;

    public static AdminMemberListStatus of(boolean withdrawn, boolean suspended, boolean withdrawalPending) {
        if (withdrawn) {
            return WITHDRAWN;
        }
        if (withdrawalPending) {
            return WITHDRAWAL_PENDING;
        }
        return suspended ? SUSPENDED : ACTIVE;
    }
}
