package showroomz.api.seller.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "이메일 · 비밀번호 로그인 요청 (판매자 · 관리자 공용)")
public class SellerLoginRequest {
    @NotBlank(message = "이메일을 입력해 주세요.")
    @Schema(description = "로그인 이메일 (계정 ID)", example = "seller@example.com")
    private String email;

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Schema(description = "비밀번호", example = "Seller123!")
    private String password;
}

