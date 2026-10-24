package showroomz.domain.member.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.member.common.type.MemberType;
import showroomz.global.config.properties.MemberProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 정지 중 탈퇴 신청 허용 여부(1021 결정 노트 기-03 【잠정】 · 설계서 7-3 · 14-A · 55 계획서 S11) — 브랜드 · 인플루언서 · 소비자 공통.
 *
 * <ul>
 *   <li>{@code member.sanction.allow-withdrawal-while-suspended}(기본 true) — 켜져 있으면 정지는 탈퇴 신청과 무관하다(남은 공구 · 계약 ·
 *   주문 · 정산 조건만 막는다). 끄면 정지 중인 회원의 신청이 409(유형별 정지 코드)다.</li>
 *   <li>신청 경로(브랜드 · 인플루언서 신청 · 소비자 탈퇴)와 탈퇴 정보 화면(파트너 · 스튜디오 계정 탭 · 앱 탈퇴 화면)이 같은 판정 · 같은 문장을
 *   읽는다 — 화면이 「신청 가능」으로 그린 뒤 409 가 나지 않게.</li>
 *   <li>이미 접수된 신청은 건드리지 않는다 — 심사 중 정지돼도 신청 유지 · 승인 시 정지 종결(7-3). 어드민 승인 재검증은 이 판정을 보지 않는다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SuspendedWithdrawalPolicy {

    private final MemberProperties properties;

    /** 정지 때문에 탈퇴를 신청할 수 없는가 — 정지 중 ∧ 설정 false. */
    public boolean blocks(boolean suspended) {
        return suspended && !properties.getSanction().isAllowWithdrawalWhileSuspended();
    }

    /** 탈퇴 화면의 비활성 사유 — 막히지 않으면 null. 신청 409 와 같은 문장이다. */
    public String blockedReason(MemberType type, boolean suspended) {
        return blocks(suspended) ? message(type) : null;
    }

    /** 신청 경로 — 막히면 409. 코드는 회원 유형별(클라이언트 계약) 정지 코드다. */
    public void requireCanRequest(MemberType type, boolean suspended) {
        if (blocks(suspended)) {
            throw new BusinessException(errorCode(type), message(type));
        }
    }

    private static ErrorCode errorCode(MemberType type) {
        return switch (type) {
            case BRAND -> ErrorCode.BRAND_SUSPENDED;
            case CREATOR -> ErrorCode.CREATOR_SUSPENDED;
            case CONSUMER -> ErrorCode.USER_SUSPENDED;
        };
    }

    /** 유형별 정지 가드와 같은 말투 — 브랜드 {@code BrandBlockedAction} · 인플루언서 {@code CreatorSanctionGuard} · 소비자 {@code UserBlockedAction}. */
    private static String message(MemberType type) {
        return switch (type) {
            case BRAND -> "계정 이용 정지 중에는 탈퇴를 신청할 수 없습니다. 운영팀 소통 스레드로 문의해 주세요.";
            case CREATOR -> "계정 이용 정지 중에는 탈퇴를 신청할 수 없습니다.";
            case CONSUMER -> "이용 정지 중에는 탈퇴할 수 없어요.";
        };
    }
}
