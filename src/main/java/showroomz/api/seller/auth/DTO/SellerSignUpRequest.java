package showroomz.api.seller.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "관리자(판매자) 회원가입 요청")
public class SellerSignUpRequest {

    // 1. 계정 정보
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Schema(description = "아이디(이메일)", example = "admin@showroomz.shop")
    private String email;

    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    // 8~16자, 영문+숫자+특수문자 조합
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,16}$", 
             message = "비밀번호는 8~16자의 영문자, 숫자, 특수문자를 포함해야 합니다.")
    @Schema(description = "비밀번호", example = "Admin123!")
    private String password;

    @NotBlank(message = "비밀번호 확인은 필수 입력값입니다.")
    @Schema(description = "비밀번호 재입력", example = "Admin123!")
    private String passwordConfirm;

    // 2. 셀러 정보
    @NotBlank(message = "판매 담당자 이름은 필수 입력값입니다.")
    @Schema(description = "판매 담당자 이름", example = "김담당")
    private String sellerName;

    @NotBlank(message = "연락처는 필수 입력값입니다.")
    // 일반적인 휴대폰 번호 형식 (010-1234-5678 또는 01012345678)
    @Pattern(regexp = "^01(?:0|1|[6-9])(?:\\d{3}|\\d{4})\\d{4}$|^01(?:0|1|[6-9])-(?:\\d{3}|\\d{4})-\\d{4}$",
             message = "올바른 휴대폰 번호 형식이 아닙니다.")
    @Schema(description = "판매 담당자 연락처", example = "010-1234-5678")
    private String sellerContact;

    // 3. 마켓 정보
    @NotBlank(message = "마켓명은 필수 입력값입니다.")
    // 공백 불가, 특수문자 불가, 한/영 혼용 불가 (한글만 or 영문만, 숫자는 허용한다고 가정)
    @Pattern(regexp = "^([가-힣0-9]+|[a-zA-Z0-9]+)$", 
             message = "마켓명은 공백과 특수문자를 사용할 수 없으며, 한글 또는 영문 중 하나만 사용해야 합니다.")
    @Schema(description = "마켓명", example = "쇼룸즈")
    private String marketName;

    @NotBlank(message = "고객센터 전화번호는 필수 입력값입니다.")
    @Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$", message = "올바른 전화번호 형식이 아닙니다.")
    @Schema(description = "고객센터 전화번호", example = "02-1234-5678")
    private String csNumber;
}