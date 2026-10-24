package showroomz.domain.member.user.service;

import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserBlockedAction;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 소비자 정지 중 차단 가드(§51-1 · 51-6 설계서 4절) — 「새로 벌이는 일」만 409 로 막는다. 화면의 「비활성 + 안내」는 앱의 일이고 서버는 그
 * 행위를 거부한다. 진행 중 주문 · 클레임 · 배송지 · 1:1 문의 · 되돌리기(좋아요 취소 · 언팔로우)는 가드가 없다 — 로그인도 그대로다.
 *
 * <p>정지 행을 읽지 않고 {@code users.status}만 본다 — 뜨거운 경로이고 상태는 정지 행에서 파생해 저장돼 있다. 기존 서비스에 생성자 의존을
 * 늘리지 않도록 정적 유틸이다(브랜드 {@code BrandSanctionGuard}와 같은 이유).
 */
public final class UserSanctionGuard {

    private UserSanctionGuard() {
    }

    public static boolean isSuspended(Users user) {
        return user != null && user.getStatus() == UserStatus.SUSPENDED;
    }

    public static void requireNotSuspended(Users user, UserBlockedAction action) {
        if (isSuspended(user)) {
            throw new BusinessException(ErrorCode.USER_SUSPENDED, action.message());
        }
    }
}
