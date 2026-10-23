package showroomz.api.app.user.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NicknameCheckResponse {
    @Schema(description = "사용 가능 여부 — code가 AVAILABLE일 때만 true", example = "true")
    private Boolean isAvailable;
    @Schema(description = "검사 결과 코드 — AVAILABLE · UNCHANGED(현재 닉네임 그대로) · INVALID_LENGTH · INVALID_FORMAT · PROFANITY · DUPLICATE",
            example = "AVAILABLE")
    private String code;
    @Schema(description = "화면에 그대로 노출할 안내 문구", example = "사용할 수 있는 닉네임이에요")
    private String message;
}

