package showroomz.api.app.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "리뷰 수정 요청")
public class ReviewUpdateRequest {

    @NotNull(message = "평점은 필수입니다.")
    @Min(value = 1, message = "평점은 1 이상이어야 합니다.")
    @Max(value = 5, message = "평점은 5 이하여야 합니다.")
    @Schema(description = "평점 (1-5)", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer rating;

    @NotBlank(message = "리뷰 내용을 입력해주세요.")
    @Size(min = 20, message = "리뷰 내용은 20자 이상이어야 합니다.")
    @Schema(description = "리뷰 내용 (20자 이상)", example = "상품 품질이 매우 좋고 배송도 빨라서 만족스럽습니다. 다음에도 구매할 예정입니다.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(description = "리뷰 이미지 URL 목록 (기존 이미지 삭제 후 새 이미지로 교체)", example = "[\"https://example.com/img1.jpg\", \"https://example.com/img2.jpg\"]")
    private List<String> imageUrls;
}
