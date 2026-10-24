package showroomz.domain.member.brand.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.member.brand.type.BrandBlockedAction;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 탈퇴 신청 심사 중 잠금(51 설계서 7절 · 시안 4-I) — 새 공구로 이어지는 계약 작성 · 복제 · 검토 요청과 로그인 정보(비밀번호 · 이메일) 변경을
 * 409 {@code BRAND_WITHDRAWAL_PENDING} 으로 막는다. 회원 상태를 바꾸지 않고 열린 신청 행으로 판정하므로 취소 · 반려되면 바로 풀린다.
 *
 * <p>정지 가드({@link BrandSanctionGuard})와 달리 DB 조회가 필요해 따로 둔다. 작성 중 계약 수정 · 삭제 · 검토 요청 취소 · 주문 · 클레임 ·
 * 정산 조정 응답 · 정산 계좌 변경 요청은 막지 않는다 — 탈퇴 조건을 정리하는 길이다(7-2).
 */
@Component
@RequiredArgsConstructor
public class BrandWithdrawalGuard {

    private final BrandSanctionReader sanctionReader;

    public boolean hasPending(Long marketId) {
        return sanctionReader.hasPendingWithdrawal(marketId);
    }

    public void requireNoPending(Long marketId, BrandBlockedAction action) {
        if (hasPending(marketId)) {
            throw new BusinessException(ErrorCode.BRAND_WITHDRAWAL_PENDING, action.withdrawalPendingMessage());
        }
    }
}
