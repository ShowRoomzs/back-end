package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.inquiry.type.InquiryDetailType;
import showroomz.domain.inquiry.type.InquiryType;

import java.util.List;

@Getter
@NoArgsConstructor
public class InquiryRegisterRequest {

    @Schema(description = "문의 타입 (대분류)", example = "DELIVERY", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "문의 타입을 선택해주세요.")
    private InquiryType type;

    @Schema(description = "문의 상세 유형 (소분류)", example = "DELIVERY_SCHEDULE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "상세 유형을 선택해주세요.")
    private InquiryDetailType detailType;

    @Schema(description = "문의 내용", example = "배송이 언제 오나요?", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "내용을 입력해주세요.")
    private String content;

    @Schema(description = "첨부 이미지 URL 리스트 (최대 10장)")
    @Size(max = 10, message = "이미지는 최대 10장까지 첨부 가능합니다.")
    private List<String> imageUrls;
}
