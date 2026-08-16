package showroomz.api.app.user.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.user.type.WithdrawalReason;

/**
 * C15-3/C15-4 회원 탈퇴 요청.
 *
 * <p>1단계에서 고른 이유와 2단계의 [필수] 동의를 함께 보낸다.
 * 이유는 서비스 개선용이라 선택하지 않아도 탈퇴할 수 있다(null 허용).
 */
@Getter
@NoArgsConstructor
public class WithdrawalRequest {

    @Schema(description = "[필수] 위 내용을 모두 확인했고, 계정과 활동 기록이 삭제되는 데 동의", example = "true")
    private boolean agreeConsent;

    @Schema(
            description = "C15-3 탈퇴 이유 (선택 — 고르지 않으면 null)",
            example = "NO_GROUP_BUY",
            allowableValues = {"NO_GROUP_BUY", "TOO_MANY_NOTIFICATIONS", "INCONVENIENT_APP",
                    "PRIVACY_CONCERN", "REJOIN_OTHER_ACCOUNT", "ETC"}
    )
    private WithdrawalReason reason;

    @Schema(description = "이유가 ETC일 때 자유 입력 (선택)", example = "쓸 일이 없어졌어요")
    private String customReason;
}
