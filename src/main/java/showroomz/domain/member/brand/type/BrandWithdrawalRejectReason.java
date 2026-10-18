package showroomz.domain.member.brand.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.member.common.type.ReasonType;

/**
 * 탈퇴 반려 사유(M3) — 신청 화면의 조건 축과 같다(§51-5 「무엇이 걸렸는지 회원이 바로 알아야 재신청이 성립한다」). 시안 5종에 체결 전
 * 계약을 더했다(52 설계서 13절 #5).
 */
@Getter
@RequiredArgsConstructor
public enum BrandWithdrawalRejectReason implements ReasonType {
    CLAIM_UNRESOLVED("반품·교환 처리 미완료"),
    UNSHIPPED_ORDERS("미배송 주문 잔존"),
    ACTIVE_GROUP_BUYS("진행중 공구 잔존"),
    UNSETTLED("미정산액 잔존"),
    CONTRACT_IN_PROGRESS("체결 전 계약 잔존"),
    OTHER("기타(직접 입력)");

    private final String label;

    public boolean isDetailRequired() {
        return this == OTHER;
    }
}
