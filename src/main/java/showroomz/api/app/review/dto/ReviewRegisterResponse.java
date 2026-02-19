package showroomz.api.app.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "리뷰 등록 응답")
public class ReviewRegisterResponse {

    @Schema(description = "생성된 리뷰 ID")
    private Long reviewId;
}
