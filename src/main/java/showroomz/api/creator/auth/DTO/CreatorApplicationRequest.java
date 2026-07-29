package showroomz.api.creator.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import showroomz.domain.market.type.SnsType;

@Getter
@Setter
@Schema(description = "크리에이터 권한 신청 요청 (로그인 유저용)")
public class CreatorApplicationRequest {

    @NotNull(message = "SNS 플랫폼은 필수 입력값입니다.")
    @Schema(description = "SNS 플랫폼 (INSTAGRAM, TIKTOK, X, YOUTUBE)", example = "INSTAGRAM")
    private SnsType snsType;

    @NotBlank(message = "채널 주소는 필수 입력값입니다.")
    @Schema(description = "채널 주소(URL)", example = "https://instagram.com/my_channel")
    private String channelUrl;

    @NotBlank(message = "계정 아이디는 필수 입력값입니다.")
    @Size(max = 30, message = "계정 아이디는 최대 30자까지 입력 가능합니다.")
    @Pattern(
            regexp = "^[a-z0-9._]+$",
            message = "계정 아이디는 영문 소문자, 숫자, 마침표(.), 밑줄(_)만 사용할 수 있습니다."
    )
    @Schema(description = "계정 아이디 (영문 소문자·숫자·마침표·밑줄만 가능, 최대 30자)", example = "my_channel")
    private String accountId;

    @NotNull(message = "팔로워 수는 필수 입력값입니다.")
    @Min(value = 0, message = "팔로워 수는 0 이상이어야 합니다.")
    @Schema(description = "팔로워 수", example = "10000")
    private Integer followerCount;

    @NotBlank(message = "업무 이메일은 필수 입력값입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Schema(description = "업무 이메일", example = "business@creator.com")
    private String businessEmail;

    @NotNull(message = "서비스 이용약관 동의 여부는 필수입니다.")
    @Schema(description = "서비스 이용약관 동의", example = "true")
    private Boolean agreeTermsOfService;

    @NotNull(message = "서비스 운영정책 동의 여부는 필수입니다.")
    @Schema(description = "서비스 운영정책 동의", example = "true")
    private Boolean agreeOperationalPolicy;

    @NotNull(message = "개인정보 수집 및 이용 동의 여부는 필수입니다.")
    @Schema(description = "개인정보 수집 및 이용 동의", example = "true")
    private Boolean agreePrivacyPolicy;

    @NotNull(message = "마케팅 목적의 개인정보 수집 및 이용 동의 여부는 필수입니다.")
    @Schema(description = "마케팅 목적의 개인정보 수집 및 이용 동의", example = "false")
    private Boolean agreeMarketingPolicy;
}
