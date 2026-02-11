package showroomz.api.admin.faq.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.inquiry.type.InquiryType;

@Getter
@NoArgsConstructor
public class AdminFaqRegisterRequest {

    @Schema(
            description = "질문 타입 (대분류). 허용값: DELIVERY(배송), ORDER_PAYMENT(주문/결제), CANCEL_REFUND_EXCHANGE(취소/교환/환불), USER_INFO(회원정보), PRODUCT_CHECK(상품확인), SERVICE(서비스)",
            example = "DELIVERY",
            allowableValues = {"DELIVERY", "ORDER_PAYMENT", "CANCEL_REFUND_EXCHANGE", "USER_INFO", "PRODUCT_CHECK", "SERVICE"}
    )
    @NotNull(message = "질문 타입을 선택해주세요.")
    private InquiryType type;

    @Schema(description = "카테고리 (소분류)", example = "배송 지연")
    @NotBlank(message = "카테고리를 입력해주세요.")
    private String category;

    @Schema(description = "질문", example = "배송은 얼마나 걸리나요?")
    @NotBlank(message = "질문 내용을 입력해주세요.")
    private String question;

    @Schema(description = "답변", example = "평균 2~3일 소요됩니다.")
    @NotBlank(message = "답변 내용을 입력해주세요.")
    private String answer;

    @Schema(description = "노출 여부", example = "true", nullable = true)
    private Boolean isVisible = true; // 기본값 true
}

