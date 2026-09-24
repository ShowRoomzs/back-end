package showroomz.api.seller.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * [서명 안내 다시 받기] 결과.
 *
 * <p>요청은 발송이 아니다 — 계약 상태는 변하지 않는다. 실제 재발송은 어드민이 모두싸인에서 한다.
 */
@Schema(description = "재발송 요청 결과")
public record ContractResendRequestResponse(

        @Schema(description = "재발송 요청 ID") Long resendRequestId,

        @Schema(description = "요청 일시") LocalDateTime requestedAt,

        @Schema(description = "이미 접수돼 있던 미처리 요청을 그대로 돌려준 것인지. "
                + "true면 새 요청을 만들지 않았다(설계서 3-6)", example = "false")
        boolean alreadyRequested
) {
}
