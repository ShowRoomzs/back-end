package showroomz.oauthlogin.auth.DTO;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    
    @NotNull(message = "닉네임은 필수 입력값입니다.")
    @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하이어야 합니다.")
    @Pattern(regexp = "^[가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9]+$", message = "닉네임에 특수문자나 이모티콘을 사용할 수 없습니다.")
    private String nickname;
    
    @Pattern(regexp = "^(MALE|FEMALE)$", message = "성별은 MALE 또는 FEMALE이어야 합니다.", flags = Pattern.Flag.CASE_INSENSITIVE)
    private String gender; // "MALE", "FEMALE", null
    
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$|^$", message = "생년월일 형식이 올바르지 않습니다. (YYYY-MM-DD)")
    private String birthday; // null 또는 "YYYY-MM-DD"
    
    @NotNull(message = "서비스 이용약관에 동의해야 합니다.")
    @AssertTrue(message = "서비스 이용약관에 동의해야 합니다.")
    private Boolean serviceAgree;
    
    @NotNull(message = "개인정보 수집 및 이용에 동의해야 합니다.")
    @AssertTrue(message = "개인정보 수집 및 이용에 동의해야 합니다.")
    private Boolean privacyAgree;
    
    private Boolean marketingAgree; // 선택사항
}

