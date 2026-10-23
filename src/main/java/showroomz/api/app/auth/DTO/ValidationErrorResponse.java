package showroomz.api.app.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "입력값 검증 실패 응답 — 필드별 오류 목록을 함께 내린다")
public class ValidationErrorResponse {
    @Schema(description = "에러 코드 — 항상 INVALID_INPUT", example = "INVALID_INPUT")
    private String code;
    @Schema(description = "에러 메시지 — 고정 문구. 필드별 사유는 errors 참고", example = "입력값이 올바르지 않습니다.")
    private String message;
    @Schema(description = "필드별 검증 오류 목록")
    private List<FieldError> errors;
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "필드 검증 오류")
    public static class FieldError {
        @Schema(description = "오류가 난 요청 필드명", example = "nickname")
        private String field;
        @Schema(description = "오류 사유 문구", example = "닉네임은 2자 이상 10자 이하이어야 합니다.")
        private String reason;
    }
}

