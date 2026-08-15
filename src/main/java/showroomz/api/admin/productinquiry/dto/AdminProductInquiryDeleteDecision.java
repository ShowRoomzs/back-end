package showroomz.api.admin.productinquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.inquiry.type.ProductInquiryAdminDeleteReason;
import showroomz.domain.inquiry.type.ProductInquiryRejectReason;

/**
 * 브랜드의 삭제 요청에 대한 운영자 판단, 그리고 운영자 직접 삭제 (§18-4~18-6).
 * 삭제 사유와 반려 사유는 성격이 다르다 — 반려 사유는 요청 브랜드에게 전달되고,
 * 삭제 사유는 운영자 내부 기록이라 브랜드·작성자 어느 쪽에도 공개되지 않는다.
 */
public class AdminProductInquiryDeleteDecision {

    @Getter
    @NoArgsConstructor
    @Schema(description = "삭제 집행 요청 — 삭제 요청 유무와 무관하게 운영자가 직접 집행할 수 있다")
    public static class ExecuteRequest {

        @NotNull(message = "삭제 사유를 선택해주세요.")
        @Schema(description = "삭제 사유", requiredMode = Schema.RequiredMode.REQUIRED, example = "ADVERTISEMENT",
                allowableValues = {"ADVERTISEMENT", "ABUSE", "PRIVACY_EXPOSURE", "ETC"})
        private ProductInquiryAdminDeleteReason reason;

        @Size(max = 500, message = "상세 사유는 최대 500자까지 입력 가능합니다.")
        @Schema(description = "상세 사유 — 선택. 사유가 `ETC`(기타 직접 입력)면 필수. " +
                "내부 기록용이며 작성자에게 통지하지 않습니다", maxLength = 500)
        private String detail;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "삭제 요청 반려 — 삭제 요청이 있는 건에만 성립한다")
    public static class RejectRequest {

        @NotNull(message = "반려 사유를 선택해주세요.")
        @Schema(description = "반려 사유", requiredMode = Schema.RequiredMode.REQUIRED, example = "NORMAL_INQUIRY",
                allowableValues = {"NOT_QUALIFYING", "INSUFFICIENT_EVIDENCE", "NORMAL_INQUIRY", "ETC"})
        private ProductInquiryRejectReason reason;

        @Size(max = 500, message = "상세 사유는 최대 500자까지 입력 가능합니다.")
        @Schema(description = "상세 사유 — 선택. 사유가 `ETC`(기타 직접 입력)면 필수. " +
                "요청 브랜드에게 전달됩니다", maxLength = 500)
        private String detail;
    }
}
