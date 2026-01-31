package showroomz.api.seller.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import showroomz.domain.market.type.SnsType;

@Getter
@Setter
@Schema(description = "크리에이터(쇼룸) 회원가입 요청")
public class CreatorSignUpRequest {

    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Schema(description = "아이디(이메일)", example = "creator@showroomz.shop")
    private String email;

    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,16}$",
             message = "비밀번호는 8~16자의 영문자, 숫자, 특수문자를 포함해야 합니다.")
    @Schema(description = "비밀번호", example = "Pass1234!")
    private String password;

    @NotBlank(message = "비밀번호 확인은 필수 입력값입니다.")
    @Schema(description = "비밀번호 재입력", example = "Pass1234!")
    private String passwordConfirm;

    @NotBlank(message = "크리에이터 본명은 필수 입력값입니다.")
    @Schema(description = "크리에이터 본명", example = "김창작")
    private String sellerName;

    @NotBlank(message = "전화번호는 필수 입력값입니다.")
    @Pattern(regexp = "^01(?:0|1|[6-9])(?:\\d{3}|\\d{4})\\d{4}$|^01(?:0|1|[6-9])-(?:\\d{3}|\\d{4})-\\d{4}$",
             message = "올바른 휴대폰 번호 형식이 아닙니다.")
    @Schema(description = "전화번호", example = "010-1234-5678")
    private String sellerContact;

    @NotBlank(message = "쇼룸명은 필수 입력값입니다.")
    @Pattern(regexp = "^([가-힣0-9]+|[a-zA-Z0-9]+)$",
             message = "쇼룸명은 공백과 특수문자를 사용할 수 없으며, 한글 또는 영문 중 하나만 사용해야 합니다.")
    @Schema(description = "쇼룸명 (ID 역할)", example = "myshowroom")
    private String marketName;

    @NotBlank(message = "활동명은 필수 입력값입니다.")
    @Schema(description = "활동명 (닉네임)", example = "뷰티크리에이터")
    private String activityName;

    @NotNull(message = "SNS 플랫폼은 필수 입력값입니다.")
    @Schema(
            description = "SNS 플랫폼 (INSTAGRAM, TIKTOK, X, YOUTUBE)",
            example = "INSTAGRAM",
            allowableValues = {"INSTAGRAM", "TIKTOK", "X", "YOUTUBE"}
    )
    private SnsType snsType;

    @NotBlank(message = "SNS URL은 필수 입력값입니다.")
    @Schema(description = "SNS URL", example = "https://instagram.com/my_id")
    private String snsUrl;
}

