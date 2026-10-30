package showroomz.api.app.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 본인인증 완료 요청(57 설계서 3-2) — 본문은 선택이고 <b>기록용</b>이다. SDK `onComplete` 가 준 값을 실어 주면 조사 자료로 남긴다.
 * 판정은 서버가 포트원을 직접 조회해서 한다 — 앱이 이름 · 생년월일 · 전화를 보내는 경로는 없다(0-1).
 */
@Getter
@NoArgsConstructor
public class IdentityVerificationCompleteRequest {

    @Size(max = 64)
    @Schema(description = "SDK 응답 identityVerificationTxId(시도 ID) — 선택", example = "iv-tx-…", nullable = true)
    private String identityVerificationTxId;

    @Size(max = 64)
    @Schema(description = "SDK 응답 code(있으면 창에서 실패) — 선택", example = "FAILURE_TYPE_PG", nullable = true)
    private String code;

    @Size(max = 64)
    @Schema(description = "SDK 응답 pgCode — 선택", example = "CS24", nullable = true)
    private String pgCode;
}
