package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.cs.type.CsCategory;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class InquiryListResponse {

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

    @Schema(description = "연결된 주문 요약 — 목록의 주문 카드(주문번호·상품명·썸네일)용. 주문을 연결하지 않았으면 null")
    private InquiryOrderSummary order;

    @Schema(description = "답변 상태 (WAITING: 접수, ANSWERED: 답변완료)", allowableValues = {"WAITING", "ANSWERED"})
    private InquiryStatus status;

    @Schema(description = "답변 내용 (접수 상태이면 null)")
    private String answerContent;

    @Schema(description = "답변 일시")
    private LocalDateTime answeredAt;

    @Schema(description = "등록 일시")
    private LocalDateTime createdAt;

    public static InquiryListResponse from(OneToOneInquiry inquiry, InquiryOrderSummary order) {
        CsCategory type = inquiry.getType();
        return InquiryListResponse.builder()
                .id(inquiry.getId())
                .type(type.name())
                .typeName(type.getDescription())
                .content(inquiry.getContent())
                // 지연 로딩 컬렉션(@ElementCollection)이라 트랜잭션 안에서 복사해 넘긴다 —
                // 원본을 그대로 실으면 직렬화 시점에 세션이 닫혀 응답 쓰기가 실패한다.
                .imageUrls(List.copyOf(inquiry.getImageUrls()))
                .orderId(inquiry.getOrderId())
                .order(order)
                .status(inquiry.getStatus())
                .answerContent(inquiry.getAnswerContent())
                .answeredAt(inquiry.getAnsweredAt())
                .createdAt(inquiry.getCreatedAt())
                .build();
    }
}
