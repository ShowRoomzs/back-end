package showroomz.domain.member.creator.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.member.common.type.ReasonType;

/**
 * 탈퇴 반려 사유(M3) — 조건과 같은 축으로 남긴다(신청 이후 상황이 바뀐 건을 되돌리기 위함 · §51-5). 기타만 안내 필수. 선언 순서가 선택지
 * 순서다(ui-admin-18 rev.2 M3). 옛 값 「미배송 주문 처리 미완료」는 조건 ①(끝나지 않은 공구 · 미종결 주문 포함)으로 합쳤다(55 계획서 M7 · V215).
 */
@Getter
@RequiredArgsConstructor
public enum CreatorWithdrawalRejectReason implements ReasonType {
    GROUP_BUY_REMAINING("끝나지 않은 공구 남음"),
    UNFINISHED_CONTRACTS("끝나지 않은 계약 남음"),
    SETTLEMENT_IN_PROGRESS("미정산 남음"),
    DISPUTE_IN_PROGRESS("분쟁·신고 처리 중"),
    OTHER("기타(직접 입력)");

    private final String label;

    public boolean isDetailRequired() {
        return this == OTHER;
    }
}
