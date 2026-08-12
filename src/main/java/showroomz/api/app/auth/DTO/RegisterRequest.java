package showroomz.api.app.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "회원가입 요청 (C0-1) — 닉네임과 약관 동의만 입력받습니다. "
        + "실명·생년월일·성별은 C0-2 본인인증(PASS) 결과로 채워지므로 요청에 포함하지 않습니다.")
public class RegisterRequest {
    
    @NotNull(message = "닉네임은 필수 입력값입니다.")
    @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하이어야 합니다.")
    @Pattern(regexp = "^[가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9]+$", message = "닉네임에 특수문자나 이모티콘을 사용할 수 없습니다.")
    @Schema(description = "닉네임", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nickname;
    
    @AssertTrue(message = "만 14세 이상만 가입할 수 있습니다.")
    @Schema(description = "[필수] 만 14세 이상입니다", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean ageAgree;
    
    @AssertTrue(message = "서비스 이용약관에 동의해야 합니다.")
    @Schema(description = "[필수] 서비스 이용약관 동의", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean serviceAgree;
    
    @AssertTrue(message = "개인정보 수집 및 이용에 동의해야 합니다.")
    @Schema(description = "[필수] 개인정보 수집·이용 동의", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean privacyAgree;
    
    @Schema(description = "[선택] 광고성 정보 수신 동의", example = "true", nullable = true)
    private Boolean marketingAgree; // 선택사항
}
