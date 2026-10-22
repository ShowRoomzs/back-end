package showroomz.api.app.user.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * C15-4 회원 탈퇴 2단계 진입 데이터.
 *
 * <p>차단 여부(진행 중 주문)와 최종 확인 모달에 들어갈 실제 개수를 한 번에 내려준다 —
 * "팔로잉 4곳과 좋아요 12개가 모두 삭제되고 되돌릴 수 없어요"를 화면이 조립할 수 있어야 한다.
 */
@Getter
@AllArgsConstructor
public class WithdrawalInfoResponse {

    @Schema(description = "탈퇴 가능 여부 — false면 동의 체크와 [탈퇴하기]가 계속 비활성", example = "false")
    private boolean withdrawable;

    @Schema(description = "진행 중인 주문 상품 수 (0이면 차단 없음)", example = "2")
    private long ongoingOrderCount;

    @Schema(description = "삭제될 팔로잉 수", example = "4")
    private long followingCount;

    @Schema(description = "삭제될 좋아요 수", example = "12")
    private long wishlistCount;

    @Schema(description = "삭제될 장바구니 상품 수", example = "3")
    private long cartCount;

    @Schema(description = "C15-3 1단계에 노출할 탈퇴 이유 목록")
    private List<WithdrawalReasonOption> reasons;

    @Schema(description = "인플루언서 계정 — true 면 소비자 탈퇴가 막힌다. 쇼룸 스튜디오 기본정보에서 탈퇴를 신청해 승인받아야 한다(§51-5)",
            example = "false")
    private boolean creatorReviewRequired;

    @Schema(description = "탈퇴 차단 사유 분해(1021 기-31) — [진행 중 주문 N건 보기]는 합. 전부 0 이고 인플루언서가 아니어야 탈퇴할 수 있다")
    private Blockers blockers;

    @Getter
    @AllArgsConstructor
    @Schema(name = "WithdrawalBlockers")
    public static class Blockers {
        @Schema(description = "구매확정 전 주문 상품 수(결제 전 제외)", example = "1")
        private long ongoingOrders;
        @Schema(description = "진행 중 반품 · 교환 요청 수", example = "1")
        private long openClaims;
        @Schema(description = "미결제 재발송비 · 사유 번복 결제 수", example = "0")
        private long unpaidCharges;
        @Schema(description = "송금 대기 중인 운임 환급 수 — 받을 돈이 남아 있다", example = "0")
        private long pendingTransfers;

        public boolean any() {
            return ongoingOrders + openClaims + unpaidCharges + pendingTransfers > 0;
        }
    }

    @Getter
    @AllArgsConstructor
    @Schema(description = "탈퇴 이유 선택지")
    public static class WithdrawalReasonOption {

        @Schema(description = "탈퇴 시 그대로 보내는 코드", example = "NO_GROUP_BUY")
        private String code;

        @Schema(description = "화면에 보이는 문구", example = "원하는 공구가 없어요")
        private String label;
    }
}
