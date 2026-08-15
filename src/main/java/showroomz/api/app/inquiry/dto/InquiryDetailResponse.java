package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.cs.type.CsCategory;
import showroomz.domain.member.user.entity.Users;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class InquiryDetailResponse {

    @Schema(description = "문의 ID")
    private Long id;

    @Schema(description = "문의 유형 코드", example = "DELIVERY")
    private String type;

    @Schema(description = "문의 유형 한글명", example = "배송")
    private String typeName;

    @Schema(description = "문의 내용")
    private String content;

    @Schema(description = "첨부 이미지 URL 리스트")
    private List<String> imageUrls;

    @Schema(description = "참조 주문 ID (선택 — 없으면 null)")
    private Long orderId;

    @Schema(description = "연결된 주문 요약 — 상세 상단 주문 카드용. 주문을 연결하지 않았으면 null (블록 자체를 노출하지 않는다)")
    private InquiryOrderSummary order;

    @Schema(description = "작성자 닉네임", example = "수민")
    private String writerNickname;

    @Schema(description = "작성자 프로필 이미지 URL (미설정이면 null)")
    private String writerProfileImageUrl;

    @Schema(description = "답변 상태 (WAITING: 접수, ANSWERED: 답변완료)", allowableValues = {"WAITING", "ANSWERED"})
    private InquiryStatus status;

    @Schema(description = "답변자 표시명 — 1:1 문의 답변 주체는 항상 운영팀이다", example = "쇼룸즈 고객센터")
    private String answererName;

    @Schema(description = "답변 내용 (접수 상태이면 null)")
    private String answerContent;

    @Schema(description = "답변 일시")
    private LocalDateTime answeredAt;

    @Schema(description = "문의 등록 일시")
    private LocalDateTime createdAt;

    /** 1:1 문의 답변 주체는 마켓이 아닌 운영팀으로 고정된다 (§17) */
    public static final String ANSWERER_NAME = "쇼룸즈 고객센터";

    public static InquiryDetailResponse from(OneToOneInquiry inquiry, InquiryOrderSummary order) {
        CsCategory type = inquiry.getType();
        Users writer = inquiry.getUser();
        return InquiryDetailResponse.builder()
                .id(inquiry.getId())
                .type(type.name())
                .typeName(type.getDescription())
                .content(inquiry.getContent())
                .imageUrls(inquiry.getImageUrls())
                .orderId(inquiry.getOrderId())
                .order(order)
                .writerNickname(writer.getNickname())
                .writerProfileImageUrl(writer.getProfileImageUrl())
                .status(inquiry.getStatus())
                .answererName(inquiry.isAnswered() ? ANSWERER_NAME : null)
                .answerContent(inquiry.getAnswerContent())
                .answeredAt(inquiry.getAnsweredAt())
                .createdAt(inquiry.getCreatedAt())
                .build();
    }
}
