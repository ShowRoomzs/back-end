package showroomz.domain.member.brand.service;

import org.springframework.stereotype.Component;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.brand.type.BrandBlockedAction;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 정지 중 차단 가드(§51-1 · 52 설계서 7절) — 「새로 벌이는 일」만 409 로 막는다. 화면의 「비활성 + 안내」는 FE 의 일이고 서버는 그 행위를
 * 거부한다. 주문 처리 · 상품 문의 답변 · 기존 상품 수정 · 기본정보(계좌 포함) · 스레드 · 정산은 가드가 없다 — 로그인도 그대로다.
 *
 * <p>인플루언서 {@code CreatorSanctionGuard}와 같은 모양(주입 컴포넌트)이다(정합 계획서 R1-7). 정지 행을 읽지 않고 {@code market.status}
 * 만 본다 — 뜨거운 경로이고 상태는 정지 행에서 파생해 저장돼 있다.
 */
@Component
public class BrandSanctionGuard {

    public boolean isSuspended(Market market) {
        return market != null && market.isSuspended();
    }

    public void requireNotSuspended(Market market, BrandBlockedAction action) {
        if (isSuspended(market)) {
            throw new BusinessException(ErrorCode.BRAND_SUSPENDED, action.message());
        }
    }
}
