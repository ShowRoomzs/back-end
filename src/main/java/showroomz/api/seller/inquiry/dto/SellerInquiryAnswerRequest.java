package showroomz.api.seller.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 브랜드 답변 등록·수정 요청 (§23-4).
 * 상한은 화면에서 입력 자체가 막히므로(maxlength) 초과 상태는 생기지 않는다 —
 * 서버 검증은 우회 요청에 대한 방어선이다.
 */
@Getter
@NoArgsConstructor
@Schema(description = "상품 문의 답변 등록·수정 요청")
public class SellerInquiryAnswerRequest {

    @NotBlank(message = "답변 내용을 입력해주세요.")
    @Size(max = 2000, message = "답변 내용은 최대 2000자까지 입력 가능합니다.")
    @Schema(description = "답변 내용 (최대 2000자)", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 2000)
    private String answerContent;
}
