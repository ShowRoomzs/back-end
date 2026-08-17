package showroomz.api.app.setting.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * C15-2 회원정보 변경 — PASS 재인증 요청.
 * 가입 시 동의와 별개의 새 수집 행위라 매번 다시 동의를 받고, 동의 일시를 이력에 남긴다.
 */
@Getter
@NoArgsConstructor
public class IdentityReverifyRequest {

    @Schema(description = "[필수] 본인확인을 위해 이름·생년월일·성별·휴대폰번호를 수집·이용하는 데 동의", example = "true")
    private boolean agreeConsent;
}
