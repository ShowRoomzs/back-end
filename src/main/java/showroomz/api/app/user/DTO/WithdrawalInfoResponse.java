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
