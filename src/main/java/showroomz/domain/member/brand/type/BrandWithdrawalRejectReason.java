package showroomz.domain.member.brand.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.member.common.type.ReasonType;

/**
 * 탈퇴 반려 사유(M3) — 신청 화면의 조건 4항목과 같은 축이다(§51-5 「무엇이 걸렸는지 회원이 바로 알아야 재신청이 성립한다」 · 1021 기-04).
 * 선언 순서가 선택지 순서다(ui-admin-15 rev.7 M3). 옛 값 반품·교환 미완료 · 미배송 주문은 「미종결 주문」으로 합쳤다(55 계획서 M7 · V215).
 */
@Getter
@RequiredArgsConstructor
public enum BrandWithdrawalRejectReason implements ReasonType {
    ACTIVE_GROUP_BUYS("진행 중 공구 잔존"),
    CONTRACT_IN_PROGRESS("체결 전 계약 잔존"),
    UNCLOSED_ORDERS("미종결 주문 잔존"),
    UNSETTLED("진행 중 정산 잔존"),
    OTHER("기타(직접 입력)");

    private final String label;

    public boolean isDetailRequired() {
        return this == OTHER;
    }
}
