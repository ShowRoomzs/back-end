package showroomz.domain.member.creator.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.member.common.type.AdminHistoryEventType;

/**
 * 인플루언서 처리 이력(53 설계서 0-7 · 구현 메모 12절). 저장 사건은 {@code creator_admin_history}에 쓰고, 파생 2종(가입 신청 · 입점 승인)은
 * {@code creator_application}을 읽을 때 합친다 — 다시 쓰면 백필과 이중 기록이 생긴다(브랜드 52 설계서 0-6 과 같은 결정).
 *
 * <p>사업자 구분 변경 승인 · 반려는 {@code brand_change_request}(인플루언서 요청)에서 읽어 합친다 — 저장하지 않는다(정합 계획서 R1-8 ·
 * 브랜드의 변경 요청 사건과 같은 결정).
 */
@Getter
@RequiredArgsConstructor
public enum CreatorAdminEventType implements AdminHistoryEventType {
    SUSPENDED("정지", "DANGER", true),
    SUSPENSION_RELEASED("정지 해제", "SUCCESS", true),
    WITHDRAWAL_REQUESTED("탈퇴 신청 접수", "WARNING", true),
    WITHDRAWAL_CANCELED("탈퇴 신청 취소", "NEUTRAL", true),
    WITHDRAWAL_APPROVED("탈퇴 승인 · 스튜디오 자격 해지", "NEUTRAL", true),
    WITHDRAWAL_REJECTED("탈퇴 신청 반려", "INFO", true),
    MEMO_CREATED("관리자 메모 기입", "NEUTRAL", true),
    MEMO_UPDATED("관리자 메모 수정", "NEUTRAL", true),
    MEMO_DELETED("관리자 메모 삭제", "NEUTRAL", true),
    PRIVACY_UNMASKED("본인확인 정보 마스킹 해제", "WARNING", true),
    SETTLEMENT_ACCOUNT_CHANGED("정산 계좌 변경", "WARNING", true),
    CONNECTIONS_ENDED("연결 일괄 종료", "NEUTRAL", true),
    BUSINESS_TYPE_CHANGE_APPROVED("사업자 구분 변경 승인", "SUCCESS", false),
    BUSINESS_TYPE_CHANGE_REJECTED("사업자 구분 변경 반려", "WARNING", false),

    SIGNED_UP("가입 신청", "INFO", false),
    ONBOARDING_APPROVED("입점 승인", "SUCCESS", false);

    private final String label;
    private final String tone;
    /** 이 모듈이 저장하는 사건 — false 는 읽기 전용 파생 행. */
    private final boolean stored;
}
