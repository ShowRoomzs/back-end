package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.InquiryType;

import java.time.LocalDateTime;

@Getter
@Builder
public class InquiryListResponse {

    @Schema(description = "문의 ID")
    private Long id;

    @Schema(description = "문의 유형")
    private InquiryType type;

    @Schema(description = "문의 제목")
    private String title;

    @Schema(description = "답변 상태")
    private InquiryStatus status;

    @Schema(description = "등록 일시")
    private LocalDateTime createdAt;

    public static InquiryListResponse from(OneToOneInquiry inquiry) {
        return InquiryListResponse.builder()
                .id(inquiry.getId())
                .type(inquiry.getType())
                .title(inquiry.getTitle())
                .status(inquiry.getStatus())
                .createdAt(inquiry.getCreatedAt())
                .build();
    }
}
