package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.InquiryType;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class InquiryDetailResponse {

    @Schema(description = "문의 ID")
    private Long id;

    @Schema(description = "문의 유형")
    private InquiryType type;

    @Schema(description = "문의 제목")
    private String title;

    @Schema(description = "문의 내용")
    private String content;

    @Schema(description = "첨부 이미지 URL 리스트")
    private List<String> imageUrls;

    @Schema(description = "답변 상태")
    private InquiryStatus status;

    @Schema(description = "답변 내용 (답변 대기 시 null)")
    private String answerContent;

    @Schema(description = "답변 일시")
    private LocalDateTime answeredAt;

    @Schema(description = "문의 등록 일시")
    private LocalDateTime createdAt;

    public static InquiryDetailResponse from(OneToOneInquiry inquiry) {
        return InquiryDetailResponse.builder()
                .id(inquiry.getId())
                .type(inquiry.getType())
                .title(inquiry.getTitle())
                .content(inquiry.getContent())
                .imageUrls(inquiry.getImageUrls())
                .status(inquiry.getStatus())
                .answerContent(inquiry.getAnswerContent())
                .answeredAt(inquiry.getAnsweredAt())
                .createdAt(inquiry.getCreatedAt())
                .build();
    }
}
