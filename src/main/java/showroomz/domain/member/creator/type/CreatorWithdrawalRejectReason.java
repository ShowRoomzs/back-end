package showroomz.domain.member.creator.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.member.common.type.ReasonType;

/** 탈퇴 반려 사유(M3) — 조건과 같은 축으로 남긴다(신청 이후 상황이 바뀐 건을 되돌리기 위함 · §51-5). 기타만 안내 필수. */
@Getter
@RequiredArgsConstructor
public enum CreatorWithdrawalRejectReason implements ReasonType {
    GROUP_BUY_REMAINING("진행중 공구가 남아 있음"),
    SETTLEMENT_IN_PROGRESS("진행 중 정산 남음"),
    UNDELIVERED_ORDER("미배송 주문 처리 미완료"),
    DISPUTE_IN_PROGRESS("분쟁·신고 처리 중"),
    OTHER("기타(직접 입력)");

    private final String label;

    public boolean isDetailRequired() {
        return this == OTHER;
    }
}
