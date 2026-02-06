package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.inquiry.type.InquiryType;

import java.util.List;

@Getter
@NoArgsConstructor
public class InquiryRegisterRequest {

    @Schema(description = "문의 유형", example = "DELIVERY", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "문의 유형을 선택해주세요.")
    private InquiryType type;

    @Schema(description = "문의 제목", example = "배송이 언제 오나요?", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "제목을 입력해주세요.")
    @Size(max = 200, message = "제목은 200자 이내로 입력해주세요.")
    private String title;

    @Schema(description = "문의 내용", example = "주문한지 3일 지났는데 아직 배송준비중입니다.", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "내용을 입력해주세요.")
    private String content;

    @Schema(description = "첨부 이미지 URL 리스트 (최대 10장)", example = "[\"https://s3.../img1.jpg\", \"https://s3.../img2.jpg\"]")
    @Size(max = 10, message = "이미지는 최대 10장까지 첨부 가능합니다.")
    private List<String> imageUrls;
}
