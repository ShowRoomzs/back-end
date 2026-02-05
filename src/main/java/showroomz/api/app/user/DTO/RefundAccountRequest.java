package showroomz.api.app.user.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RefundAccountRequest {

    @Schema(description = "은행 표준 코드 (예: 004, 090)", example = "004")
    @NotBlank(message = "은행 코드는 필수입니다.")
    @Size(min = 3, max = 3, message = "은행 코드는 3자리여야 합니다.")
    private String bankCode;

    @Schema(description = "계좌번호 (하이픈 없이 숫자만)", example = "123456789012")
    @NotBlank(message = "계좌번호는 필수입니다.")
    @Pattern(regexp = "^[0-9]*$", message = "계좌번호는 숫자만 입력해주세요.")
    private String accountNumber;

    @Schema(description = "예금주명 (선택)", example = "홍길동")
    private String accountHolder;
}
