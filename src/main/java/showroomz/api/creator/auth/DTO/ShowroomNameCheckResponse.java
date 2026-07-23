package showroomz.api.creator.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "쇼룸명 중복/유효성 검사 응답")
public class ShowroomNameCheckResponse {

    @Schema(description = "사용 가능 여부", example = "true")
    private Boolean isAvailable;

    @Schema(description = "응답 코드 (AVAILABLE / DUPLICATE / INVALID_FORMAT)", example = "AVAILABLE")
    private String code;

    @Schema(description = "결과 메시지", example = "사용 가능한 쇼룸명입니다.")
    private String message;
}
