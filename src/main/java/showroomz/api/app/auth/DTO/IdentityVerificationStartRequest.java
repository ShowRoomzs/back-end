package showroomz.api.app.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.user.type.IdentityVerificationPurpose;

/** 본인인증 시작(57 설계서 3-1) — 용도만 받는다. 개인정보는 받지 않는다(포트원 SDK 창이 받는다). */
@Getter
@NoArgsConstructor
public class IdentityVerificationStartRequest {

    @NotNull(message = "purpose 는 필수입니다.")
    @Schema(description = "용도 — SIGNUP(회원가입 C0-2 · registerToken) / REVERIFY(회원정보 재인증 C15-2 · accessToken)",
            example = "SIGNUP", requiredMode = Schema.RequiredMode.REQUIRED)
    private IdentityVerificationPurpose purpose;
}
