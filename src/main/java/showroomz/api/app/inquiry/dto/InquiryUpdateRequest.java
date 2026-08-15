package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.cs.type.CsCategory;

import java.util.List;

@Getter
@NoArgsConstructor
public class InquiryUpdateRequest {

    @Schema(description = "문의 유형 (5종)", example = "DELIVERY", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"DELIVERY", "CANCEL_EXCHANGE_RETURN", "ORDER_PAYMENT", "SERVICE", "ACCOUNT"})
    @NotNull(message = "문의 유형을 선택해주세요.")
    private CsCategory type;

    @Schema(description = "문의 내용 (최대 1000자)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "문의 내용을 입력해주세요.")
    @Size(max = 1000, message = "문의 내용은 1000자 이내로 입력해주세요.")
    private String content;

    @Schema(description = "첨부 이미지 URL 리스트 (최대 5장)")
    @Size(max = 5, message = "사진은 최대 5장까지 첨부할 수 있습니다.")
    private List<String> imageUrls;

    @Schema(description = "참조 주문 ID (선택 — 주문 없이도 문의할 수 있습니다)", example = "123456")
    private Long orderId;
}
