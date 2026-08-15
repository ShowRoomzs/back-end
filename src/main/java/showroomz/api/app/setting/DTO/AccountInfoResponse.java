package showroomz.api.app.setting.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * C15-2 회원정보 — 조회 전용.
 *
 * <p>이름·생년월일·휴대폰번호는 본인인증(PASS) 결과라 화면에서 수정할 수 없고, 마스킹해서 내려준다.
 * 마스킹은 서버에서 끝낸다 — 원본을 내려보내고 클라이언트가 가리는 방식이면 가린 의미가 없다.
 */
@Getter
@AllArgsConstructor
public class AccountInfoResponse {

    @Schema(description = "이름 (마스킹)", example = "김수*")
    private String name;

    @Schema(description = "생년월일 (마스킹)", example = "1998.04.**")
    private String birthday;

    @Schema(description = "휴대폰번호 (마스킹)", example = "010-****-1234")
    private String phoneNumber;

    @Schema(description = "본인인증 완료 시각 (미인증이면 null)")
    private LocalDateTime identityVerifiedAt;
}
