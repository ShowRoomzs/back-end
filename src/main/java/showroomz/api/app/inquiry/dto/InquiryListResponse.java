package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.type.InquiryDetailType;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.InquiryType;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class InquiryListResponse {

    @Schema(description = "문의 ID")
    private Long id;

    @Schema(description = "문의 타입 (대분류)")
    private InquiryType type;

    @Schema(description = "문의 상세 유형 (소분류)")
    private InquiryDetailType detailType;

    @Schema(description = "문의 내용")
    private String content;

    @Schema(description = "첨부 이미지 URL 리스트")
    private List<String> imageUrls;

    @Schema(description = "답변 상태 (WAITING: 답변 대기, ANSWERED: 답변 완료)", allowableValues = {"WAITING", "ANSWERED"})
    private InquiryStatus status;

    @Schema(description = "답변 내용 (답변 대기 시 null)")
    private String answerContent;

    @Schema(description = "답변 일시")
    private LocalDateTime answeredAt;

    @Schema(description = "등록 일시")
    private LocalDateTime createdAt;

    public static InquiryListResponse from(OneToOneInquiry inquiry) {
        return InquiryListResponse.builder()
                .id(inquiry.getId())
                .type(inquiry.getType())
                .detailType(inquiry.getCategory())
                .content(inquiry.getContent())
                .imageUrls(inquiry.getImageUrls())
                .status(inquiry.getStatus())
                .answerContent(inquiry.getAnswerContent())
                .answeredAt(inquiry.getAnsweredAt())
                .createdAt(inquiry.getCreatedAt())
                .build();
    }
}
