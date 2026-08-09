package showroomz.api.seller.changerequest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.domain.changerequest.type.ChangeRequestField;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "변경 요청 항목 1건")
public class ChangeRequestItemRequest {

    @NotNull(message = "변경 항목은 필수입니다.")
    @Schema(description = "변경 항목 키. enum에 없는 값(예: 사업자등록번호)은 역직렬화 단계에서 400으로 거부된다.",
            example = "REPRESENTATIVE_NAME")
    private ChangeRequestField fieldKey;

    @NotBlank(message = "변경할 값을 입력해주세요.")
    @Schema(description = "요청값. BANK_CODE는 은행 표준 코드(3자리)를 보낸다.", example = "이대표")
    private String requestedValue;
}
