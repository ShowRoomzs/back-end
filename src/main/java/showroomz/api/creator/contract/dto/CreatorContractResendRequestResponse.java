package showroomz.api.creator.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * [서명 안내 다시 받기] 결과.
 *
 * <p><b>실제 재발송은 우리가 하지 않는다.</b> 운영자가 모두싸인에서 한다(§25-3 #1).
 * 화면 문구가 「재발송했습니다」가 되면 안 된다 — <b>「요청했습니다」</b>다.
 * 이 구분이 흐려지면 인플루언서가 오지 않을 메일을 기다린다.
 */
@Schema(description = "서명 안내 재발송 요청 결과")
public record CreatorContractResendRequestResponse(

        @Schema(description = "재발송 요청 ID") Long resendRequestId,

        @Schema(description = "요청 일시") LocalDateTime requestedAt,

        @Schema(description = "이미 접수돼 있던 미처리 요청을 그대로 돌려준 것인지. "
                + "true면 새 행을 만들지 않았다 — 어드민 큐에 같은 계약이 여러 줄 쌓이는 것을 막는다",
                example = "false")
        boolean alreadyRequested
) {
}
