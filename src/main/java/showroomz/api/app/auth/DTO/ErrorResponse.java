package showroomz.api.app.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "공통 에러 응답")
public class ErrorResponse {
    @Schema(description = "에러 코드 — 클라이언트는 이 값으로 오류를 구분한다 (예: INVALID_INPUT, USER_NOT_FOUND)", example = "INVALID_INPUT")
    private String code;
    @Schema(description = "에러 메시지 — 화면에 노출할 수 있는 안내 문구. 기본은 코드별 고정 문구이고 상황에 따라 상세 문구로 바뀐다",
            example = "입력값이 올바르지 않습니다.")
    private String message;
}

