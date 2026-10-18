package showroomz.domain.member.brand.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.member.common.type.AdminHistoryEventType;

/**
 * 브랜드 처리 이력(52 설계서 0-6). 앞 9종은 {@code brand_admin_history}에 저장하고, 뒤 5종은 다른 테이블의 사실(가입 · 입점 승인 ·
 * 변경 요청)을 읽을 때 합친다 — 다시 쓰면 백필과 이중 기록이 생긴다.
 */
@Getter
@RequiredArgsConstructor
public enum BrandAdminEventType implements AdminHistoryEventType {
    SUSPENDED("정지", "DANGER", true),
    SUSPENSION_RELEASED("정지 해제", "SUCCESS", true),
    WITHDRAWAL_REQUESTED("탈퇴 신청 접수", "WARNING", true),
    WITHDRAWAL_CANCELED("탈퇴 신청 취소", "NEUTRAL", true),
    WITHDRAWAL_APPROVED("탈퇴 승인 · 계정 해지", "NEUTRAL", true),
    WITHDRAWAL_REJECTED("탈퇴 신청 반려", "INFO", true),
    MEMO_CREATED("관리자 메모 기입", "NEUTRAL", true),
    MEMO_UPDATED("관리자 메모 수정", "NEUTRAL", true),
    MEMO_DELETED("관리자 메모 삭제", "NEUTRAL", true),

    SIGNED_UP("가입 신청", "INFO", false),
    ONBOARDING_APPROVED("입점 승인", "SUCCESS", false),
    CHANGE_REQUEST_RECEIVED("변경 요청 접수", "INFO", false),
    CHANGE_REQUEST_APPROVED("변경 요청 승인", "SUCCESS", false),
    CHANGE_REQUEST_REJECTED("변경 요청 반려", "WARNING", false);

    private final String label;
    private final String tone;
    /** 이 모듈이 저장하는 사건 — false 는 읽기 전용 파생 행. */
    private final boolean stored;
}
