package showroomz.domain.member.user.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 소비자 처리 이력(51-6 설계서 7-2). 저장 사건은 {@code user_admin_history}에 쓰고, 파생 4종은 읽을 때 합친다 — 가입({@code users}) ·
 * 탈퇴 · 재가입({@code user_status_history}) · 소명 접수({@code one_to_one_inquiry.sanction_id}). 다시 쓰면 이중 기록이 생긴다.
 */
@Getter
@RequiredArgsConstructor
public enum UserAdminEventType {
    SUSPENDED("계정 정지", "DANGER", true),
    SUSPENSION_RELEASED("정지 해제", "INFO", true),
    MEMO_CREATED("관리자 메모 기입", "NEUTRAL", true),
    MEMO_UPDATED("관리자 메모 수정", "NEUTRAL", true),
    MEMO_DELETED("관리자 메모 삭제", "NEUTRAL", true),

    SIGNED_UP("가입", "NEUTRAL", false),
    WITHDRAWN("탈퇴", "NEUTRAL", false),
    REJOINED("재가입", "NEUTRAL", false),
    APPEAL_RECEIVED("정지 소명 문의 접수", "NEUTRAL", false);

    private final String label;
    private final String tone;
    /** 이 모듈이 저장하는 사건 — false 는 읽기 전용 파생 행. */
    private final boolean stored;
}
