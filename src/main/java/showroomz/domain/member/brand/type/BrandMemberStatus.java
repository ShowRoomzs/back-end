package showroomz.domain.member.brand.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.market.type.MarketStatus;

/**
 * 어드민 회원 관리의 브랜드 상태 3종(52 설계서 0-2) — {@link MarketStatus}를 접는다. 회원 상세 배지 = 목록 상태 열이다.
 *
 * <p>{@code DORMANT}는 어느 코드도 세팅하지 않는다(셀러 휴면 전환 배치 없음) — 남아 있으면 활성으로 내린다. 탈퇴 <b>신청</b>은 상태가 아니다
 * (§51-0) — 승인 전까지 브랜드는 실제로 활성이다.
 */
@Getter
@RequiredArgsConstructor
public enum BrandMemberStatus {
    ACTIVE("활성", "SUCCESS"),
    SUSPENDED("정지", "DANGER"),
    WITHDRAWN("탈퇴", "NEUTRAL");

    private final String label;
    private final String tone;

    public static BrandMemberStatus of(MarketStatus status) {
        if (status == null) {
            return ACTIVE;
        }
        return switch (status) {
            case ACTIVE, DORMANT -> ACTIVE;
            case SUSPENDED -> SUSPENDED;
            case WITHDRAWN -> WITHDRAWN;
        };
    }
}
