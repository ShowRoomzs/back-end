package showroomz.domain.member.common.service;

import showroomz.domain.member.brand.type.SanctionReleaseReason;
import showroomz.domain.member.common.type.ReasonType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 회원 조치(정지 · 해제 · 탈퇴 반려)의 입력 규칙 한 벌(§51-2 · §51-4 · M1 · M3 · 정합 계획서 R1-6). 브랜드 · 인플루언서 조치가 같은 검증 ·
 * 같은 문장을 쓴다 — 에러 코드 값은 회원 유형별(클라이언트 계약)이라 호출자가 넘긴다.
 */
public final class MemberSanctionRules {

    private MemberSanctionRules() {
    }

    /**
     * 정형 사유 필수 · 기타면 상세 필수 — 다듬은 상세(없으면 null)를 돌려준다.
     *
     * @param detailRequired 기타인데 상세가 비었을 때의 코드(회원 유형별)
     */
    public static String requireDetail(ReasonType reason, String detail, ErrorCode detailRequired) {
        if (reason == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사유를 선택해 주세요.");
        }
        String normalized = trimToNull(detail);
        if (reason.isDetailRequired() && normalized == null) {
            throw new BusinessException(detailRequired);
        }
        return normalized;
    }

    /** 운영자가 고르는 해제 사유 — 비면 「소명 · 시정 확인」. 「탈퇴 승인으로 종결」은 시스템만 쓴다(400). */
    public static SanctionReleaseReason operatorReleaseReason(SanctionReleaseReason reason) {
        SanctionReleaseReason resolved = reason == null ? SanctionReleaseReason.RESOLVED : reason;
        if (resolved == SanctionReleaseReason.WITHDRAWN) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "탈퇴 종결은 운영자가 고를 수 있는 해제 사유가 아닙니다.");
        }
        return resolved;
    }

    /** 이력 · 카드 문장 — 「오정지 — 사업자 정보 재확인」. 상세가 없으면 라벨만. */
    public static String releaseText(SanctionReleaseReason reason, String note) {
        return note == null ? reason.getLabel() : reason.getLabel() + " — " + note;
    }

    /** 이력 문장 — 「반품·교환 처리 미완료 — 반품 1건 수취 확인 후 다시 신청해 주세요.」. */
    public static String reasonText(ReasonType reason, String detail) {
        return detail == null ? reason.getLabel() : reason.getLabel() + " — " + detail;
    }

    public static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
